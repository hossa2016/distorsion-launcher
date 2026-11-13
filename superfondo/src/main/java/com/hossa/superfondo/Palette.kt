package com.hossa.superfondo

import android.content.Context
import android.graphics.Color

data class Palette(
    val name: String,
    val accent: Int,      // color principal del brillo
    val hot: Int,         // anillos y ondas
    val ring: Int,        // borde de platillos al golpear
    val ember2: Int,      // brasas claras
    val shell: Int,       // centro del bombo
    val bg: Int,          // tinte del fondo
    val thumb: Int        // imagen de vista previa
)

object Palettes {
    val all = listOf(
        Palette(
            "Rojo", Color.parseColor("#E1252F"), Color.parseColor("#FF2E3A"), Color.parseColor("#FF5A3A"),
            Color.parseColor("#FF8C46"), Color.parseColor("#3A1416"), Color.parseColor("#1C1112"), R.drawable.thumb_rojo
        ),
        Palette(
            "Azul", Color.parseColor("#2F7BFF"), Color.parseColor("#3D9BFF"), Color.parseColor("#6FB6FF"),
            Color.parseColor("#9FD3FF"), Color.parseColor("#13233F"), Color.parseColor("#0E1420"), R.drawable.thumb_azul
        ),
        Palette(
            "Plata", Color.parseColor("#C9CED6"), Color.parseColor("#E8ECF2"), Color.parseColor("#FFFFFF"),
            Color.parseColor("#FFFFFF"), Color.parseColor("#2C2E33"), Color.parseColor("#15161A"), R.drawable.thumb_plata
        )
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("superfondo", Context.MODE_PRIVATE)

    fun selectedIndex(ctx: Context): Int = sp(ctx).getInt("palette", 0).coerceIn(0, all.size - 1)

    fun select(ctx: Context, index: Int) {
        sp(ctx).edit().putInt("palette", index).apply()
    }
}
