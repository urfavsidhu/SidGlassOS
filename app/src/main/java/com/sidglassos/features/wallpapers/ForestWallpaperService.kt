package com.sidglassos.features.wallpapers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.sidglassos.core.storage.GlassSettings
import com.sidglassos.core.storage.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ForestWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ForestEngine()

    private inner class ForestEngine : Engine() {
        private val thread = HandlerThread("ForestWallpaper").apply { start() }
        private val handler = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val scene = ForestScene()
        private val power = getSystemService(PowerManager::class.java)
        @Volatile private var settings = GlassSettings()
        @Volatile private var visible = false
        @Volatile private var surfaceReady = false
        @Volatile private var destroyed = false
        @Volatile private var screenOn = true
        @Volatile private var saver = false
        @Volatile private var offset = 0f
        @Volatile private var width = 0
        @Volatile private var height = 0
        private val startTime = SystemClock.uptimeMillis()
        private val frame = Runnable { drawFrame() }
        // Serialize redraw requests with frames so a settings change during a static
        // frame cannot be removed when that older frame finishes.
        private val redrawRequest = Runnable {
            handler.removeCallbacks(frame)
            if (!destroyed && visible && surfaceReady && screenOn) handler.post(frame)
        }
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                screenOn = power?.isInteractive != false
                saver = power?.isPowerSaveMode == true
                redraw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(true)
            screenOn = power?.isInteractive != false
            saver = power?.isPowerSaveMode == true
            ContextCompat.registerReceiver(this@ForestWallpaperService, powerReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
            scope.launch {
                SettingsRepository(this@ForestWallpaperService).settings
                    .catch { emit(GlassSettings()) }
                    .collect { settings = it; redraw() }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) surfaceReady = surfaceHolder.surface.isValid
            redraw()
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            redraw()
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            surfaceReady = true
            redraw()
        }
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float,
            yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            offset = (xOffset - .5f) * 2f
            if (settings.parallax && !settings.reducedMotion) redraw()
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frame)
            handler.post { scene.release() }
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() {
            destroyed = true
            visible = false
            handler.removeCallbacks(frame)
            handler.removeCallbacks(redrawRequest)
            scope.cancel()
            runCatching { unregisterReceiver(powerReceiver) }
            handler.post { scene.release(); thread.quitSafely() }
            super.onDestroy()
        }

        private fun redraw() {
            handler.removeCallbacks(redrawRequest)
            if (!destroyed) handler.post(redrawRequest)
        }

        private fun drawFrame() {
            if (destroyed || !visible || !surfaceReady || !screenOn || width <= 0 || height <= 0) return
            val config = settings
            val fps = if (config.animationIntensity <= 0f || config.particleCount == 0) 0 else
                WallpaperFramePolicy.framesPerSecond(config.performance, config.reducedMotion, saver)
            val seconds = if (fps == 0) 0f else
                (SystemClock.uptimeMillis() - startTime) / 1000f * config.animationIntensity.coerceIn(0f, 1f)
            var canvas: android.graphics.Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                canvas?.let { scene.draw(it, width, height, seconds,
                    WallpaperFramePolicy.particles(config.particleCount, config.performance),
                    if (config.parallax && !config.reducedMotion && !saver) offset else 0f) }
            } catch (_: IllegalStateException) {
                // Surface can disappear while an OEM wallpaper host is changing activities.
                surfaceReady = false
            } catch (_: IllegalArgumentException) {
                // Invalid surfaces are retried only after a visibility/surface callback.
                surfaceReady = false
            } finally {
                canvas?.let { current -> runCatching { surfaceHolder.unlockCanvasAndPost(current) } }
            }
            // A queued redraw request will replace this timer on the same render thread.
            handler.removeCallbacks(frame)
            if (fps > 0 && !destroyed && visible && surfaceReady && screenOn)
                handler.postDelayed(frame, 1000L / fps)
        }
    }
}
