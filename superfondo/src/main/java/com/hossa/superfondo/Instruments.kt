package com.hossa.superfondo

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.pow

class InstrumentInfo(
    val name: String,
    val prefix: String,          // prefijo de los sonidos en res/raw
    val slots: Int,              // cuántos sonidos usa
    val kits: List<String>,      // sonidos alternativos incluidos
    val baseMidi: Int?,          // nota del primer sonido (solo instrumentos melódicos)
    val factory: () -> BaseScene
)

object Instruments {
    const val DRUMS = 0
    const val GUITAR = 1
    const val KEYS = 2
    const val PADS = 3

    val all = listOf(
        InstrumentInfo("Batería", "drums", 8, listOf("Rock", "Electrónica 808", "Metal"), null) { DrumScene() },
        InstrumentInfo("Guitarra", "guitar", 13, listOf("Distorsión", "Limpia", "Acústica"), 40) { GuitarScene() },
        InstrumentInfo("Teclado", "keys", 13, listOf("Piano eléctrico", "Sintetizador", "Órgano"), 60) { KeysScene() },
        InstrumentInfo("Pads", "pads", 16, listOf("Electrónica"), null) { PadsScene() }
    )

    fun selectedIndex(ctx: Context): Int = Settings.sp(ctx).getInt("instrument", 0).coerceIn(0, all.size - 1)
    fun select(ctx: Context, i: Int) { Settings.sp(ctx).edit().putInt("instrument", i).apply() }
}

/** Ajustes que afectan cómo se toca cada escena. */
class SceneSettings(val guitarMode: Int, val guitarProg: Int, val keysMode: Int, val keysScale: Int)

object Settings {
    fun sp(ctx: Context) = ctx.getSharedPreferences("superfondo", Context.MODE_PRIVATE)

    /** -1 = sonidos hechos con una canción propia */
    fun kit(ctx: Context, i: Int) = sp(ctx).getInt("kit_$i", 0)
    fun setKit(ctx: Context, i: Int, v: Int) = sp(ctx).edit().putInt("kit_$i", v).apply()
    fun cut(ctx: Context, i: Int) = sp(ctx).getInt("cut_$i", 100)
    fun setCut(ctx: Context, i: Int, v: Int) = sp(ctx).edit().putInt("cut_$i", v).apply()
    fun echo(ctx: Context, i: Int) = sp(ctx).getInt("echo_$i", 0)
    fun setEcho(ctx: Context, i: Int, v: Int) = sp(ctx).edit().putInt("echo_$i", v).apply()
    fun song(ctx: Context, i: Int): String? = sp(ctx).getString("song_$i", null)
    fun labels(ctx: Context, i: Int): List<String> =
        if (kit(ctx, i) == -1) (sp(ctx).getString("labels_$i", "") ?: "").split("|") else emptyList()

    fun padMode(ctx: Context) = sp(ctx).getInt("padMode", 0)         // 0 ritmo, 1 golpes
    fun setPadMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("padMode", v).apply()
    fun padLen(ctx: Context) = sp(ctx).getInt("padLen", 1)           // 0 4 s, 1 8 s, 2 16 s
    fun setPadLen(ctx: Context, v: Int) = sp(ctx).edit().putInt("padLen", v).apply()
    fun guitarMode(ctx: Context) = sp(ctx).getInt("gMode", 0)        // 0 notas, 1 acordes
    fun setGuitarMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("gMode", v).apply()
    fun guitarProg(ctx: Context) = sp(ctx).getInt("gProg", 0)        // 0 rock, 1 pop, 2 triste
    fun setGuitarProg(ctx: Context, v: Int) = sp(ctx).edit().putInt("gProg", v).apply()
    fun keysMode(ctx: Context) = sp(ctx).getInt("kMode", 0)          // 0 notas, 1 acordes
    fun setKeysMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("kMode", v).apply()
    fun keysScale(ctx: Context) = sp(ctx).getInt("kScale", 0)        // 0 libre, 1 mayor, 2 menor, 3 pentatónica
    fun setKeysScale(ctx: Context, v: Int) = sp(ctx).edit().putInt("kScale", v).apply()

    fun scene(ctx: Context) = SceneSettings(guitarMode(ctx), guitarProg(ctx), keysMode(ctx), keysScale(ctx))

    fun version(ctx: Context, i: Int) = sp(ctx).getInt("ver_$i", 0)
    fun prepared(ctx: Context, i: Int) = sp(ctx).getBoolean("prep_$i", false)
    fun markPrepared(ctx: Context, i: Int, prepared: Boolean) {
        sp(ctx).edit().putBoolean("prep_$i", prepared).putInt("ver_$i", version(ctx, i) + 1).commit()
    }
}

