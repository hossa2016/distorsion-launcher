package com.hossa.superfondo

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class RockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = RockEngine()

    inner class RockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scene = Scene()
        private var visible = false
        private var registered = false
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

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                registered = true
            } catch (e: Exception) {
                registered = false
            }
            scene.setLockedInstant(isLocked())
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            if (registered) {
                try {
                    unregisterReceiver(receiver)
                } catch (e: Exception) {
                }
            }
            super.onDestroy()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                scene.onVisible(isLocked(), SystemClock.uptimeMillis())
                schedule(0)
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
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

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                scene.touch(event.x, event.y, SystemClock.uptimeMillis())
                schedule(0)
            }
            super.onTouchEvent(event)
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
                // superficie no disponible: se reintenta en el siguiente cuadro
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                    }
                }
            }
            schedule(if (scene.isBusy(SystemClock.uptimeMillis())) 16L else 33L)
        }
    }
}
