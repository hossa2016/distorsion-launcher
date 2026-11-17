package com.hossa.superfondo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private class GString(val x: Float, val thick: Float, val freq: Float) {
    var pluckAt = -100_000L
    var amp = 0f
    var centerY = 1200f
    var shader: Shader? = null

    fun env(now: Long): Float = if (now < pluckAt) 0f else amp * exp(-(now - pluckAt) / 1500f)
}

class GuitarScene : BaseScene() {
    override val pivotY = 1350f

    private val top = -800f
    private val boardEnd = 1950f
    private val bridgeY = 2650f
    private val strings = listOf(
        GString(300f, 7f, 4.2f), GString(424f, 6f, 5.1f), GString(548f, 5f, 6.0f),
        GString(672f, 4f, 6.9f), GString(796f, 3f, 7.8f), GString(920f, 2.4f, 8.7f)
    )
    private val frets = ArrayList<Float>()

    private val pBoard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 11, 11) }
    private val pEdge = Paint().apply { color = Color.rgb(42, 37, 36) }
    private val pFret = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFretShadow = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val pInlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 217, 210, 198) }
    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pPickup = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 16, 17) }
    private val pPickupRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val pPole = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pSaddle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pString = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pShadow = Paint().apply { color = Color.argb(130, 0, 0, 0) }
    private val path = Path()
    private val tmp = RectF()
    private val metal = Color.rgb(205, 198, 188)

    init {
        var y = -300f
        for (k in 0 until 18) {
            y += 330f * 0.955f.pow(k)
            if (y < boardEnd - 20f) frets.add(y)
        }
        pFret.shader = LinearGradient(
            0f, 0f, 0f, 9f,
            intArrayOf(Color.rgb(110, 106, 101), Color.rgb(205, 200, 191), Color.rgb(70, 68, 66)),
            null, Shader.TileMode.CLAMP
        )
        pFret.alpha = 110
        for (s in strings) {
            s.shader = LinearGradient(
                s.x - s.thick / 2, 0f, s.x + s.thick / 2, 0f,
                intArrayOf(Color.rgb(93, 90, 87), Color.rgb(233, 228, 220), Color.rgb(163, 157, 149), Color.rgb(62, 60, 58)),
                floatArrayOf(0f, 0.45f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
        }
        pBody.shader = LinearGradient(
            0f, boardEnd, 1220f, boardEnd + 600f,
            intArrayOf(Color.rgb(8, 7, 7), Color.rgb(24, 22, 22), Color.rgb(6, 6, 6)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        pPickupRim.shader = LinearGradient(
            0f, 0f, 1220f, 0f,
            intArrayOf(Color.rgb(90, 87, 83), Color.rgb(230, 225, 217), Color.rgb(110, 106, 101), Color.rgb(200, 195, 187)),
            null, Shader.TileMode.CLAMP
        )
        pPole.color = Color.rgb(185, 180, 172)
        pSaddle.color = Color.rgb(170, 165, 158)
        setPalette(0)
        resize(1080, 2400)
    }

    override fun onPaletteChanged() {}

    private fun pluck(i: Int, amp: Float, cy: Float, at: Long) {
        val s = strings.getOrNull(i) ?: return
        s.amp = amp; s.centerY = cy.coerceIn(300f, boardEnd + 200f); s.pluckAt = at
    }

    override fun performScreenOn(now: Long) = pluck(2, 9f, 1250f, now + 350)

    override fun performUnlock(now: Long) {
        for (i in strings.indices) pluck(i, 30f - i * 2.4f, 1150f, now + i * 32L)
        sound("gtr_chord", 0.75f, 0L, false)
    }

    override fun idle(now: Long) = pluck(rnd.nextInt(strings.size), 5f + rnd.nextFloat() * 4f, 900f + rnd.nextFloat() * 700f, now)

    override fun hitTest(bx: Float, by: Float, now: Long): Boolean {
        if (by < top || by > bridgeY) return false
        var best = -1
        var bd = 70f
        strings.forEachIndexed { i, s -> val d = abs(s.x - bx); if (d < bd) { bd = d; best = i } }
        if (best < 0) return false
        pluck(best, 26f, by, now)
        sound("gtr_$best", 0.9f, 0L, true)
        return true
    }

    override fun contentBusy(now: Long) = strings.any { it.env(now) > 0.4f || now < it.pluckAt }

    override fun drawContent(c: Canvas, now: Long) {
        // cuerpo y diapasón
        tmp.set(-600f, boardEnd - 40f, 1820f, 3600f)
        c.drawRoundRect(tmp, 120f, 120f, pBody)
        c.drawRect(225f, top, 995f, boardEnd, pBoard)
        c.drawRect(225f, top, 228f, boardEnd, pEdge)
        c.drawRect(992f, top, 995f, boardEnd, pEdge)
        for (fy in frets) {
            c.save(); c.translate(0f, fy)
            c.drawRect(228f, 0f, 992f, 9f, pFret)
            c.restore()
            c.drawRect(228f, fy + 9f, 992f, fy + 15f, pFretShadow)
        }
        for (k in intArrayOf(2, 4, 6, 8)) if (k + 1 < frets.size) c.drawCircle(610f, (frets[k] + frets[k + 1]) / 2, 20f, pInlay)
        if (frets.size > 12) {
            val cy = (frets[11] + frets[12]) / 2
            c.drawCircle(486f, cy, 18f, pInlay); c.drawCircle(734f, cy, 18f, pInlay)
        }
        // cejuela final del diapasón
        c.drawRect(225f, boardEnd - 6f, 995f, boardEnd, pEdge)
        drawPickup(c, 2160f, now)
        drawPickup(c, 2420f, now)
        // puente
        for (s in strings) {
            tmp.set(s.x - 22f, bridgeY - 14f, s.x + 22f, bridgeY + 22f)
            c.drawRoundRect(tmp, 6f, 6f, pSaddle)
        }
        for (s in strings) drawString(c, s, now)
    }

    private fun drawPickup(c: Canvas, y: Float, now: Long) {
        tmp.set(250f, y - 60f, 970f, y + 60f)
        c.drawRoundRect(tmp, 30f, 30f, pPickup)
        c.drawRoundRect(tmp, 30f, 30f, pPickupRim)
        val heat = strings.maxOf { it.env(now) } / 30f
        for (s in strings) {
            pPole.color = blend(Color.rgb(185, 180, 172), palette.hot, min(1f, heat))
            c.drawCircle(s.x, y - 22f, 11f, pPole)
            c.drawCircle(s.x, y + 22f, 11f, pPole)
        }
    }

    private fun drawString(c: Canvas, s: GString, now: Long) {
        val e = s.env(now)
        c.drawRect(s.x + s.thick / 2, top, s.x + s.thick / 2 + 3f, bridgeY, pShadow)
        if (e < 0.4f) {
            pString.shader = s.shader
            pString.color = Color.WHITE
            pString.strokeWidth = s.thick
            c.drawLine(s.x, top, s.x, bridgeY, pString)
            return
        }
        val len = 1250f
        val y0 = s.centerY - len / 2
        val y1 = s.centerY + len / 2
        val t = (now - s.pluckAt) / 1000f
        path.reset()
        path.moveTo(s.x, top)
        path.lineTo(s.x, y0)
        var y = y0
        while (y <= y1) {
            val uu = (y - y0) / len
            val env = sin(PI * uu).toFloat()
            val wave = sin(2 * PI * (7.0 * uu - s.freq * t)).toFloat()
            path.lineTo(s.x + e * env * wave, y)
            y += 6f
        }
        path.lineTo(s.x, y1)
        path.lineTo(s.x, bridgeY)
        val heat = min(1f, e / 14f)
        pGlow.color = withAlpha(palette.accent, (35 * heat).toInt())
        pGlow.strokeWidth = s.thick + 22f
        c.drawPath(path, pGlow)
        pGlow.color = withAlpha(palette.accent, (80 * heat).toInt())
        pGlow.strokeWidth = s.thick + 9f
        c.drawPath(path, pGlow)
        pString.shader = null
        pString.color = blend(metal, palette.hot, heat)
        pString.strokeWidth = s.thick
        c.drawPath(path, pString)
    }
}
