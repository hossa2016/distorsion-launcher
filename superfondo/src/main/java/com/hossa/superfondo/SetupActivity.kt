package com.hossa.superfondo

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.pow

class SetupActivity : Activity() {

    private val REQ_SONG = 41
    private lateinit var sounds: SoundBank
    private lateinit var preview: PreviewView
    private lateinit var scroll: ScrollView
    private lateinit var section: LinearLayout
    private lateinit var status: TextView
    private lateinit var showBtn: TextView
    private lateinit var button: Button
    private val instrumentChips = ArrayList<TextView>()
    private val colorDots = ArrayList<GradientDrawable>()
    private val worker = Executors.newSingleThreadExecutor()
    private var instrument = 0
    private var color = 0
    private var dp = 1f

    @Volatile private var songPcm: Pcm? = null
    @Volatile private var songName = ""
    @Volatile private var windowStart = 0f
    @Volatile private var markers: List<Float> = emptyList()

    private val ink = Color.parseColor("#ECE6DD")
    private val muted = Color.parseColor("#8E867C")
    private val accent get() = Palettes.all[color].accent
    private val onAccent get() = if (color == 2) Color.parseColor("#111114") else Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        instrument = Instruments.selectedIndex(this)
        color = Palettes.selectedIndex(this)
        sounds = SoundBank(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this, sounds)
        root.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(40), px(20), px(32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(150, 5, 5, 6), Color.argb(238, 5, 5, 6), Color.argb(250, 5, 5, 6))
            )
            isClickable = true
        }

        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply { text = "DISTORSIÓN"; setTextColor(ink); textSize = 24f; letterSpacing = 0.3f },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(this).apply {
            text = "Probar ▾"; setTextColor(muted); textSize = 14f
            setPadding(px(12), px(8), px(4), px(8))
            setOnClickListener { setPanelVisible(false) }
        })
        panel.addView(head)

        panel.addView(label("Instrumento"))
        val iRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Instruments.all.forEachIndexed { i, info ->
            val chip = TextView(this).apply {
                text = info.name; textSize = 15f; gravity = Gravity.CENTER
                setPadding(px(18), px(10), px(18), px(10))
                setOnClickListener { chooseInstrument(i) }
            }
            iRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = px(8) })
            instrumentChips.add(chip)
        }
        panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(iRow) })

        panel.addView(label("Color"))
        val colors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        Palettes.all.forEachIndexed { i, pal ->
            val dot = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pal.accent) }
            colorDots.add(dot)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(4), px(18), px(4))
                setOnClickListener { chooseColor(i) }
            }
            item.addView(View(this).apply { background = dot }, LinearLayout.LayoutParams(px(28), px(28)))
            item.addView(TextView(this).apply { text = pal.name; setTextColor(ink); textSize = 14f; setPadding(px(8), 0, 0, 0) })
            colors.addView(item)
        }
        panel.addView(colors)

        section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(section)
        status = TextView(this).apply { setTextColor(accent); textSize = 13f; setPadding(0, px(8), 0, 0) }
        panel.addView(status)

        panel.addView(label("Sonido en el fondo de pantalla"))
        panel.addView(toggle("Al tocar", Palettes.soundTouch(this)) { Palettes.setSoundTouch(this, it) })
        panel.addView(toggle("Al desbloquear (no suena en silencio)", Palettes.soundUnlock(this)) { Palettes.setSoundUnlock(this, it) })

        button = Button(this).apply {
            text = "Aplicar superfondo"; isAllCaps = false; textSize = 17f
            setOnClickListener { applyWallpaper() }
        }
        panel.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)).apply { topMargin = px(16) })

        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(panel) }
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            topMargin = (resources.displayMetrics.heightPixels * 0.18f).toInt()
        })

        showBtn = TextView(this).apply {
            text = "Opciones ▴"; setTextColor(ink); textSize = 14f
            setPadding(px(18), px(10), px(18), px(10))
            background = GradientDrawable().apply { cornerRadius = 22 * dp; setColor(Color.argb(200, 20, 20, 24)) }
            visibility = View.GONE
            setOnClickListener { setPanelVisible(true) }
        }
        root.addView(showBtn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(36) })

        setContentView(root)
        refreshStatic()
        rebuildSection()
        preview.setInstrument(instrument, color)
    }

    override fun onDestroy() {
        worker.shutdownNow()
        sounds.release()
        super.onDestroy()
    }

    // ---------- ayudantes de interfaz ----------
    private fun px(v: Int) = (v * dp).toInt()

    private fun label(t: String) = TextView(this).apply {
        text = t.uppercase(); setTextColor(muted); textSize = 11f; letterSpacing = 0.15f
        setPadding(0, px(14), 0, px(6))
    }

    private fun hint(t: String) = TextView(this).apply { text = t; setTextColor(muted); textSize = 12.5f; setPadding(0, px(6), 0, 0) }

    private fun chipStyle(chip: TextView, on: Boolean) {
        chip.setTextColor(if (on) onAccent else ink)
        chip.background = GradientDrawable().apply {
            cornerRadius = 20 * dp
            if (on) setColor(accent) else { setColor(Color.argb(160, 30, 30, 34)); setStroke(px(1), Color.argb(40, 255, 255, 255)) }
        }
    }

    private fun chips(options: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { i, t ->
            val chip = TextView(this).apply {
                text = t; textSize = 14f; gravity = Gravity.CENTER
                setPadding(px(14), px(8), px(14), px(8))
                setOnClickListener { onPick(i) }
            }
            chipStyle(chip, i == selected)
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = px(8) })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun slider(name: String, value: Int, fmt: (Int) -> String, onDone: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val nameTv = TextView(this).apply { text = name; setTextColor(ink); textSize = 14f }
        val valTv = TextView(this).apply { text = fmt(value); setTextColor(muted); textSize = 13f; gravity = Gravity.END }
        val bar = SeekBar(this).apply {
            max = 100; progress = value
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(ink)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { valTv.text = fmt(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { onDone(progress) }
            })
        }
        box.addView(nameTv, LinearLayout.LayoutParams(px(60), ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(valTv, LinearLayout.LayoutParams(px(70), ViewGroup.LayoutParams.WRAP_CONTENT))
        return box
    }

    private fun toggle(t: String, value: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t; textSize = 14f; setTextColor(ink); isChecked = value
        setPadding(0, px(6), 0, px(2))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    private fun setPanelVisible(v: Boolean) {
        scroll.visibility = if (v) View.VISIBLE else View.GONE
        showBtn.visibility = if (v) View.GONE else View.VISIBLE
        if (!v) Toast.makeText(this, "Toca el instrumento para probarlo", Toast.LENGTH_SHORT).show()
    }

    // ---------- sección de sonido (cambia según el instrumento) ----------
    private fun rebuildSection() {
        section.removeAllViews()
        val i = instrument
        val info = Instruments.all[i]
        val kit = Settings.kit(this, i)
        val custom = kit == -1

        section.addView(label("Sonido"))
        val kitOptions = info.kits + "Mi canción"
        section.addView(chips(kitOptions, if (custom) info.kits.size else kit) { pick ->
            if (pick == info.kits.size) chooseCustom() else {
                Settings.setKit(this, i, pick); rebuildSection(); prepareAsync()
            }
        })

        if (custom) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(10), 0, 0) }
            row.addView(TextView(this).apply {
                text = "Cargar canción"; setTextColor(onAccent); textSize = 14f
                setPadding(px(16), px(10), px(16), px(10))
                background = GradientDrawable().apply { cornerRadius = 14 * dp; setColor(accent) }
                setOnClickListener { pickSong() }
            })
            val name = if (songPcm != null) songName else (Settings.song(this, i) ?: "Sin canción")
            row.addView(TextView(this).apply {
                text = "♪ $name"; setTextColor(ink); textSize = 13f; maxLines = 1
                setPadding(px(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            section.addView(row)

            val p = songPcm
            if (p != null) {
                val wave = WaveView(this).apply {
                    peaks = Slicer.peaks(p, 220)
                    duration = p.seconds
                    windowStart = this@SetupActivity.windowStart
                    windowLen = currentWindowLen()
                    markers = this@SetupActivity.markers
                    accent = this@SetupActivity.accent
                    onPick = { t -> pickPosition(t) }
                }
                section.addView(wave, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(64)).apply { topMargin = px(10) })
                section.addView(hint(when (i) {
                    Instruments.DRUMS -> "Cada tambor usa uno de los 8 golpes más fuertes de la canción."
                    Instruments.PADS -> if (Settings.padMode(this) == 0) "Toca la onda para elegir la parte que se corta en 16 pads." else "Se usan los 16 golpes más fuertes."
                    else -> "Toca la onda para elegir el sonido. Se afina para cada " + (if (i == Instruments.KEYS) "tecla." else "cuerda.")
                }))
            } else if (SoundStore.hasSource(this, i)) {
                section.addView(hint("Para elegir otra parte, vuelve a cargar la canción."))
            }

            if (i == Instruments.PADS) {
                section.addView(label("Corte"))
                section.addView(chips(listOf("Ritmo", "Golpes"), Settings.padMode(this)) {
                    Settings.setPadMode(this, it); rebuildFromSong()
                })
                if (Settings.padMode(this) == 0) {
                    section.addView(label("Largo de la parte"))
                    section.addView(chips(listOf("4 s", "8 s", "16 s"), Settings.padLen(this)) {
                        Settings.setPadLen(this, it); rebuildFromSong()
                    })
                }
            }
        }

        if (i == Instruments.GUITAR) {
            section.addView(label("Modo"))
            section.addView(chips(listOf("Notas", "Acordes"), Settings.guitarMode(this)) {
                Settings.setGuitarMode(this, it); rebuildSection(); preview.reload(); preview.demo()
            })
            if (Settings.guitarMode(this) == 1) {
                section.addView(label("Acordes"))
                section.addView(chips(listOf("Rock", "Pop", "Triste"), Settings.guitarProg(this)) {
                    Settings.setGuitarProg(this, it); rebuildSection(); preview.reload(); preview.demo()
                })
            }
        }
        if (i == Instruments.KEYS) {
            section.addView(label("Modo"))
            section.addView(chips(listOf("Notas", "Acordes"), Settings.keysMode(this)) {
                Settings.setKeysMode(this, it); rebuildSection(); preview.reload(); preview.demo()
            })
            section.addView(label("Escala"))
            section.addView(chips(listOf("Libre", "Mayor", "Menor", "Pentatónica"), Settings.keysScale(this)) {
                Settings.setKeysScale(this, it); rebuildSection(); preview.reload(); preview.demo()
            })
            if (Settings.keysScale(this) > 0) section.addView(hint("Todas las teclas suenan dentro de la escala: no hay notas equivocadas."))
        }

        section.addView(label("Efectos"))
        section.addView(slider("Filtro", Settings.cut(this, i), { if (it >= 99) "Abierto" else "${SoundStore.cutoffHz(it).toInt()} Hz" }) {
            Settings.setCut(this, i, it); prepareAsync()
        })
        section.addView(slider("Eco", Settings.echo(this, i), { "$it%" }) {
            Settings.setEcho(this, i, it); prepareAsync()
        })
    }

    private fun currentWindowLen(): Float = when (instrument) {
        Instruments.PADS -> if (Settings.padMode(this) == 0) floatArrayOf(4f, 8f, 16f)[Settings.padLen(this)] else 0f
        Instruments.GUITAR, Instruments.KEYS -> 1.6f
        else -> 0f
    }

    // ---------- acciones ----------
    private fun chooseInstrument(i: Int) {
        instrument = i
        Instruments.select(this, i)
        markers = emptyList()
        refreshStatic()
        rebuildSection()
        preview.setInstrument(i, color)
    }

    private fun chooseColor(i: Int) {
        color = i
        Palettes.select(this, i)
        preview.scene.setPalette(i)
        preview.invalidate()
        refreshStatic()
        rebuildSection()
    }

    private fun refreshStatic() {
        instrumentChips.forEachIndexed { i, chip -> chipStyle(chip, i == instrument) }
        colorDots.forEachIndexed { i, d -> if (i == color) d.setStroke(px(3), Color.WHITE) else d.setStroke(0, Color.TRANSPARENT) }
        status.setTextColor(accent)
        button.setTextColor(onAccent)
        button.background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(accent) }
    }

    private fun chooseCustom() {
        when {
            songPcm != null -> rebuildFromSong()
            SoundStore.hasSource(this, instrument) -> {
                Settings.setKit(this, instrument, -1); rebuildSection(); prepareAsync()
            }
            else -> pickSong()
        }
    }

    private fun pickSong() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_SONG)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el selector de archivos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SONG || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runBg("Leyendo canción…", {
            val pcm = AudioDecoder.decode(this, uri)
            songPcm = pcm
            songName = displayName(uri)
            windowStart = (pcm.seconds * 0.3f).coerceAtMost((pcm.seconds - 4f).coerceAtLeast(0f))
            buildSource()
        }) {
            rebuildSection(); preview.reload(); preview.demo()
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        } catch (e: Exception) { }
        return "Canción"
    }

    private fun pickPosition(t: Float) {
        val p = songPcm ?: return
        val len = currentWindowLen()
        if (len <= 0f) return
        windowStart = (t - len / 2).coerceIn(0f, (p.seconds - len).coerceAtLeast(0f))
        rebuildFromSong()
    }

    private fun rebuildFromSong() {
        if (songPcm == null) {
            if (SoundStore.hasSource(this, instrument)) { Settings.setKit(this, instrument, -1); rebuildSection(); prepareAsync() }
            else pickSong()
            return
        }
        runBg("Creando sonidos…", { buildSource() }) { rebuildSection(); preview.reload(); preview.demo() }
    }

    /** Corta la canción para el instrumento actual y prepara los sonidos (en segundo plano). */
    private fun buildSource() {
        val p = songPcm ?: return
        val i = instrument
        val fmt = { s: Float -> "${(s / 60).toInt()}:${String.format("%02d", (s % 60).toInt())}" }
        val parts: List<ShortArray>
        val labels: List<String>
        when (i) {
            Instruments.DRUMS, Instruments.PADS -> {
                val slices = if (i == Instruments.DRUMS) Slicer.golpes(p, 8)
                else if (Settings.padMode(this) == 0) Slicer.ritmo(p, windowStart, floatArrayOf(4f, 8f, 16f)[Settings.padLen(this)])
                else Slicer.golpes(p, 16)
                parts = slices.map { Slicer.render(p, it, 20000f, 0f) }
                val secs = slices.map { it.start.toFloat() / p.sampleRate }
                labels = secs.map(fmt)
                markers = secs
            }
            else -> {
                val start = (windowStart * p.sampleRate).toInt()
                val base = Slicer.render(p, Slice(start, (1.6f * p.sampleRate).toInt()), 20000f, 0f)
                parts = (0 until 13).map { j -> Slicer.pitch(base, 2f.pow(j / 12f)) }
                labels = emptyList()
                markers = listOf(windowStart)
            }
        }
        SoundStore.saveSource(this, i, parts, p.sampleRate, labels, songName)
        SoundStore.prepare(this, i)
    }

    private fun prepareAsync() {
        val i = instrument
        runBg("Preparando sonidos…", { SoundStore.prepare(this, i) }) { preview.reload(); preview.demo() }
    }

    private fun runBg(msg: String, work: () -> Unit, done: () -> Unit) {
        status.text = msg
        worker.execute {
            try {
                work()
                runOnUiThread { status.text = ""; done() }
            } catch (e: Throwable) {
                runOnUiThread { status.text = "No se pudo: ${e.message ?: "error desconocido"}. Prueba con un MP3." }
            }
        }
    }

    private fun applyWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, RockWallpaperService::class.java)
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                Toast.makeText(this, "Elige «Distorsión» en la lista", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Tu teléfono no permitió abrir el selector de fondos animados", Toast.LENGTH_LONG).show()
            }
        }
    }
}
