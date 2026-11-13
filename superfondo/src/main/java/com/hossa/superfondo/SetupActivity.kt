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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val image = ImageView(this).apply {
            setImageResource(R.drawable.thumb)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (48 * dp).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(235, 5, 5, 6))
            )
        }

        val title = TextView(this).apply {
            text = "DISTORSIÓN"
            setTextColor(Color.parseColor("#ECE6DD"))
            textSize = 30f
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "Superfondo animado · acorde al desbloquear · toca las cuerdas"
            setTextColor(Color.parseColor("#8E867C"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (24 * dp).toInt())
        }
        val button = Button(this).apply {
            text = "Aplicar superfondo"
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 18 * dp
                setColor(Color.parseColor("#E1252F"))
            }
            setOnClickListener { applyWallpaper() }
        }

        panel.addView(title)
        panel.addView(sub)
        panel.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (58 * dp).toInt()))

        root.addView(
            panel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )
        setContentView(root)
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
