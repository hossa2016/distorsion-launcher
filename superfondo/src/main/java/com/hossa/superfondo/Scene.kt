package com.hossa.superfondo

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Animación simple de un valor entre dos puntos en el tiempo. */
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
        if (dur <= 0) return to.also { value = it }
        val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
        // ease in-out cúbico
        val e = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
        value = from + (to - from) * e
        return value
    }

    fun running(now: Long) = dur > 0 && now - start < dur
}

private class GuitarString(val baseX: Float, val thickness: Float) {
    var pluckAt = -100_000L
    var amp = 0f
    var centerY = 0f
    var freq = 6f

    fun envelope(now: Long): Float {
        if (now < pluckAt) return 0f
        return amp * exp(-(now - pluckAt) / 1500f)
    }
}

private class Ember {
    var x = 0f; var y = 0f; var vy = 0f; var vx = 0f; var r = 0f; var phase = 0f; var hot = false
}

class Scene {
    private var w = 1080f
    private var h = 2400f

    var xOffset = 0.5f
    var zoom = 0f

    private var locked = true
    private val scale = Tween(1f)
    private val bright = Tween(1f)
    private val energy = Tween(0f)

    private var strings: List<GuitarString> = emptyList()
    private val frets = ArrayList<Float>()
    private val embers = ArrayList<Ember>()
    private var lastFrame = 0L
    private var nextIdlePluck = 0L
    private val rnd = Random(7)

