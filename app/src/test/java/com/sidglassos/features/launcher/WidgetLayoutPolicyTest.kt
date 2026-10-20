package com.sidglassos.features.launcher

import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutPolicyTest {
    @Test fun duplicateAndInvalidIdsCannotCreateMultipleViewsForOneBinding() {
        assertEquals(listOf(HostedWidget(3, 48), HostedWidget(7, 1200)), WidgetLayoutPolicy.normalize(
            listOf(HostedWidget(-1), HostedWidget(3, -20), HostedWidget(3, 900), HostedWidget(7, 9000))))
    }
    @Test fun hostCapacityBoundsRetainedNativeViews() {
        assertEquals(12, WidgetLayoutPolicy.normalize((1..100).map { HostedWidget(it) }).size)
    }
    @Test fun providerResizeConstraintsAndMalformedBoundsAreRespected() {
        assertEquals(180, WidgetLayoutPolicy.height(80, 180, 400))
        assertEquals(400, WidgetLayoutPolicy.height(700, 180, 400))
        assertEquals(180, WidgetLayoutPolicy.height(250, 180, -1))
    }
    @Test fun reorderPreservesIdsAndDoesNotCrossThePageBoundaries() {
        val items = listOf(HostedWidget(1), HostedWidget(2), HostedWidget(3))
        assertEquals(listOf(2, 1, 3), WidgetLayoutPolicy.move(items, 1, 1).map { it.id })
        assertEquals(items, WidgetLayoutPolicy.move(items, 1, -1))
        assertEquals(items, WidgetLayoutPolicy.move(items, 99, 1))
    }
}
