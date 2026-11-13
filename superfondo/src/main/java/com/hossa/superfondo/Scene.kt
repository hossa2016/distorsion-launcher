package com.hossa.superfondo

import android.graphics.Bitmap
import android.graphics.BitmapShader
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Animación simple de un valor. */
private class Tween(var value: Float) {
    private var from = value
    private var to = value
    private var start = 0L
    private var dur = 0L

    fun animate(target: Float, durationMs: Long, now: Long, fromValue: Float = get(now)) {
        from = fromValue; to = target; start = now; dur = durationMs
    }

    fun snap(v: Float) {
        value = v; from = v; to = v; dur = 0
    }

    fun get(now: Long): Float {
        if (dur <= 0) { value = to; return value }
        if (now < start) { value = from; return value }
        val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
        val e = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
        value = from + (to - from) * e
        return value
    }

    fun running(now: Long) = dur > 0 && now - start < dur
}

/** Tambor o platillo del kit. Coordenadas en el lienzo base de 1220 de ancho. */
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

private class Ember {
    var x = 0f; var y = 0f; var vy = 0f; var vx = 0f; var r = 0f; var phase = 0f; var hot = false
}

class Scene {
    private var w = 1080f
    private var h = 2400f
    private var u = 1f

    var xOffset = 0.5f
    var zoom = 0f

    private var palette = Palettes.all[0]
    private var paletteIndex = -1

    private var locked = true
    private val scale = Tween(0.86f)
    private val bright = Tween(1f)
    private val energy = Tween(0f)
    private var shockAt = -100_000L
    private var lastUnlock = -10_000L
    private var nextIdle = 0L
    private var lastFrame = 0L
    private val rnd = Random(11)

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
    private val embers = ArrayList<Ember>()

    private val pBg = Paint()
    private val pSpot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pVignette = Paint()
    private val pGrain = Paint()
    private val pDim = Paint().apply { color = Color.BLACK }
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
    private val pEmber = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpRect = RectF()

    init {
        buildBaseShaders()
        setPalette(0)
        resize(1080, 2400)
    }

    // ---------- configuración ----------

