package com.hossa.superfondo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private class Key(val semitone: Int, val black: Boolean, val rect: RectF) {
    var pressAt = -100_000L
    var strength = 0f
    fun env(now: Long): Float = if (now < pressAt) 0f else strength * exp(-(now - pressAt) / 380f)
}

private class Beam(val x: Float, val width: Float, val start: Long)

class KeysScene : BaseScene() {
    override val pivotY = 1600f
    override val homeBright = 0.85f

    private val bodyRect = RectF(40f, 1180f, 1180f, 2400f)
    private val screenRect = RectF(380f, 1250f, 840f, 1400f)
    private val keysTop = 1600f
    private val whiteH = 730f
    private val blackH = 440f
    private val left = 80f
    private val whiteW = (1140f - 80f) / 8f
    private val whites = ArrayList<Key>()
    private val blacks = ArrayList<Key>()
    private val beams = ArrayList<Beam>()
    private val knobs = listOf(Pair(150f, 1300f), Pair(270f, 1300f), Pair(950f, 1300f), Pair(1070f, 1300f))

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBodyRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.rgb(48, 46, 45) }
    private val pScreen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(6, 6, 7) }
    private val pScreenText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 42f; letterSpacing = 0.28f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val pWave = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val pWhite = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pWhiteDown = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBlack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBlackDown = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGap = Paint().apply { color = Color.rgb(5, 5, 6) }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBeam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pKnob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pKnobLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; color = Color.rgb(236, 230, 221) }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 20f; color = Color.argb(140, 236, 230, 221); letterSpacing = 0.15f }
    private val tmp = RectF()

    init {
        val whiteSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
        for (i in 0 until 8) whites.add(Key(whiteSemis[i], false, RectF(left + i * whiteW, keysTop, left + (i + 1) * whiteW, keysTop + whiteH)))
        val blackAfter = intArrayOf(0, 1, 3, 4, 5)
        val blackSemis = intArrayOf(1, 3, 6, 8, 10)
        for (j in 0 until 5) {
            val cx = left + (blackAfter[j] + 1) * whiteW
            blacks.add(Key(blackSemis[j], true, RectF(cx - 41f, keysTop, cx + 41f, keysTop + blackH)))
        }
        pBody.shader = LinearGradient(0f, bodyRect.top, 0f, bodyRect.bottom,
            intArrayOf(Color.rgb(30, 29, 29), Color.rgb(16, 15, 15), Color.rgb(9, 9, 9)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        pWhite.shader = LinearGradient(0f, keysTop, 0f, keysTop + whiteH,
            intArrayOf(Color.rgb(46, 46, 50), Color.rgb(36, 36, 40), Color.rgb(28, 28, 31)), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
        pWhiteDown.shader = LinearGradient(0f, keysTop, 0f, keysTop + whiteH,
            intArrayOf(Color.rgb(30, 30, 33), Color.rgb(24, 24, 27)), null, Shader.TileMode.CLAMP)
        pBlack.shader = LinearGradient(0f, keysTop, 0f, keysTop + blackH,
            intArrayOf(Color.rgb(12, 12, 13), Color.rgb(20, 20, 22), Color.rgb(6, 6, 7)), floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP)
        pBlackDown.color = Color.rgb(8, 8, 9)
        setPalette(0)
        resize(1080, 2400)
    }

    override fun onPaletteChanged() {
        pBeam.shader = LinearGradient(0f, 0f, 0f, 1f,
            intArrayOf(withAlpha(palette.accent, 0), withAlpha(palette.hot, 170)), null, Shader.TileMode.CLAMP)
        pScreenText.color = palette.hot
        pWave.color = withAlpha(palette.accent, 200)
        pKnob.shader = RadialGradient(0f, -10f, 50f,
            intArrayOf(Color.rgb(70, 68, 66), Color.rgb(28, 27, 27)), null, Shader.TileMode.CLAMP)
    }

    private fun keyFor(semi: Int): Key? = (whites + blacks).firstOrNull { it.semitone == semi }

    private fun press(semi: Int, strength: Float, at: Long, withBeam: Boolean = true) {
        val k = keyFor(semi) ?: return
        k.pressAt = at; k.strength = strength
        if (withBeam) beams.add(Beam(k.rect.centerX(), k.rect.width() * 0.7f, at))
        if (beams.size > 24) beams.removeAt(0)
    }

    override fun performScreenOn(now: Long) = press(9, 0.6f, now + 400)

    override fun performUnlock(now: Long) {
        val arp = intArrayOf(0, 4, 7, 12)
        arp.forEachIndexed { i, s ->
            press(s, 1f, now + i * 110L)
            sound("key_$s", 0.65f, i * 110L, false)
        }
    }

    override fun idle(now: Long) = press(intArrayOf(0, 4, 7, 9, 12)[rnd.nextInt(5)], 0.35f, now)

    override fun hitTest(bx: Float, by: Float, now: Long): Boolean {
        val k = blacks.firstOrNull { it.rect.contains(bx, by) } ?: whites.firstOrNull { it.rect.contains(bx, by) } ?: return false
        press(k.semitone, 1f, now)
        sound("key_${k.semitone}", 0.9f, 0L, true)
        return true
    }

    override fun contentBusy(now: Long): Boolean {
        beams.removeAll { now - it.start > 1800 }
        return beams.isNotEmpty() || (whites + blacks).any { it.env(now) > 0.02f || now < it.pressAt }
    }

    override fun drawContent(c: Canvas, now: Long) {
        // haces de luz que suben desde las teclas
        for (b in beams) {
            if (now < b.start) continue
            val t = (now - b.start) / 1800f
            val yTop = keysTop - 200f - t * 2000f
            val hgt = 700f
            c.save()
            c.translate(b.x - b.width / 2, yTop)
            c.scale(b.width, hgt)
            pBeam.alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
            c.drawRect(0f, 0f, 1f, 1f, pBeam)
            c.restore()
        }
        pBeam.alpha = 255

        // cuerpo
        pFill.color = Color.argb(170, 0, 0, 0)
        tmp.set(bodyRect.left - 10f, bodyRect.top + 30f, bodyRect.right + 10f, bodyRect.bottom + 60f)
        c.drawRoundRect(tmp, 60f, 60f, pFill)
        c.drawRoundRect(bodyRect, 46f, 46f, pBody)
        c.drawRoundRect(bodyRect, 46f, 46f, pBodyRim)

        // pantalla
        val en = energy.get(now)
        c.drawRoundRect(screenRect, 14f, 14f, pScreen)
        c.drawText("DISTORSIÓN", screenRect.centerX(), screenRect.top + 62f, pScreenText)
        var px = screenRect.left + 30f
        var prevY = screenRect.top + 110f
        val tt = now / 1000f
        while (px < screenRect.right - 30f) {
            val ny = screenRect.top + 110f + sin(px * 0.05f + tt * 8f) * (4f + 18f * en) * sin(px * 0.011f)
            c.drawLine(px, prevY, px + 6f, ny, pWave)
            prevY = ny; px += 6f
        }

        // perillas
        knobs.forEachIndexed { i, (kx, ky) ->
            c.save(); c.translate(kx, ky)
            c.drawCircle(0f, 0f, 44f, pKnob)
            val ang = (-130f + 260f * (0.3f + 0.15f * i + 0.35f * en)) * (Math.PI / 180f).toFloat()
            c.drawLine(sin(ang) * 14f, -cos(ang) * 14f, sin(ang) * 36f, -cos(ang) * 36f, pKnobLine)
            c.restore()
            c.drawText(arrayOf("CORTE", "RESON", "ECO", "VOL")[i], kx, ky + 80f, pLabel)
        }

        // teclas
        c.drawRect(left - 6f, keysTop - 6f, left + 8 * whiteW + 6f, keysTop + whiteH + 6f, pGap)
        for (k in whites) drawKey(c, k, now, pWhite, pWhiteDown, 10f)
        for (k in blacks) drawKey(c, k, now, pBlack, pBlackDown, 8f)
        // franja de color sobre las teclas
        pFill.color = withAlpha(palette.accent, 200)
        c.drawRect(left, keysTop - 6f, left + 8 * whiteW, keysTop - 2f, pFill)
    }

    private fun drawKey(c: Canvas, k: Key, now: Long, up: Paint, down: Paint, radius: Float) {
        val e = k.env(now)
        val off = 8f * e
        tmp.set(k.rect.left + 3f, k.rect.top + off, k.rect.right - 3f, k.rect.bottom + off)
        c.drawRoundRect(tmp, radius, radius, if (e > 0.05f) down else up)
        if (!k.black) {
            pFill.color = Color.argb(30, 255, 255, 255)
            c.drawRect(tmp.left + 4f, tmp.top, tmp.left + 6f, tmp.bottom - 20f, pFill)
        }
        if (e > 0.02f) {
            pFill.color = withAlpha(palette.accent, (120 * e).toInt())
            c.drawRoundRect(tmp, radius, radius, pFill)
            pFill.color = withAlpha(palette.hot, (255 * e).toInt())
            c.drawRect(tmp.left, tmp.top, tmp.right, tmp.top + 6f, pFill)
        }
    }
}