/** Prepara los sonidos de cada instrumento: sonido elegido + canción propia + efectos. */
object SoundStore {
    private fun dir(ctx: Context, i: Int) = File(ctx.filesDir, "inst$i").apply { mkdirs() }
    fun preparedFile(ctx: Context, i: Int, j: Int) = File(dir(ctx, i), "p_$j.wav")
    private fun sourceFile(ctx: Context, i: Int, j: Int) = File(dir(ctx, i), "src_$j.wav")
    fun hasSource(ctx: Context, i: Int) = sourceFile(ctx, i, 0).exists()

    fun cutoffHz(v: Int): Float = if (v >= 99) 20000f else 200f * 100f.pow(v / 100f)

    /** Guarda los pedazos de la canción como material base. */
    fun saveSource(ctx: Context, i: Int, parts: List<ShortArray>, sr: Int, labels: List<String>, song: String) {
        val info = Instruments.all[i]
        for (j in 0 until info.slots) {
            val f = sourceFile(ctx, i, j)
            val p = parts.getOrNull(j) ?: parts.lastOrNull()
            if (p != null) Slicer.writeWav(f, p, sr) else f.delete()
        }
        Settings.sp(ctx).edit()
            .putInt("kit_$i", -1)
            .putString("song_$i", song)
            .putString("labels_$i", labels.joinToString("|"))
            .commit()
    }

    /** Genera los archivos finales con efectos. Se llama fuera del hilo principal. */
    fun prepare(ctx: Context, i: Int) {
        val info = Instruments.all[i]
        val kit = Settings.kit(ctx, i)
        val custom = kit == -1 && hasSource(ctx, i)
        val cut = Settings.cut(ctx, i)
        val echo = Settings.echo(ctx, i)
        if (!custom && cut >= 99 && echo == 0) {
            Settings.markPrepared(ctx, i, false)
            return
        }
        val k = if (kit < 0) 0 else kit
        for (j in 0 until info.slots) {
            val pcm = if (custom) {
                Slicer.readWav(sourceFile(ctx, i, j).readBytes())
            } else {
                val res = ctx.resources.getIdentifier("${info.prefix}_${k}_$j", "raw", ctx.packageName)
                Slicer.readWav(ctx.resources.openRawResource(res).use { it.readBytes() })
            }
            val out = Slicer.render(pcm, Slice(0, pcm.data.size), cutoffHz(cut), echo / 100f)
            Slicer.writeWav(preparedFile(ctx, i, j), out, pcm.sampleRate)
        }
        Settings.markPrepared(ctx, i, true)
    }
}

/** Carga los sonidos del instrumento con SoundPool y los toca con poco retraso. */
class SoundBank(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var ids = IntArray(0)
    private var loadedInstrument = -1
    private var loadedVersion = -1

    fun load(i: Int) {
        val v = Settings.version(ctx, i)
        if (i == loadedInstrument && v == loadedVersion) return
        for (id in ids) if (id != 0) pool.unload(id)
        loadedInstrument = i
        loadedVersion = v
        val info = Instruments.all[i]
        val prepared = Settings.prepared(ctx, i)
        val kit = Settings.kit(ctx, i).coerceAtLeast(0)
        ids = IntArray(info.slots) { j ->
            val f = SoundStore.preparedFile(ctx, i, j)
            if (prepared && f.exists()) pool.load(f.absolutePath, 1)
            else {
                val res = ctx.resources.getIdentifier("${info.prefix}_${kit}_$j", "raw", ctx.packageName)
                if (res != 0) pool.load(ctx, res, 1) else 0
            }
        }
    }

    /** "s:3" = sonido número 3; "n:64" = nota MIDI 64 (instrumentos melódicos). */
    fun play(id: String, vol: Float, delayMs: Long) {
        if (loadedInstrument < 0) return
        var slot: Int
        var rate = 1f
        when {
            id.startsWith("s:") -> slot = id.substring(2).toIntOrNull() ?: return
            id.startsWith("n:") -> {
                val midi = id.substring(2).toIntOrNull() ?: return
                val base = Instruments.all[loadedInstrument].baseMidi ?: return
                slot = midi - base
                while (slot > 12) { slot -= 12; rate *= 2f }
                while (slot < 0) { slot += 12; rate /= 2f }
            }
            else -> return
        }
        val sid = ids.getOrNull(slot) ?: return
        if (sid == 0) return
        val r = rate.coerceIn(0.5f, 2f)
        if (delayMs <= 0) pool.play(sid, vol, vol, 1, 0, r)
        else handler.postDelayed({ pool.play(sid, vol, vol, 1, 0, r) }, delayMs)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}
