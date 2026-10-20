package com.sidglassos.core.design

import com.sidglassos.core.storage.PerformanceMode
import org.junit.Assert.*
import org.junit.Test

class GlassEffectPolicyTest {
    @Test fun unsupportedAndSoftwareWindowsNeverEnableGpuBlur() {
        assertEquals(0f, GlassEffectPolicy.blurRadiusPx(30, true, false, PerformanceMode.HIGH_QUALITY, 3f), 0f)
        assertEquals(0f, GlassEffectPolicy.blurRadiusPx(36, false, false, PerformanceMode.HIGH_QUALITY, 3f), 0f)
    }
    @Test fun eitherPowerSaverSwitchOverridesQuality() {
        assertEquals(0f, GlassEffectPolicy.blurRadiusPx(36, true, true, PerformanceMode.HIGH_QUALITY, 3f), 0f)
        assertEquals(0f, GlassEffectPolicy.blurRadiusPx(36, true, false, PerformanceMode.BATTERY_SAVER, 3f), 0f)
    }
    @Test fun radiiAreBoundedEvenAtExtremeOrInvalidDensity() {
        assertEquals(32f, GlassEffectPolicy.blurRadiusPx(36, true, false, PerformanceMode.HIGH_QUALITY, 100f), 0f)
        assertEquals(24f, GlassEffectPolicy.blurRadiusPx(36, true, false, PerformanceMode.BALANCED, 100f), 0f)
        assertEquals(9f, GlassEffectPolicy.blurRadiusPx(36, true, false, PerformanceMode.BALANCED, Float.NaN), 0f)
    }
    @Test fun backdropCropUsesTheSameCenteredTextureInBothOrientations() {
        val portrait = BackdropMapping.crop(720, 1440, 1080f, 2400f)
        assertEquals(2400f / 1440f, portrait.scale, .001f)
        assertEquals(-60f, portrait.left, .001f)
        assertEquals(0f, portrait.top, .001f)
        val landscape = BackdropMapping.crop(720, 1440, 2400f, 1080f)
        assertEquals(0f, landscape.left, .001f)
        assertEquals(-1860f, landscape.top, .001f)
    }
}
