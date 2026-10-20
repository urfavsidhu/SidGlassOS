package com.sidglassos.features.wallpapers

import com.sidglassos.core.storage.PerformanceMode

object WallpaperFramePolicy {
    /** Zero means a single frame on changes; no background animation task remains scheduled. */
    fun framesPerSecond(mode: PerformanceMode, reducedMotion: Boolean, powerSaver: Boolean): Int =
        when {
            reducedMotion || powerSaver || mode == PerformanceMode.BATTERY_SAVER -> 0
            mode == PerformanceMode.HIGH_QUALITY -> 30
            else -> 24
        }

    fun particles(requested: Int, mode: PerformanceMode): Int =
        requested.coerceIn(0, if (mode == PerformanceMode.HIGH_QUALITY) 80 else 40)
}