    fun setPalette(index: Int) {
        val i = index.coerceIn(0, Palettes.all.size - 1)
        if (i == paletteIndex) return
        paletteIndex = i
        palette = Palettes.all[i]
        val a = palette.accent
        for (p in pieces) {
            if (p.cymbal) {
                p.sheenShader = SweepGradient(
                    p.x, p.y,
                    intArrayOf(
                        Color.TRANSPARENT, withAlpha(a, 150), Color.TRANSPARENT, Color.TRANSPARENT,
                        Color.argb(45, 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.07f, 0.16f, 0.5f, 0.57f, 0.66f, 1f)
                )
            }
        }
        pShell.shader = LinearGradient(
            kickRect.left, 0f, kickRect.right, 0f,
            intArrayOf(Color.rgb(5, 5, 5), Color.rgb(29, 27, 27), palette.shell, Color.rgb(29, 27, 27), Color.rgb(5, 5, 5)),
            null, Shader.TileMode.CLAMP
        )
        pSpot.shader = RadialGradient(
            610f, 1500f, 900f,
            intArrayOf(withAlpha(a, 255), withAlpha(a, 70), withAlpha(a, 0)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        buildScreenShaders()
    }

    private fun buildBaseShaders() {
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
    }

    fun resize(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        u = w / 1220f
        buildScreenShaders()
        embers.clear()
        repeat(55) { embers.add(newEmber(true)) }
    }

    private fun buildScreenShaders() {
        pBg.shader = RadialGradient(
            w * 0.5f, h * 0.55f, max(w, h) * 0.8f,
            intArrayOf(palette.bg, Color.rgb(10, 8, 8), Color.rgb(3, 3, 3)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        pVignette.shader = RadialGradient(
            w * 0.5f, h * 0.5f, max(w, h) * 0.75f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(205, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        if (pGrain.shader == null) {
            val size = 160
            val px = IntArray(size * size) { val v = rnd.nextInt(256); Color.rgb(v, v, v) }
            pGrain.shader = BitmapShader(
                Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888),
                Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
            )
            pGrain.alpha = 12
        }
    }

    private fun newEmber(anywhere: Boolean) = Ember().apply {
        x = w * rnd.nextFloat()
        y = if (anywhere) rnd.nextFloat() * h else h + rnd.nextFloat() * h * 0.1f
        vy = -(h * 0.01f + rnd.nextFloat() * h * 0.025f)
        vx = (rnd.nextFloat() - 0.5f) * w * 0.02f
        r = w * (0.0016f + rnd.nextFloat() * 0.0035f)
        phase = rnd.nextFloat() * 6.28f
        hot = rnd.nextFloat() < 0.3f
    }

    // ---------- eventos ----------

    fun setLockedInstant(isLocked: Boolean) {
        locked = isLocked
        scale.snap(if (isLocked) 0.86f else 1.08f)
        bright.snap(if (isLocked) 0.72f else 0.8f)
    }

    fun onVisible(isLocked: Boolean, now: Long) {
        if (isLocked != locked) {
            if (isLocked) setLockedInstant(true) else onUnlock(now)
        }
        lastFrame = now
    }

    fun onScreenOff() {
        locked = true
        scale.snap(0.8f)
        bright.snap(0f)
        energy.snap(0f)
    }

    fun onScreenOn(isLocked: Boolean, now: Long) {
        if (!isLocked) { onUnlock(now); return }
        locked = true
        bright.animate(0.72f, 1500, now, 0f)
        scale.animate(0.86f, 1800, now, 0.8f)
        energy.animate(0f, 1800, now, 0.35f)
        hit("hihat", 0.6f, now + 450)
    }

    fun onUnlock(now: Long) {
        if (!locked && now - lastUnlock < 2000) return
        lastUnlock = now
        locked = false
        scale.animate(1.08f, 1000, now + 200)
        bright.animate(0.8f, 600, now)
        hit("tom1", 0.8f, now)
        hit("tom2", 0.8f, now + 80)
        hit("floor", 0.85f, now + 160)
        hit("snare", 0.9f, now + 240)
        hit("kick", 1f, now + 340)
        hit("crash", 1f, now + 340)
        shockAt = now + 340
        energy.animate(0f, 2600, now + 340, 1f)
        nextIdle = now + 7000
    }

    fun touch(x: Float, y: Float, now: Long) {
        if (locked) return
        val s = currentScale(now) * u
        val bx = (x - (w / 2 + parallax())) / s + 610f
        val by = (y - h * 0.553f) / s + 1500f
        // los platillos están encima de los tambores: se prueban primero
        val target = pieces.sortedBy { if (it.cymbal) 0 else 1 }
            .firstOrNull { hypot(bx - it.x, by - it.y) <= it.r + 10f }
        when {
            target != null -> hit(target.id, 1f, now)
            kickRect.contains(bx, by) -> hit("kick", 1f, now)
            else -> return
        }
        energy.animate(0f, 1400, now, min(1f, energy.get(now) + 0.45f))
    }

    private fun hit(id: String, strength: Float, at: Long) {
        val p = allPieces.firstOrNull { it.id == id } ?: return
        p.hitAt = at
        p.strength = strength
    }

    private fun parallax() = (0.5f - xOffset) * w * 0.07f
    private fun currentScale(now: Long) = scale.get(now) * (1f + zoom * 0.06f)

    fun isBusy(now: Long): Boolean =
        scale.running(now) || bright.running(now) || energy.get(now) > 0.02f ||
            now - shockAt < 1200 || allPieces.any { it.env(now) > 0.02f || now < it.hitAt }

    // ---------- dibujo ----------

    fun draw(c: Canvas, now: Long) {
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now

        if (!locked && now > nextIdle) {
            val opts = listOf("hihat", "tom1", "tom2", "snare")
            hit(opts[rnd.nextInt(opts.size)], 0.35f, now)
            nextIdle = now + 6000 + rnd.nextInt(5000)
        }

        val en = energy.get(now)
        c.drawRect(0f, 0f, w, h, pBg)

        c.save()
        val s = currentScale(now) * u
        c.translate(w / 2 + parallax(), h * 0.553f)
        c.scale(s, s)
        c.translate(-610f, -1500f)

        pSpot.alpha = (60 + 140 * en).toInt().coerceIn(0, 255)
        c.drawCircle(610f, 1500f, 900f, pSpot)

        drawKick(c, now)
        for (p in pieces) if (!p.cymbal) drawDrum(c, p, now)
        for (p in pieces) if (p.cymbal) drawCymbal(c, p, now)
        drawShock(c, now)
        c.restore()

        drawEmbers(c, dt, en, now)
        c.drawRect(0f, 0f, w, h, pVignette)
        c.drawRect(0f, 0f, w, h, pGrain)

        val dim = 1f - bright.get(now) + zoom * 0.35f
        if (dim > 0.005f) {
            pDim.alpha = (dim.coerceIn(0f, 1f) * 255).toInt()
            c.drawRect(0f, 0f, w, h, pDim)
        }
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

    private fun drawEmbers(c: Canvas, dt: Float, en: Float, now: Long) {
        val speed = (if (locked) 0.5f else 1f) + en * 2.4f
        val tt = now / 1000f
        for (i in embers.indices) {
            val m = embers[i]
            m.y += m.vy * dt * speed
            m.x += (m.vx + sin(tt * 0.8f + m.phase) * w * 0.01f) * dt
            if (m.y < -h * 0.05f) { embers[i] = newEmber(false); continue }
            val life = (m.y / h).coerceIn(0f, 1f)
            val flicker = 0.6f + 0.4f * sin(tt * 3f + m.phase * 5f)
            val a = (255 * life * flicker * (0.5f + 0.5f * en)).toInt().coerceIn(0, 255)
            val col = if (m.hot) palette.ember2 else palette.accent
            pEmber.color = withAlpha(col, a / 4)
            c.drawCircle(m.x, m.y, m.r * 3.2f, pEmber)
            pEmber.color = withAlpha(col, a)
            c.drawCircle(m.x, m.y, m.r, pEmber)
        }
    }

    private fun withAlpha(color: Int, a: Int) =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
