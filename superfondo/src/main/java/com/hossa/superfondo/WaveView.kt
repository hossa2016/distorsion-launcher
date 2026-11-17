package com.hossa.superfondo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Onda de la canción: toca para elegir la parte que se usa. */
class WaveView(context: Context) : View(context) {
    var peaks: FloatArray? = null
    var duration = 1f
    var windowStart = 0f
    var windowLen = 0f
    var markers: List<Float> = emptyList()
    var accent = Color.RED
    var onPick: ((Float) -> Unit)? = null

    private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 18, 21) }
    private val pBar = Paint()
    private val pWin = Paint().apply { color = Color.argb(28, 255, 255, 255) }
    private val pMark = Paint()
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, 24f, 24f, pBg)
        val pk = peaks ?: return
        if (windowLen > 0f) {
            val x0 = windowStart / duration * w
            val x1 = (windowStart + windowLen).coerceAtMost(duration) / duration * w
            c.drawRect(x0, 0f, x1, h, pWin)
        }
        val bw = w / pk.size
        for (i in pk.indices) {
            val t = i.toFloat() / pk.size * duration
            val inside = windowLen <= 0f || (t >= windowStart && t < windowStart + windowLen)
            pBar.color = if (inside) Color.rgb(120, 113, 105) else Color.rgb(52, 52, 58)
            val bh = (pk[i] * h * 0.85f).coerceAtLeast(2f)
            c.drawRect(i * bw, (h - bh) / 2, i * bw + bw * 0.7f, (h + bh) / 2, pBar)
        }
        pMark.color = accent
        for (m in markers) {
            val x = m / duration * w
            c.drawRect(x, 0f, x + 2.5f, h, pMark)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (peaks == null) return false
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            onPick?.invoke((e.x / width).coerceIn(0f, 1f) * duration)
        }
        return true
    }
}
