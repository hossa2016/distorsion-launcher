package com.hossa.superfondo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.exp

private class Pad(val index: Int, val rect: RectF) {
    var hitAt = -100_000L
    var strength = 0f
    var glow: Shader? = null
    fun env(now: Long): Float = if (now < hitAt) 0f else strength * exp(-(now - hitAt) / 420f)
}

class PadsScene : BaseScene() {
    override val pivotY = 1420f

    /** Etiquetas de tiempo cuando los pads vienen de una canción propia. */
    var labels: List<String> = emptyList()

    private val bodyRect = RectF(70f, 760f, 1150f, 2090f)
    private val pads = ArrayList<Pad>()
    private val padSize = 232f
    private val gap = 24f
    private val gridTop = 1020f
    private val gridLeft = 110f
    private var seqStart = -100_000L
    private val stepMs = 120L

    // patrón al desbloquear: lista de pads por paso
    private val pattern = listOf(
        intArrayOf(0, 12), intArrayOf(3), intArrayOf(3, 8), intArrayOf(1),
        intArrayOf(3, 0), intArrayOf(0), intArrayOf(3, 9), intArrayOf(2, 4)
    )

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.rgb(46, 44, 43) }
    private val pPad = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pPadEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; textSize = 34f; letterSpacing = 0.3f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); color = Color.argb(210, 236, 230, 221)
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 30f; color = Color.argb(170, 236, 230, 221)
    }
    private val tmp = RectF()

    init {
        for (r in 0 until 4) for (col in 0 until 4) {
            val x = gridLeft + col * (padSize + gap)
            val y = gridTop + r * (padSize + gap)
            pads.add(Pad(r * 4 + col, RectF(x, y, x + padSize, y + padSize)))
        }
        pBody.shader = LinearGradient(0f, bodyRect.top, 0f, bodyRect.bottom,
            intArrayOf(Color.rgb(28, 27, 27), Color.rgb(15, 14, 14), Color.rgb(9, 9, 9)), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
        pPad.shader = LinearGradient(0f, 0f, padSize, padSize,
            intArrayOf(Color.rgb(34, 34, 38), Color.rgb(19, 19, 22), Color.rgb(13, 13, 15)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        setPalette(0)
        resize(1080, 2400)
    }

    override fun onPaletteChanged() {
        for (p in pads) {
            p.glow = RadialGradient(p.rect.centerX(), p.rect.centerY(), padSize * 0.75f,
                intArrayOf(palette.hot, palette.accent, withAlpha(palette.accent, 0)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        pPadEdge.color = withAlpha(palette.accent, 40)
    }

    private fun hit(i: Int, strength: Float, at: Long) {
        val p = pads.getOrNull(i) ?: return
        p.hitAt = at; p.strength = strength
    }

    override fun performScreenOn(now: Long) = hit(5, 0.5f, now + 400)

    override fun performUnlock(now: Long) {
        seqStart = now
        pattern.forEachIndexed { step, ids ->
            for (id in ids) {
                hit(id, 1f, now + step * stepMs)
                sound("s:$id", 0.6f, step * stepMs, false)
            }
        }
    }

    override fun idle(now: Long) = hit(rnd.nextInt(16), 0.3f, now)

    override fun hitTest(bx: Float, by: Float, now: Long): Boolean {
        val p = pads.firstOrNull { it.rect.contains(bx, by) } ?: return false
        hit(p.index, 1f, now)
        sound("s:${p.index}", 0.9f, 0L, true)
        return true
    }

    override fun contentBusy(now: Long): Boolean =
        now - seqStart < pattern.size * stepMs + 300 || pads.any { it.env(now) > 0.02f || now < it.hitAt }

    override fun drawContent(c: Canvas, now: Long) {
        pFill.color = Color.argb(170, 0, 0, 0)
        tmp.set(bodyRect.left - 10f, bodyRect.top + 30f, bodyRect.right + 10f, bodyRect.bottom + 60f)
        c.drawRoundRect(tmp, 60f, 60f, pFill)
        c.drawRoundRect(bodyRect, 44f, 44f, pBody)
        c.drawRoundRect(bodyRect, 44f, 44f, pRim)

        c.drawText("DISTORSIÓN", gridLeft, 850f, pText)
        // indicador de pasos
        val step = if (now >= seqStart && now - seqStart < pattern.size * stepMs) ((now - seqStart) / stepMs).toInt() else -1
        for (i in 0 until 16) {
            val x = gridLeft + i * ((1000f - 15 * 10f) / 16f + 10f)
            val wLed = (1000f - 15 * 10f) / 16f
            val on = i / 2 == step
            pFill.color = if (on) palette.hot else Color.rgb(36, 35, 38)
            tmp.set(x, 920f, x + wLed, 940f)
            c.drawRoundRect(tmp, 6f, 6f, pFill)
        }

        for (p in pads) {
            val e = p.env(now)
            val k = 1f - 0.04f * e
            c.save()
            c.scale(k, k, p.rect.centerX(), p.rect.centerY())
            c.save(); c.translate(p.rect.left, p.rect.top)
            tmp.set(0f, 0f, padSize, padSize)
            c.drawRoundRect(tmp, 30f, 30f, pPad)
            c.drawRoundRect(tmp, 30f, 30f, pPadEdge)
            c.restore()
            if (e > 0.01f) {
                pGlow.shader = p.glow
                pGlow.alpha = (255 * e).toInt().coerceIn(0, 255)
                c.drawRoundRect(p.rect, 30f, 30f, pGlow)
            }
            labels.getOrNull(p.index)?.let { if (it.isNotEmpty()) c.drawText(it, p.rect.centerX(), p.rect.bottom - 26f, pLabel) }
            // LED de esquina
            pFill.color = if (e > 0.05f) palette.hot else Color.rgb(48, 46, 46)
            c.drawCircle(p.rect.right - 26f, p.rect.top + 26f, 7f, pFill)
            c.restore()
        }
    }
}
