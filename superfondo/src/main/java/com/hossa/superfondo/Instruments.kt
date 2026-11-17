package com.hossa.superfondo

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

class InstrumentInfo(
    val name: String,
    val sounds: List<String>,
    val factory: () -> BaseScene
)

object Instruments {
    val all = listOf(
        InstrumentInfo("Batería", listOf("kick", "snare", "tom1", "tom2", "floor", "hihat", "crash", "ride").map { "drum_$it" }) { DrumScene() },
        InstrumentInfo("Guitarra", (0..5).map { "gtr_$it" } + "gtr_chord") { GuitarScene() },
        InstrumentInfo("Teclado", (0..12).map { "key_$it" }) { KeysScene() },
        InstrumentInfo("Pads", (0..15).map { "pad_$it" }) { PadsScene() }
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("superfondo", Context.MODE_PRIVATE)
    fun selectedIndex(ctx: Context): Int = sp(ctx).getInt("instrument", 0).coerceIn(0, all.size - 1)
    fun select(ctx: Context, i: Int) { sp(ctx).edit().putInt("instrument", i).apply() }
}

/** Carga los sonidos con SoundPool y los reproduce con poco retraso. */
class SoundBank(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = HashMap<String, Int>()

    fun ensure(names: List<String>) {
        for (n in names) {
            if (ids.containsKey(n)) continue
            val res = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
            if (res != 0) ids[n] = pool.load(ctx, res, 1)
        }
    }

    fun play(name: String, vol: Float, delayMs: Long) {
        val id = ids[name] ?: return
        if (delayMs <= 0) pool.play(id, vol, vol, 1, 0, 1f)
        else handler.postDelayed({ pool.play(id, vol, vol, 1, 0, 1f) }, delayMs)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}
