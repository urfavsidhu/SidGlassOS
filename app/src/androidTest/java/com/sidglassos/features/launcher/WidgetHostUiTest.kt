package com.sidglassos.features.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

/** Uses real Android binding/configuration UI; never adopts shell permissions or changes Home role. */
class WidgetHostUiTest {
    @get:Rule val compose = createAndroidComposeRule<LauncherActivity>(effectContext = StandardTestDispatcher())
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private var baseline = emptySet<Int>()
    private var prepared = false

    @Before fun preserveExistingWidgets() {
        assertTrue("Widget mutation tests require an Android emulator", Build.MODEL.contains("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        baseline = hostValue { it.widgets.map(HostedWidget::id).toSet() }
        assertEquals(AppWidgetManager.INVALID_APPWIDGET_ID, hostValue { it.pendingId })
        prepared = true
    }

    @After fun removeOnlyTestInstances() {
        if (!prepared) return
        if (device.hasObject(By.text("Configure test widget")) || device.hasObject(By.pkg("com.android.settings"))) device.pressBack()
        compose.activityRule.scenario.onActivity { activity ->
            val host = activity.widgetHost
            if (host.pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.cancelPending()
            host.widgets.filter { it.id !in baseline }.forEach { host.remove(it.id) }
        }
    }

    @Test fun closingPickerAllocatesNoWidgetIds() {
        val ids = systemIds()
        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithText("Add a widget").assertIsDisplayed()
        compose.onNodeWithText("Close widget picker").performClick()
        assertEquals(ids, systemIds())
        assertEquals(baseline, hostValue { it.widgets.map(HostedWidget::id).toSet() })
    }

    @Test fun deniedBindingReleasesItsAllocatedId() {
        val ids = systemIds()
        select("Sid test configurable")
        assertTrue("Android's widget consent screen was not shown", device.wait(Until.hasObject(By.pkg("com.android.settings")), 15_000))
        val pending = hostValue { it.pendingId }
        assertTrue(pending > 0)
        device.pressBack()
        waitHost { it.pendingId == AppWidgetManager.INVALID_APPWIDGET_ID }
        assertEquals(ids, systemIds())
        assertEquals(baseline, hostValue { it.widgets.map(HostedWidget::id).toSet() })
    }

    @Test fun nativeWidgetResizesSurvivesRecreationAndCanBeRemoved() {
        select("Glass clock")
        approveBinding()
        waitHost { it.widgets.any { widget -> widget.id !in baseline } }
        val item = hostValue { it.widgets.first { widget -> widget.id !in baseline } }
        compose.waitUntil(15_000) { hasNativeView(item.id) }
        compose.onNodeWithText("Taller widget").performScrollTo().performClick()
        val resizedHeight = hostValue { it.widgets.first { widget -> widget.id == item.id }.heightDp }
        assertTrue(resizedHeight > item.heightDp)
        compose.activityRule.scenario.recreate()
        waitHost { it.widgets.any { widget -> widget.id == item.id && widget.heightDp == resizedHeight } }
        compose.waitUntil(15_000) { hasNativeView(item.id) }
        compose.onNodeWithText("Remove widget").performScrollTo().performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        waitHost { it.widgets.none { widget -> widget.id == item.id } }
        assertFalse(systemIds().contains(item.id))
    }

    @Test fun requiredConfigurationRunsBeforeTheWidgetIsCommitted() {
        select("Sid test configurable")
        approveBinding()
        assertTrue(device.wait(Until.hasObject(By.text("Configure test widget")), 15_000))
        assertEquals(baseline, hostValue { it.widgets.map(HostedWidget::id).toSet() })
        device.findObject(By.text("Save test widget")).click()
        waitHost { it.widgets.any { widget -> widget.id !in baseline } }
        val item = hostValue { it.widgets.first { widget -> widget.id !in baseline } }
        // Advance Compose's test clock through page navigation before querying native UI automation.
        compose.waitUntil(15_000) { hasNativeView(item.id, "Configured test widget") }
        assertTrue(device.wait(Until.hasObject(By.text("Configured test widget")), 15_000))
    }

    @Test fun canceledConfigurationDoesNotLeaveAWidgetOrAllocatedId() {
        val ids = systemIds()
        select("Sid test configurable")
        approveBinding()
        assertTrue(device.wait(Until.hasObject(By.text("Cancel test widget")), 15_000))
        device.findObject(By.text("Cancel test widget")).click()
        waitHost { it.pendingId == AppWidgetManager.INVALID_APPWIDGET_ID }
        assertEquals(baseline, hostValue { it.widgets.map(HostedWidget::id).toSet() })
        assertEquals(ids, systemIds())
    }

    private fun select(label: String) {
        compose.onNodeWithText("Widgets").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(label)
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Add $label").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add $label").performClick()
    }

    private fun approveBinding() {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.hasObject(By.text("Configure test widget")) || hostValue { it.widgets.any { widget -> widget.id !in baseline } }) return
            val positive = device.findObject(By.pkg("com.android.settings").text(Pattern.compile("(?i)allow|create|add|add widget")))
            if (positive != null) { positive.click(); return }
            SystemClock.sleep(100)
        }
        fail("Android did not present a usable widget consent action or complete binding")
    }

    private fun waitHost(predicate: (LauncherWidgetHost) -> Boolean) { compose.waitUntil(20_000) { hostValue(predicate) } }
    private fun systemIds(): Set<Int> = AppWidgetHost(instrumentation.targetContext, LauncherWidgetHost.HOST_ID).appWidgetIds.toSet()
    private fun hasNativeView(id: Int, expectedText: String? = null): Boolean {
        var found = false
        compose.activityRule.scenario.onActivity { activity ->
            fun visit(view: View) {
                if (view is AppWidgetHostView && view.appWidgetId == id) {
                    fun hasClock(child: View): Boolean = (if (expectedText == null) child is android.widget.TextClock
                        else child is android.widget.TextView && child.text.toString() == expectedText) ||
                        (child is ViewGroup && (0 until child.childCount).any { hasClock(child.getChildAt(it)) })
                    if (hasClock(view)) found = true
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
        }
        return found
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> hostValue(block: (LauncherWidgetHost) -> T): T {
        var value: Any? = null
        compose.activityRule.scenario.onActivity { value = block(it.widgetHost) }
        return value as T
    }
}
