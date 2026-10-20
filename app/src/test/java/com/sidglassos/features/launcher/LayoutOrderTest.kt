package com.sidglassos.features.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutOrderTest {
    @Test fun movingForwardAndBackwardPreservesEveryComponent() {
        assertEquals(listOf("b", "c", "a", "d"), LayoutOrder.move(listOf("a", "b", "c", "d"), "a", "c"))
        assertEquals(listOf("c", "a", "b", "d"), LayoutOrder.move(listOf("a", "b", "c", "d"), "c", "a"))
    }

    @Test fun missingComponentsDoNotInsertOrDeleteApps() {
        assertEquals(listOf("a", "b"), LayoutOrder.move(listOf("a", "b"), "gone", "b"))
        assertEquals(listOf("a", "b"), LayoutOrder.move(listOf("a", "b"), "a", "gone"))
    }

    @Test fun accessibilityMovementClampsAtPageCollectionBoundaries() {
        assertEquals(listOf("a", "b"), LayoutOrder.moveBy(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), LayoutOrder.moveBy(listOf("a", "b"), "b", 1))
        assertEquals(listOf("b", "a"), LayoutOrder.moveBy(listOf("a", "b"), "a", 1))
    }

    @Test fun searchHandlesCaseWhitespaceAndDuplicateAppLabels() {
        val apps = listOf(LaunchableApp("one", "Camera", "com.oppo.camera"), LaunchableApp("two", "Camera", "org.other.camera"))
        assertEquals(apps, LayoutOrder.search(apps, "  CAMERA  "))
        assertEquals(listOf(apps[1]), LayoutOrder.search(apps, "org.other"))
        assertEquals(apps, LayoutOrder.search(apps, "  "))
        assertEquals(emptyList<LaunchableApp>(), LayoutOrder.search(apps, "missing"))
    }
}
