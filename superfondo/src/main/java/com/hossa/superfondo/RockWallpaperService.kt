package com.hossa.superfondo

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class RockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = RockEngine()

    inner class RockEngine : Engine() {
        private val ctx: Context get() = this@RockWallpaperService
        private val handler = Handler(Looper.getMainLooper())
        private var instrumentIndex = -1
        private var scene: BaseScene = DrumScene()
        private var sounds: SoundBank? = null
        private var visible = false
        private var registered = false
        private var surfW = 0
        private var surfH = 0
        private var lastTouchAt = 0L
        private val drawRunnable = Runnable { drawFrame() }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val now = SystemClock.uptimeMillis()
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> scene.onScreenOff()
                    Intent.ACTION_SCREEN_ON -> scene.onScreenOn(isLocked(), now)
                    Intent.ACTION_USER_PRESENT -> scene.onUnlock(now)
                }
                schedule(0)
            }
        }

        private fun isLocked(): Boolean = try {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        } catch (e: Exception) {
            false
        }

        /** Crea el instrumento elegido si cambió. */
        private fun syncSettings() {
            val idx = Instruments.selectedIndex(ctx)
            if (idx != instrumentIndex) {
                instrumentIndex = idx
                val info = Instruments.all[idx]
                scene = info.factory()
                if (surfW > 0) scene.resize(surfW, surfH)
                scene.setLockedInstant(isLocked())
                scene.onSound = { id, vol, delay, fromTouch -> playSound(id, vol, delay, fromTouch) }
                try { sounds?.ensure(info.sounds) } catch (e: Exception) { }
            }
            scene.setPalette(Palettes.selectedIndex(ctx))
        }

        private fun playSound(id: String, vol: Float, delay: Long, fromTouch: Boolean) {
            if (fromTouch) {
                if (!Palettes.soundTouch(ctx)) return
            } else {
                if (!Palettes.soundUnlock(ctx)) return
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            }
            sounds?.play(id, vol, delay)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            try { sounds = SoundBank(ctx) } catch (e: Exception) { sounds = null }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else registerReceiver(receiver, filter)
                registered = true
            } catch (e: Exception) {
                registered = false
            }
            syncSettings()
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            if (registered) try { unregisterReceiver(receiver) } catch (e: Exception) { }
            sounds?.release()
            sounds = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                syncSettings()
                scene.onVisible(isLocked(), SystemClock.uptimeMillis())
                schedule(0)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfW = width; surfH = height
            scene.resize(width, height)
            schedule(0)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            scene.xOffset = xOffset
            schedule(0)
        }

        override fun onZoomChanged(zoom: Float) {
            scene.zoom = zoom
            schedule(0)
        }

        private fun doTouch(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            lastTouchAt = now
            scene.touch(x, y, now)
            schedule(0)
        }

        override fun onTouchEvent(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> doTouch(event.x, event.y)
                MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = event.actionIndex
                    doTouch(event.getX(i), event.getY(i))
                }
            }
            super.onTouchEvent(event)
        }

        // Muchas pantallas de inicio no envían el toque completo, solo un "tap"
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP) {
                if (SystemClock.uptimeMillis() - lastTouchAt > 350) doTouch(x.toFloat(), y.toFloat())
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun schedule(delayMs: Long) {
            handler.removeCallbacks(drawRunnable)
            if (visible) handler.postDelayed(drawRunnable, delayMs)
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
                if (canvas != null) scene.draw(canvas, SystemClock.uptimeMillis())
            } catch (e: Exception) {
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
            }
            schedule(if (scene.isBusy(SystemClock.uptimeMillis())) 16L else 33L)
        }
    }
}