    // pinceles
    private val pBg = Paint()
    private val pBoard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 11, 11) }
    private val pEdge = Paint().apply { color = Color.rgb(42, 37, 36) }
    private val pFret = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFretShadow = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val pInlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(26, 217, 210, 198) }
    private val pString = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pStrShadow = Paint().apply { color = Color.argb(130, 0, 0, 0) }
    private val pAura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pEmber = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pVignette = Paint()
    private val pGrain = Paint()
    private val pDim = Paint().apply { color = Color.BLACK }
    private val path = Path()
    private val stringShaders = ArrayList<Shader>()

    private val crimson = Color.rgb(225, 37, 47)
    private val metal = Color.rgb(205, 198, 188)

    init {
        resize(1080, 2400)
    }

    fun resize(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        val u = w / 1220f

        pBg.shader = RadialGradient(
            w * 0.5f, h * 0.62f, max(w, h) * 0.75f,
            intArrayOf(Color.rgb(27, 17, 18), Color.rgb(11, 9, 9), Color.rgb(4, 4, 4)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        pFret.shader = LinearGradient(
            0f, 0f, 0f, 9f * u,
            intArrayOf(Color.rgb(110, 106, 101), Color.rgb(200, 195, 186), Color.rgb(70, 68, 66)),
            null, Shader.TileMode.CLAMP
        )
        pFret.alpha = 85
        pAura.shader = RadialGradient(
            w * 0.5f, h * 0.68f, w * 0.75f,
            intArrayOf(Color.argb(255, 225, 37, 47), Color.argb(90, 225, 37, 47), Color.argb(0, 225, 37, 47)),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        pVignette.shader = RadialGradient(
            w * 0.5f, h * 0.5f, max(w, h) * 0.72f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(200, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        pGrain.shader = BitmapShader(noiseBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        pGrain.alpha = 14

        val xs = floatArrayOf(300f, 424f, 548f, 672f, 796f, 920f)
        val th = floatArrayOf(7f, 6f, 5f, 4f, 3f, 2.4f)
        strings = xs.indices.map { i ->
            GuitarString(xs[i] * u, th[i] * u).also { it.freq = 4.2f + i * 0.9f }
        }
        stringShaders.clear()
        strings.forEach { s ->
            stringShaders.add(
                LinearGradient(
                    s.baseX - s.thickness / 2, 0f, s.baseX + s.thickness / 2, 0f,
                    intArrayOf(Color.rgb(93, 90, 87), Color.rgb(233, 228, 220), Color.rgb(163, 157, 149), Color.rgb(62, 60, 58)),
                    floatArrayOf(0f, 0.45f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
            )
        }

        frets.clear()
        var y = -0.11f * h
        for (k in 0 until 16) {
            y += 0.1217f * h * 0.955f.pow(k)
            frets.add(y)
        }

        embers.clear()
        repeat(46) { embers.add(newEmber(true)) }
    }

    private fun noiseBitmap(): Bitmap {
        val size = 160
        val px = IntArray(size * size) {
            val v = rnd.nextInt(256)
            Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun newEmber(anywhere: Boolean): Ember = Ember().apply {
        x = w * (0.15f + rnd.nextFloat() * 0.7f)
        y = if (anywhere) rnd.nextFloat() * h else h + rnd.nextFloat() * h * 0.1f
        vy = -(h * 0.012f + rnd.nextFloat() * h * 0.03f)
        vx = (rnd.nextFloat() - 0.5f) * w * 0.02f
        r = w * (0.0015f + rnd.nextFloat() * 0.003f)
        phase = rnd.nextFloat() * 6.28f
        hot = rnd.nextFloat() < 0.3f
    }

    // ---------- eventos ----------

    fun setLockedInstant(isLocked: Boolean) {
        locked = isLocked
        scale.snap(if (isLocked) 1f else 1.1f)
        bright.snap(if (isLocked) 0.78f else 1f)
    }

    fun onVisible(isLocked: Boolean, now: Long) {
        if (isLocked != locked) {
            if (isLocked) setLockedInstant(true) else onUnlock(now)
        }
        lastFrame = now
    }

    fun onScreenOff() {
        locked = true
        scale.snap(0.94f)
        bright.snap(0f)
        energy.snap(0f)
    }

    fun onScreenOn(isLocked: Boolean, now: Long) {
        if (!isLocked) {
            onUnlock(now); return
        }
        locked = true
        bright.animate(0.78f, 1600, now, 0f)
        scale.animate(1f, 1800, now, 0.94f)
        energy.animate(0f, 2200, now, 0.45f)
        pluck(2, 9f, h * 0.68f, now + 350)
    }

    private var lastUnlock = -10_000L

    fun onUnlock(now: Long) {
        if (!locked && now - lastUnlock < 2000) return
        lastUnlock = now
        locked = false
        scale.animate(1.1f, 950, now)
        bright.animate(1f, 600, now)
        energy.animate(0f, 2800, now, 1f)
        for (i in strings.indices) {
            pluck(i, 30f - i * 2.6f, h * 0.64f, now + i * 45L)
        }
        nextIdlePluck = now + 6000
    }

    fun touch(x: Float, y: Float, now: Long) {
        if (locked || strings.isEmpty()) return
        val s = scale.get(now) * (1f + zoom * 0.06f)
        val sx = (x - w / 2 - parallax()) / s + w / 2
        val sy = (y - h / 2) / s + h / 2
        var best = 0
        var bd = Float.MAX_VALUE
        strings.forEachIndexed { i, st ->
            val d = abs(st.baseX - sx)
            if (d < bd) { bd = d; best = i }
        }
        pluck(best, 24f, sy, now)
        energy.animate(0f, 1600, now, min(1f, energy.get(now) + 0.4f))
    }

    private fun pluck(i: Int, ampDp: Float, cy: Float, at: Long) {
        val st = strings.getOrNull(i) ?: return
        st.amp = ampDp * (w / 1220f)
        st.centerY = cy.coerceIn(h * 0.25f, h * 0.8f)
        st.pluckAt = at
    }

    private fun parallax(): Float = (0.5f - xOffset) * w * 0.07f

    fun isBusy(now: Long): Boolean =
        scale.running(now) || bright.running(now) || energy.get(now) > 0.02f ||
            strings.any { it.envelope(now) > 0.4f || now < it.pluckAt }

    // ---------- dibujo ----------

    fun draw(c: Canvas, now: Long) {
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now

        if (!locked && now > nextIdlePluck) {
            pluck(rnd.nextInt(strings.size), 5f + rnd.nextFloat() * 5f, h * (0.4f + rnd.nextFloat() * 0.3f), now)
            nextIdlePluck = now + 5000 + rnd.nextInt(5000)
        }

        val en = energy.get(now)
        val s = scale.get(now) * (1f + zoom * 0.06f)

        c.drawRect(0f, 0f, w, h, pBg)

        c.save()
        c.translate(w / 2 + parallax(), h / 2)
        c.scale(s, s)
        c.translate(-w / 2, -h / 2)

        drawBoard(c)

        // aura roja
        pAura.alpha = (40 + 120 * en).toInt().coerceIn(0, 255)
        c.drawRect(-w, 0f, 2 * w, h * 1.2f, pAura)

        strings.forEachIndexed { i, st -> drawString(c, st, stringShaders[i], now) }
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

    private fun drawBoard(c: Canvas) {
        val u = w / 1220f
        val left = 225f * u
        val right = 995f * u
        val top = -h * 0.2f
        val bottom = h * 1.2f
        c.drawRect(left, top, right, bottom, pBoard)
        c.drawRect(left, top, left + 2 * u, bottom, pEdge)
        c.drawRect(right - 2 * u, top, right, bottom, pEdge)
        for (fy in frets) {
            c.save()
            c.translate(0f, fy)
            c.drawRect(left + 3 * u, 0f, right - 3 * u, 9 * u, pFret)
            c.restore()
            c.drawRect(left + 3 * u, fy + 9 * u, right - 3 * u, fy + 15 * u, pFretShadow)
        }
        val cx = w / 2
        for (k in intArrayOf(2, 4, 6, 8)) {
            if (k + 1 < frets.size) c.drawCircle(cx, (frets[k] + frets[k + 1]) / 2, 20 * u, pInlay)
        }
        if (frets.size > 12) {
            val cy = (frets[11] + frets[12]) / 2
            c.drawCircle(486 * u, cy, 18 * u, pInlay)
            c.drawCircle(734 * u, cy, 18 * u, pInlay)
        }
    }

    private fun drawString(c: Canvas, st: GuitarString, shader: Shader, now: Long) {
        val top = -h * 0.2f
        val bottom = h * 1.2f
        val e = st.envelope(now)
        // sombra
        c.drawRect(st.baseX + st.thickness / 2, top, st.baseX + st.thickness / 2 + 3f, bottom, pStrShadow)

        if (e < 0.4f) {
            pString.shader = shader
            pString.color = Color.WHITE
            pString.strokeWidth = st.thickness
            c.drawLine(st.baseX, top, st.baseX, bottom, pString)
            return
        }

        val len = h * 0.46f
        val y0 = st.centerY - len / 2
        val y1 = st.centerY + len / 2
        val t = (now - st.pluckAt) / 1000f
        path.reset()
        path.moveTo(st.baseX, top)
        path.lineTo(st.baseX, y0)
        var y = y0
        val step = 6f
        while (y <= y1) {
            val uu = (y - y0) / len
            val env = sin(PI * uu).toFloat()
            val wave = sin(2 * PI * (7.0 * uu - st.freq * t)).toFloat()
            path.lineTo(st.baseX + e * env * wave, y)
            y += step
        }
        path.lineTo(st.baseX, y1)
        path.lineTo(st.baseX, bottom)

        val heat = min(1f, e / (14f * (w / 1220f)))
        // brillo
        pGlow.color = crimson
        pGlow.strokeWidth = st.thickness + 22f * (w / 1220f)
        pGlow.alpha = (35 * heat).toInt()
        c.drawPath(path, pGlow)
        pGlow.strokeWidth = st.thickness + 9f * (w / 1220f)
        pGlow.alpha = (80 * heat).toInt()
        c.drawPath(path, pGlow)
        // núcleo
        pString.shader = null
        pString.color = blend(metal, Color.rgb(255, 60, 70), heat)
        pString.strokeWidth = st.thickness
        c.drawPath(path, pString)
    }

    private fun drawEmbers(c: Canvas, dt: Float, en: Float, now: Long) {
        val speed = (if (locked) 0.55f else 1f) + en * 2.2f
        val tt = now / 1000f
        for (i in embers.indices) {
            val m = embers[i]
            m.y += m.vy * dt * speed
            m.x += (m.vx + sin(tt * 0.8f + m.phase) * w * 0.01f) * dt
            if (m.y < -h * 0.05f) {
                embers[i] = newEmber(false); continue
            }
            val life = (m.y / h).coerceIn(0f, 1f)
            val flicker = 0.6f + 0.4f * sin(tt * 3f + m.phase * 5f)
            val a = (255 * life * flicker * (0.55f + 0.45f * en)).toInt().coerceIn(0, 255)
            pEmber.color = if (m.hot) Color.rgb(255, 140, 70) else crimson
            pEmber.alpha = a / 4
            c.drawCircle(m.x, m.y, m.r * 3.2f, pEmber)
            pEmber.alpha = a
            c.drawCircle(m.x, m.y, m.r, pEmber)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        )
    }
}
