package com.hossa.superfondo

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SetupActivity : Activity() {

    private lateinit var sounds: SoundBank
    private lateinit var preview: PreviewView
    private lateinit var panel: LinearLayout
    private lateinit var showBtn: TextView
    private lateinit var button: Button
    private val instrumentChips = ArrayList<TextView>()
    private val colorDots = ArrayList<GradientDrawable>()
    private var instrument = 0
    private var color = 0
    private var dp = 1f

    private val ink = Color.parseColor("#ECE6DD")
    private val muted = Color.parseColor("#8E867C")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        instrument = Instruments.selectedIndex(this)
        color = Palettes.selectedIndex(this)
        sounds = SoundBank(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this, sounds)
        root.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(56), px(20), px(36))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(235, 5, 5, 6), Color.argb(250, 5, 5, 6))
            )
            isClickable = true
        }

        // título + botón ocultar
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "DISTORSIÓN"; setTextColor(ink); textSize = 24f; letterSpacing = 0.3f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(this).apply {
            text = "Probar ▾"; setTextColor(muted); textSize = 14f
            setPadding(px(12), px(8), px(4), px(8))
            setOnClickListener { setPanelVisible(false) }
        })
        panel.addView(head)

        panel.addView(label("Instrumento"))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Instruments.all.forEachIndexed { i, info ->
            val chip = TextView(this).apply {
                text = info.name
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(px(18), px(10), px(18), px(10))
                setOnClickListener { chooseInstrument(i) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = px(8)
            row.addView(chip, lp)
            instrumentChips.add(chip)
        }
        scroll.addView(row)
        panel.addView(scroll)

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
            item.addView(View(this).apply { background = dot }, LinearLayout.LayoutParams(px(30), px(30)))
            item.addView(TextView(this).apply { text = pal.name; setTextColor(ink); textSize = 14f; setPadding(px(8), 0, 0, 0) })
            colors.addView(item)
        }
        panel.addView(colors)

        panel.addView(toggle("Sonido al tocar", Palettes.soundTouch(this)) { Palettes.setSoundTouch(this, it) })
        panel.addView(toggle("Sonido al desbloquear (no suena en silencio)", Palettes.soundUnlock(this)) { Palettes.setSoundUnlock(this, it) })

        button = Button(this).apply {
            text = "Aplicar superfondo"; isAllCaps = false; textSize = 17f
            setOnClickListener { applyWallpaper() }
        }
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56))
        blp.topMargin = px(14)
        panel.addView(button, blp)

        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        showBtn = TextView(this).apply {
            text = "Opciones ▴"; setTextColor(ink); textSize = 14f
            setPadding(px(18), px(10), px(18), px(10))
            background = GradientDrawable().apply { cornerRadius = 22 * dp; setColor(Color.argb(200, 20, 20, 24)) }
            visibility = View.GONE
            setOnClickListener { setPanelVisible(true) }
        }
        val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        slp.bottomMargin = px(36)
        root.addView(showBtn, slp)

        setContentView(root)
        refresh()
        preview.setInstrument(instrument, color)
    }

    override fun onDestroy() {
        sounds.release()
        super.onDestroy()
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun label(t: String) = TextView(this).apply {
        text = t.uppercase(); setTextColor(muted); textSize = 11f; letterSpacing = 0.15f
        setPadding(0, px(14), 0, px(6))
    }

    private fun toggle(t: String, value: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t; textSize = 14f; setTextColor(ink); isChecked = value
        setPadding(0, px(6), 0, px(2))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    private fun setPanelVisible(v: Boolean) {
        panel.visibility = if (v) View.VISIBLE else View.GONE
        showBtn.visibility = if (v) View.GONE else View.VISIBLE
        if (!v) Toast.makeText(this, "Toca el instrumento para probarlo", Toast.LENGTH_SHORT).show()
    }

    private fun chooseInstrument(i: Int) {
        instrument = i
        Instruments.select(this, i)
        refresh()
        preview.setInstrument(i, color)
    }

    private fun chooseColor(i: Int) {
        color = i
        Palettes.select(this, i)
        preview.scene.setPalette(i)
        preview.invalidate()
        refresh()
    }

    private fun refresh() {
        val pal = Palettes.all[color]
        instrumentChips.forEachIndexed { i, chip ->
            val on = i == instrument
            chip.setTextColor(if (on) (if (color == 2) Color.parseColor("#111114") else Color.WHITE) else ink)
            chip.background = GradientDrawable().apply {
                cornerRadius = 20 * dp
                if (on) setColor(pal.accent) else { setColor(Color.argb(160, 30, 30, 34)); setStroke(px(1), Color.argb(40, 255, 255, 255)) }
            }
        }
        colorDots.forEachIndexed { i, d -> if (i == color) d.setStroke(px(3), Color.WHITE) else d.setStroke(0, Color.TRANSPARENT) }
        button.setTextColor(if (color == 2) Color.parseColor("#111114") else Color.WHITE)
        button.background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(pal.accent) }
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
