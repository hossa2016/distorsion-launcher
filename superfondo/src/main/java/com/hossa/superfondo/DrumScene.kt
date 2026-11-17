package com.hossa.superfondo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Tambor o platillo del kit, en coordenadas base. */
private class Piece(val id: String, val x: Float, val y: Float, val r: Float, val cymbal: Boolean) {
    var hitAt = -100_000L
    var strength = 0f
    var rimShader: Shader? = null
    var headShader: Shader? = null
    var shadowShader: Shader? = null
    var sheenShader: Shader? = null

    fun env(now: Long): Float {
        if (now < hitAt) return 0f
        val decay = if (cymbal) 1300f else 450f
        return strength * exp(-(now - hitAt) / decay)
    }
}

private val DRUM_SLOT = mapOf("kick" to 0, "snare" to 1, "tom1" to 2, "tom2" to 3, "floor" to 4, "hihat" to 5, "crash" to 6, "ride" to 7)

class DrumScene : BaseScene() {
    private val pieces = listOf(
        Piece("tom1", 470f, 1150f, 150f, false),
        Piece("tom2", 760f, 1150f, 160f, false),
        Piece("snare", 430f, 1600f, 195f, false),
        Piece("floor", 920f, 1640f, 225f, false),
        Piece("hihat", 175f, 1330f, 175f, true),
        Piece("crash", 250f, 820f, 250f, true),
        Piece("ride", 1010f, 930f, 290f, true)
    )
    private val kick = Piece("kick", 610f, 2100f, 200f, false)
    private val allPieces = pieces + kick
    private val kickRect = RectF(415f, 1900f, 805f, 2300f)
    private var shockAt = -100_000L

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pShader = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 16f }
    private val pLug = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 195, 187) }
    private val pLugDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 58, 55) }
    private val pRingA = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(46, 42, 39) }
    private val pRingB = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(27, 25, 23) }
    private val pShell = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pHoop = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 54f
        letterSpacing = 0.26f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        color = Color.argb(215, 236, 230, 221)
    }
    private val tmpRect = RectF()

    init {
        for (p in allPieces) {
            p.rimShader = SweepGradient(
                p.x, p.y,
                intArrayOf(
                    Color.rgb(240, 236, 230), Color.rgb(111, 107, 103), Color.rgb(217, 212, 204),
                    Color.rgb(74, 71, 68), Color.rgb(189, 183, 174), Color.rgb(240, 236, 230)
                ), null
            )
            p.headShader = RadialGradient(
                p.x - p.r * 0.16f, p.y - p.r * 0.24f, p.r * 1.5f,
                intArrayOf(Color.rgb(42, 40, 39), Color.rgb(22, 21, 21), Color.rgb(12, 12, 12)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            p.shadowShader = RadialGradient(
                p.x + 8f, p.y + 22f, p.r * 1.25f,
                intArrayOf(Color.argb(200, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP
            )
        }
        pHoop.shader = LinearGradient(
            0f, 0f, 0f, 34f,
            intArrayOf(Color.rgb(240, 236, 230), Color.rgb(111, 107, 103), Color.rgb(217, 212, 204), Color.rgb(74, 71, 68)),
            null, Shader.TileMode.CLAMP
        )
        setPalette(0)
        resize(1080, 2400)
    }

    override fun onPaletteChanged() {
        val a = palette.accent
        for (p in pieces) if (p.cymbal) {
            p.sheenShader = SweepGradient(
                p.x, p.y,
                intArrayOf(
                    Color.TRANSPARENT, withAlpha(a, 150), Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.argb(45, 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.07f, 0.16f, 0.5f, 0.57f, 0.66f, 1f)
            )
        }
        pShell.shader = LinearGradient(
            kickRect.left, 0f, kickRect.right, 0f,
            intArrayOf(Color.rgb(5, 5, 5), Color.rgb(29, 27, 27), palette.shell, Color.rgb(29, 27, 27), Color.rgb(5, 5, 5)),
            null, Shader.TileMode.CLAMP
        )
    }

    private fun hit(id: String, strength: Float, at: Long) {
        val p = allPieces.firstOrNull { it.id == id } ?: return
        p.hitAt = at
        p.strength = strength
    }

    override fun performScreenOn(now: Long) {
        hit("hihat", 0.6f, now + 450)
    }

    override fun performUnlock(now: Long) {
        hit("tom1", 0.8f, now)
        hit("tom2", 0.8f, now + 80)
        hit("floor", 0.85f, now + 160)
        hit("snare", 0.9f, now + 240)
        hit("kick", 1f, now + 340)
        hit("crash", 1f, now + 340)
        shockAt = now + 340
        sound("s:2", 0.6f, 0L, false)
        sound("s:3", 0.6f, 80L, false)
        sound("s:4", 0.65f, 160L, false)
        sound("s:1", 0.7f, 240L, false)
        sound("s:0", 0.85f, 340L, false)
        sound("s:6", 0.6f, 340L, false)
    }

    override fun idle(now: Long) {
        val opts = listOf("hihat", "tom1", "tom2", "snare")
        hit(opts[rnd.nextInt(opts.size)], 0.35f, now)
    }

    override fun hitTest(bx: Float, by: Float, now: Long): Boolean {
        val target = pieces.sortedBy { if (it.cymbal) 0 else 1 }
            .firstOrNull { hypot(bx - it.x, by - it.y) <= it.r + 10f }
        when {
            target != null -> {
                hit(target.id, 1f, now); sound("s:" + (DRUM_SLOT[target.id] ?: 0), 1f, 0L, true)
            }
            kickRect.contains(bx, by) -> {
                hit("kick", 1f, now); sound("s:0", 1f, 0L, true)
            }
            else -> return false
        }
        return true
    }

    override fun contentBusy(now: Long): Boolean =
        now - shockAt < 1200 || allPieces.any { it.env(now) > 0.02f || now < it.hitAt }

    override fun drawContent(c: Canvas, now: Long) {
        drawKick(c, now)
        for (p in pieces) if (!p.cymbal) drawDrum(c, p, now)
        for (p in pieces) if (p.cymbal) drawCymbal(c, p, now)
        drawShock(c, now)
    }

    private fun drawKick(c: Canvas, now: Long) {
        val e = kick.env(now)
        c.save()
        val k = 1f + 0.025f * e
        c.scale(k, k, kickRect.centerX(), kickRect.centerY())
        pFill.color = Color.argb(170, 0, 0, 0)
        tmpRect.set(kickRect.left - 15f, kickRect.top + 30f, kickRect.right + 15f, kickRect.bottom + 50f)
        c.drawRoundRect(tmpRect, 40f, 40f, pFill)
        c.drawRoundRect(kickRect, 20f, 20f, pShell)
        if (e > 0.01f) {
            pFill.color = withAlpha(palette.accent, (90 * e).toInt())
            c.drawRoundRect(kickRect, 20f, 20f, pFill)
        }
        drawHoop(c, kickRect.top - 10f)
        drawHoop(c, kickRect.bottom - 20f)
        c.drawText("DISTORSIÓN", 610f, 2130f, pText)
        pFill.color = palette.accent
        c.drawRect(560f, 2160f, 660f, 2163f, pFill)
        c.restore()
    }

    private fun drawHoop(c: Canvas, top: Float) {
        c.save()
        c.translate(0f, top)
        tmpRect.set(kickRect.left - 15f, 0f, kickRect.right + 15f, 34f)
        c.drawRoundRect(tmpRect, 10f, 10f, pHoop)
        c.restore()
    }

    private fun drawDrum(c: Canvas, p: Piece, now: Long) {
        val e = p.env(now)
        c.save()
        val k = 1f + 0.03f * e
        c.scale(k, k, p.x, p.y)
        pShader.shader = p.shadowShader
        c.drawCircle(p.x, p.y, p.r * 1.25f, pShader)
        val n = if (p.r > 200f) 10 else 8
        for (i in 0 until n) {
            c.save()
            c.rotate(i * 360f / n, p.x, p.y)
            tmpRect.set(p.x + p.r + 2f, p.y - 9f, p.x + p.r + 30f, p.y + 9f)
            c.drawRoundRect(tmpRect, 6f, 6f, pLug)
            tmpRect.set(p.x + p.r + 18f, p.y - 9f, p.x + p.r + 30f, p.y + 9f)
            c.drawRoundRect(tmpRect, 5f, 5f, pLugDark)
            c.restore()
        }
        pRim.shader = p.rimShader
        c.drawCircle(p.x, p.y, p.r + 6f, pRim)
        pShader.shader = p.headShader
        c.drawCircle(p.x, p.y, p.r - 2f, pShader)
        pFill.color = Color.argb(110, 8, 8, 8)
        c.drawCircle(p.x, p.y, p.r * 0.32f, pFill)
        if (e > 0.01f) {
            pFill.color = withAlpha(palette.accent, (95 * e).toInt())
            c.drawCircle(p.x, p.y, p.r - 2f, pFill)
        }
        if (now >= p.hitAt) {
            val t = (now - p.hitAt) / 750f
            for (kk in 0 until 3) {
                val pr = t - kk * 0.16f
                if (pr in 0f..1f) {
                    val rr = p.r * (0.12f + 0.85f * pr)
                    val a = (1f - pr) * p.strength
                    pStroke.color = withAlpha(palette.hot, (50 * a).toInt())
                    pStroke.strokeWidth = 16f
                    c.drawCircle(p.x, p.y, rr, pStroke)
                    pStroke.color = withAlpha(palette.hot, (230 * a).toInt())
                    pStroke.strokeWidth = 5f
                    c.drawCircle(p.x, p.y, rr, pStroke)
                }
            }
        }
        c.restore()
    }

    private fun drawCymbal(c: Canvas, p: Piece, now: Long) {
        val e = p.env(now)
        val t = (now - p.hitAt) / 1000f
        val wobble = if (e > 0.01f) sin(t * 15f) * 7f * e else 0f
        c.save()
        c.rotate(wobble, p.x, p.y)
        val k = 1f + 0.02f * e * sin(t * 22f)
        c.scale(k, k, p.x, p.y)
        pShader.shader = p.shadowShader
        c.drawCircle(p.x, p.y, p.r * 1.2f, pShader)
        pFill.color = Color.rgb(40, 36, 32)
        c.drawCircle(p.x, p.y, p.r, pFill)
        var rr = p.r * 0.2f
        var odd = false
        while (rr < p.r) {
            c.drawCircle(p.x, p.y, rr, if (odd) pRingA else pRingB)
            odd = !odd
            rr += 6f
        }
        c.save()
        c.rotate(-150f + wobble * 3f, p.x, p.y)
        pShader.shader = p.sheenShader
        pShader.alpha = (150 + 105 * e).toInt().coerceIn(0, 255)
        c.drawCircle(p.x, p.y, p.r, pShader)
        pShader.alpha = 255
        c.restore()
        pFill.color = Color.rgb(84, 75, 66)
        c.drawCircle(p.x, p.y, p.r * 0.2f, pFill)
        pFill.color = Color.rgb(10, 10, 10)
        c.drawCircle(p.x, p.y, 12f, pFill)
        pFill.color = Color.rgb(200, 195, 187)
        c.drawCircle(p.x, p.y, 5f, pFill)
        if (e > 0.01f) {
            pStroke.color = withAlpha(palette.ring, (60 * e).toInt())
            pStroke.strokeWidth = 18f
            c.drawCircle(p.x, p.y, p.r, pStroke)
            pStroke.color = withAlpha(palette.ring, (240 * e).toInt())
            pStroke.strokeWidth = 5f
            c.drawCircle(p.x, p.y, p.r, pStroke)
        }
        c.restore()
    }

    private fun drawShock(c: Canvas, now: Long) {
        if (now < shockAt) return
        val t = (now - shockAt) / 1100f
        if (t > 1f) return
        val ease = 1f - (1f - t).pow(3)
        for (kk in 0 until 4) {
            val rr = (300f + kk * 260f) * ease
            val a = (0.55f - kk * 0.12f) * (1f - t)
            pStroke.color = withAlpha(palette.hot, (60 * a).toInt())
            pStroke.strokeWidth = 28f - kk * 4f
            c.drawCircle(610f, 1500f, rr, pStroke)
            pStroke.color = withAlpha(palette.hot, (255 * a).toInt())
            pStroke.strokeWidth = 9f - kk * 2f
            c.drawCircle(610f, 1500f, rr, pStroke)
        }
    }
}
