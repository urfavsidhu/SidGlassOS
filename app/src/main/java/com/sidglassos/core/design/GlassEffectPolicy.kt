package com.sidglassos.core.design

import com.sidglassos.core.storage.PerformanceMode

/** Bounded, static GPU effects. No CPU blur, screenshot capture or frame timer. */
object GlassEffectPolicy {
    fun blurRadiusPx(sdk: Int, accelerated: Boolean, powerSaver: Boolean,
                     performance: PerformanceMode, density: Float): Float {
        if (sdk < 31 || !accelerated || powerSaver || performance == PerformanceMode.BATTERY_SAVER) return 0f
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return if (performance == PerformanceMode.HIGH_QUALITY) (14f * scale).coerceAtMost(32f)
        else (9f * scale).coerceAtMost(24f)
    }
}

/** ContentScale.Crop mapping shared by the scene and its cropped glass backgrounds. */
data class BackdropMapping(val scale: Float, val left: Float, val top: Float) {
    companion object {
        fun crop(imageWidth: Int, imageHeight: Int, width: Float, height: Float): BackdropMapping {
            if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return BackdropMapping(1f, 0f, 0f)
            val scale = maxOf(width / imageWidth, height / imageHeight)
            return BackdropMapping(scale, (width - imageWidth * scale) / 2f, (height - imageHeight * scale) / 2f)
        }
    }
}
