package com.hossa.superfondo

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SetupActivity : Activity() {

    private lateinit var image: ImageView
    private lateinit var button: Button
    private val chips = ArrayList<Pair<LinearLayout, GradientDrawable>>()
    private var selected = 0
    private var dp = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dp = resources.displayMetrics.density
        selected = Palettes.selectedIndex(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(24), px(60), px(24), px(44))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(240, 5, 5, 6))
            )
        }

        panel.addView(TextView(this).apply {
            text = "DISTORSIÓN"
            setTextColor(Color.parseColor("#ECE6DD"))
            textSize = 30f
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
        })
        panel.addView(TextView(this).apply {
            text = "Superfondo de batería · redoble al desbloquear · toca los tambores"
            setTextColor(Color.parseColor("#8E867C"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, px(8), 0, px(22))
        })

        // selector de color
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        Palettes.all.forEachIndexed { i, pal ->
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pal.accent)
            }
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(px(14), px(10), px(14), px(10))
                setOnClickListener { choose(i) }
            }
            val circle = android.view.View(this).apply { background = dot }
            chip.addView(circle, LinearLayout.LayoutParams(px(44), px(44)))
            chip.addView(TextView(this).apply {
                text = pal.name
                setTextColor(Color.parseColor("#CFC8BF"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, px(6), 0, 0)
            })
            chips.add(Pair(chip, dot))
            row.addView(chip)
        }
        panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        button = Button(this).apply {
            text = "Aplicar superfondo"
            isAllCaps = false
            textSize = 17f
            setOnClickListener { applyWallpaper() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(58))
        lp.topMargin = px(18)
        panel.addView(button, lp)

        root.addView(
            panel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )
        setContentView(root)
        render()
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun choose(i: Int) {
        selected = i
        Palettes.select(this, i)
        render()
    }

    private fun render() {
        val pal = Palettes.all[selected]
        image.setImageResource(pal.thumb)
        chips.forEachIndexed { i, (_, dot) ->
            if (i == selected) dot.setStroke(px(3), Color.WHITE) else dot.setStroke(0, Color.TRANSPARENT)
        }
        val isSilver = selected == 2
        button.setTextColor(if (isSilver) Color.parseColor("#111114") else Color.WHITE)
        button.background = GradientDrawable().apply {
            cornerRadius = 18 * dp
            setColor(pal.accent)
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
