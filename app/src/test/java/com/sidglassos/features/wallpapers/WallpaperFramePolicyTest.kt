package com.sidglassos.features.wallpapers

import com.sidglassos.core.storage.PerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperFramePolicyTest {
    @Test fun reducedMotionOverridesEveryQualityLevel() {
        PerformanceMode.entries.forEach {
            assertEquals(0, WallpaperFramePolicy.framesPerSecond(it, reducedMotion = true, powerSaver = false))
        }
    }
    @Test fun systemPowerSaverOverridesHighQuality() {
        assertEquals(0, WallpaperFramePolicy.framesPerSecond(PerformanceMode.HIGH_QUALITY, false, true))
    }
    @Test fun modesHaveBoundedFrameRates() {
        assertEquals(30, WallpaperFramePolicy.framesPerSecond(PerformanceMode.HIGH_QUALITY, false, false))
        assertEquals(24, WallpaperFramePolicy.framesPerSecond(PerformanceMode.BALANCED, false, false))
        assertEquals(0, WallpaperFramePolicy.framesPerSecond(PerformanceMode.BATTERY_SAVER, false, false))
    }
    @Test fun particleBudgetClampsInvalidOrExcessiveValues() {
        assertEquals(0, WallpaperFramePolicy.particles(-4, PerformanceMode.BALANCED))
        assertEquals(40, WallpaperFramePolicy.particles(500, PerformanceMode.BALANCED))
        assertEquals(80, WallpaperFramePolicy.particles(500, PerformanceMode.HIGH_QUALITY))
        assertEquals(24, WallpaperFramePolicy.particles(24, PerformanceMode.BALANCED))
    }
}
