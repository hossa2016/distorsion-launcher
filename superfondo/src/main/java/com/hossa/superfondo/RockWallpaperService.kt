package com.hossa.superfondo

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
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
        private var soundPool: SoundPool? = null
        private val soundIds = HashMap<String, Int>()

        private fun setupSounds() {
            val pool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            val res = mapOf(
                "kick" to R.raw.drum_kick, "snare" to R.raw.drum_snare, "tom1" to R.raw.drum_tom1,
                "tom2" to R.raw.drum_tom2, "floor" to R.raw.drum_floor, "hihat" to R.raw.drum_hihat,
                "crash" to R.raw.drum_crash, "ride" to R.raw.drum_ride
            )
            for ((k, r) in res) soundIds[k] = pool.load(this@RockWallpaperService, r, 1)
            soundPool = pool
            scene.onSound = { id, vol, delay, fromTouch -> playSound(id, vol, delay, fromTouch) }
        }

        private fun playSound(id: String, vol: Float, delay: Long, fromTouch: Boolean) {
            val ctx = this@RockWallpaperService
            if (fromTouch) {
                if (!Palettes.soundTouch(ctx)) return
            } else {
                if (!Palettes.soundUnlock(ctx)) return
                // al desbloquear se respeta el modo silencio/vibración
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            }
            val sid = soundIds[id] ?: return
            handler.postDelayed({ soundPool?.play(sid, vol, vol, 1, 0, 1f) }, delay)
        }

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
            try { setupSounds() } catch (e: Exception) { }
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
            scene.setPalette(Palettes.selectedIndex(this@RockWallpaperService))
            scene.setLockedInstant(isLocked())
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            scene.onSound = null
            soundPool?.release()
            soundPool = null
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
                scene.setPalette(Palettes.selectedIndex(this@RockWallpaperService))
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
