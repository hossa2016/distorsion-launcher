package com.hossa.superfondo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** Vista en vivo del instrumento, para probarlo antes de aplicarlo. */
class PreviewView(context: Context, private val sounds: SoundBank) : View(context) {
    var scene: BaseScene = DrumScene()
        private set

    fun setInstrument(index: Int, palette: Int) {
        val info = Instruments.all[index]
        scene = info.factory()
        if (width > 0) scene.resize(width, height)
        scene.setPalette(palette)
        scene.onSound = { id, vol, delay, _ -> sounds.play(id, vol, delay) }
        sounds.ensure(info.sounds)
        scene.setLockedInstant(true)
        // pequeña pausa para que carguen los sonidos y se vea la animación de desbloqueo
        postDelayed({ scene.onUnlock(SystemClock.uptimeMillis()); invalidate() }, 450)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scene.resize(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        scene.draw(canvas, now)
        if (scene.isBusy(now)) postInvalidateOnAnimation() else postInvalidateDelayed(33)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> scene.touch(event.x, event.y, SystemClock.uptimeMillis())
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                scene.touch(event.getX(i), event.getY(i), SystemClock.uptimeMillis())
            }
        }
        invalidate()
        return true
    }
}
