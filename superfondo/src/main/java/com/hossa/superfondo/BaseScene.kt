package com.hossa.superfondo

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Animación simple de un valor. */
class Tween(var value: Float) {
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

class Ember {
    var x = 0f; var y = 0f; var vy = 0f; var vx = 0f; var r = 0f; var phase = 0f; var hot = false
}

/**
 * Base de todos los instrumentos: fondo, brasas, viñeta, grano, cámara (zoom y paralaje)
 * y el flujo bloqueo → desbloqueo. Cada instrumento dibuja su contenido en un lienzo base
 * de 1220 unidades de ancho, centrado en (610, pivotY).
 */
abstract class BaseScene {
    protected var w = 1080f
    protected var h = 2400f
    protected var u = 1f

    var xOffset = 0.5f
    var zoom = 0f

    /** id del sonido, volumen, retraso en ms, viene de un toque */
    var onSound: ((String, Float, Long, Boolean) -> Unit)? = null

    protected var palette = Palettes.all[0]
    private var paletteIndex = -1

    protected var locked = true
    protected val scale = Tween(0.86f)
    protected val bright = Tween(1f)
    protected val energy = Tween(0f)
    private var lastUnlock = -10_000L
    protected var nextIdle = 0L
    private var lastFrame = 0L
    protected val rnd = Random(11)

    protected open val pivotY = 1500f
    protected open val lockScale = 0.86f
    protected open val homeScale = 1.08f
    protected open val homeBright = 0.8f
    protected open val lockBright = 0.72f
    protected open val emberCount = 55

    private val embers = ArrayList<Ember>()
    private val pBg = Paint()
    private val pVignette = Paint()
    private val pGrain = Paint()
    private val pDim = Paint().apply { color = Color.BLACK }
    private val pEmber = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val pSpot = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------- lo que implementa cada instrumento ----------
    protected abstract fun onPaletteChanged()
    protected abstract fun drawContent(c: Canvas, now: Long)
    protected abstract fun performUnlock(now: Long)
    protected open fun performScreenOn(now: Long) {}
    protected open fun idle(now: Long) {}
    /** Toque en coordenadas base. Devuelve true si golpeó algo. */
    protected abstract fun hitTest(bx: Float, by: Float, now: Long): Boolean
    protected abstract fun contentBusy(now: Long): Boolean
    /** Modo de juego (acordes, escalas…). */
    open fun applySettings(s: SceneSettings) {}

    // ---------- configuración ----------
    fun setPalette(index: Int) {
        val i = index.coerceIn(0, Palettes.all.size - 1)
        if (i == paletteIndex) return
        paletteIndex = i
        palette = Palettes.all[i]
        val a = palette.accent
        pSpot.shader = RadialGradient(
            610f, pivotY, 900f,
            intArrayOf(withAlpha(a, 255), withAlpha(a, 70), withAlpha(a, 0)),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        buildScreenShaders()
        onPaletteChanged()
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        w = width.toFloat(); h = height.toFloat()
        u = w / 1220f
        buildScreenShaders()
        embers.clear()
        repeat(emberCount) { embers.add(newEmber(true)) }
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
        scale.snap(if (isLocked) lockScale else homeScale)
        bright.snap(if (isLocked) lockBright else homeBright)
    }

    fun onVisible(isLocked: Boolean, now: Long) {
        if (isLocked != locked) {
            if (isLocked) setLockedInstant(true) else onUnlock(now)
        }
        lastFrame = now
    }

    fun onScreenOff() {
        locked = true
        scale.snap(lockScale * 0.93f)
        bright.snap(0f)
        energy.snap(0f)
    }

    fun onScreenOn(isLocked: Boolean, now: Long) {
        if (!isLocked) { onUnlock(now); return }
        locked = true
        bright.animate(lockBright, 1500, now, 0f)
        scale.animate(lockScale, 1800, now, lockScale * 0.93f)
        energy.animate(0f, 1800, now, 0.35f)
        performScreenOn(now)
    }

    fun onUnlock(now: Long) {
        if (!locked && now - lastUnlock < 2000) return
        lastUnlock = now
        locked = false
        scale.animate(homeScale, 1000, now + 200)
        bright.animate(homeBright, 600, now)
        energy.animate(0f, 2600, now + 300, 1f)
        nextIdle = now + 7000
        performUnlock(now)
    }

    /** Toque en coordenadas de pantalla. */
    fun touch(x: Float, y: Float, now: Long) {
        if (locked) return
        val s = currentScale(now) * u
        val bx = (x - (w / 2 + parallax())) / s + 610f
        val by = (y - h * 0.553f) / s + pivotY
        if (hitTest(bx, by, now)) {
            energy.animate(0f, 1400, now, min(1f, energy.get(now) + 0.45f))
        }
    }

    protected fun sound(id: String, vol: Float, delayMs: Long, fromTouch: Boolean) {
        onSound?.invoke(id, vol, delayMs, fromTouch)
    }

    private fun parallax() = (0.5f - xOffset) * w * 0.07f
    private fun currentScale(now: Long) = scale.get(now) * (1f + zoom * 0.06f)

    fun isBusy(now: Long): Boolean =
        scale.running(now) || bright.running(now) || energy.get(now) > 0.02f || contentBusy(now)

    // ---------- dibujo ----------
    fun draw(c: Canvas, now: Long) {
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.1f)
        lastFrame = now

        if (!locked && now > nextIdle) {
            idle(now)
            nextIdle = now + 6000 + rnd.nextInt(5000)
        }

        val en = energy.get(now)
        c.drawRect(0f, 0f, w, h, pBg)

        c.save()
        val s = currentScale(now) * u
        c.translate(w / 2 + parallax(), h * 0.553f)
        c.scale(s, s)
        c.translate(-610f, -pivotY)
        pSpot.alpha = (60 + 140 * en).toInt().coerceIn(0, 255)
        c.drawCircle(610f, pivotY, 900f, pSpot)
        drawContent(c, now)
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

    protected fun withAlpha(color: Int, a: Int) =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    protected fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        )
    }
}
