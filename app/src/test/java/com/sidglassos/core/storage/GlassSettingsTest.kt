package com.sidglassos.core.storage

import org.junit.Assert.*
import org.junit.Test

class GlassSettingsTest {
    @Test fun corruptOrExtremePreferencesStayWithinRenderingBudgets() {
        val s = GlassSettings(gridColumns = 100, iconSize = -2, glassOpacity = Float.NaN,
            particleCount = 10_000, animationIntensity = Float.POSITIVE_INFINITY,
            iconSpacing = -1, widgetOpacity = Float.NaN).normalized()
        assertEquals(6, s.gridColumns)
        assertEquals(40, s.iconSize)
        assertEquals(.16f, s.glassOpacity)
        assertEquals(60, s.particleCount)
        assertEquals(.5f, s.animationIntensity)
        assertEquals(4, s.iconSpacing)
        assertEquals(.72f, s.widgetOpacity)
    }

    @Test fun persistentLayoutKeepsOrderWhileRemovingDuplicateOrBlankEntries() {
        val s = GlassSettings(homeOrder = listOf("a", "", "b", "a"), dock = listOf("a", "b", "a", "c", "d", "e")).normalized()
        assertEquals(listOf("a", "b"), s.homeOrder)
        assertEquals(listOf("a", "b", "c", "d"), s.dock)
    }

    @Test fun balancedDefaultDoesNotGrantAccessOrEnableMotionFeatures() {
        val s = GlassSettings()
        assertEquals(PerformanceMode.BALANCED, s.performance)
        assertFalse(s.parallax)
        assertFalse(s.launcherInitialized)
        assertTrue(s.homeOrder.isEmpty())
    }

    @Test fun rapidEditsFromTheSameSnapshotDoNotUndoEachOther() {
        val displayed = GlassSettings()
        val firstEdit = displayed.copy(gridColumns = 6)
        val secondEditBeforeReload = displayed.copy(showLabels = false)
        val stored = displayed.mergeEdits(displayed, firstEdit)
            .mergeEdits(displayed, secondEditBeforeReload)
        assertEquals(6, stored.gridColumns)
        assertFalse(stored.showLabels)
    }

    @Test fun appearanceEditPreservesLauncherChangesMadeAfterScreenLoaded() {
        val displayed = GlassSettings()
        val latest = displayed.copy(launcherInitialized = true,
            homeOrder = listOf("camera/component", "music/component"),
            dock = listOf("camera/component"), iconAssignments = mapOf("camera/component" to "Camera"))
        val stored = latest.mergeEdits(displayed, displayed.copy(glassOpacity = .65f))
        assertEquals(latest.homeOrder, stored.homeOrder)
        assertEquals(latest.dock, stored.dock)
        assertEquals(latest.iconAssignments, stored.iconAssignments)
        assertTrue(stored.launcherInitialized)
        assertEquals(.65f, stored.glassOpacity)
    }

    @Test fun newestIntentionalEditWinsForTheSameField() {
        val displayed = GlassSettings()
        val stored = displayed.mergeEdits(displayed, displayed.copy(iconSize = 64))
            .mergeEdits(displayed, displayed.copy(iconSize = 48))
        assertEquals(48, stored.iconSize)
    }

    @Test fun widgetColorsRetainOnlyArgbBits() {
        assertEquals(0xFFFFFFFFL, GlassSettings(widgetTint = -1L).normalized().widgetTint)
        assertEquals(0xFFDDE5F4L, GlassSettings(widgetTint = 0x123FFDDE5F4L).normalized().widgetTint)
    }
}
