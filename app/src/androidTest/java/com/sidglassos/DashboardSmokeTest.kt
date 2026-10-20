package com.sidglassos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Device/emulator tests. Do not count these as executed by a JVM build. */
class DashboardSmokeTest {
    // Queue composition effects while retaining the Compose test clock and normal synchronization.
    // https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html
    @get:Rule val compose = createAndroidComposeRule<DashboardActivity>(effectContext = StandardTestDispatcher())

    @Before fun awaitPreferenceLoad() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("A quieter kind of Android.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun dashboardCanOpenControlsWithoutSpecialPermissions() {
        compose.onNodeWithText("A quieter kind of Android.").assertIsDisplayed()
        compose.onNodeWithText("Controls").performClick()
        compose.onNodeWithText("Control Center", substring = true).assertIsDisplayed()
    }

    @Test fun appearancePageShowsAssignableOriginalSymbols() {
        compose.onNodeWithText("Icons").performClick()
        compose.onNodeWithText("Made of light").assertIsDisplayed()
        compose.onNodeWithText("Phone").assertIsDisplayed()
    }

    @Test fun selectedPageSurvivesActivityRecreation() {
        compose.onNodeWithText("Icons").performClick()
        compose.onNodeWithText("Made of light").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Made of light").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Made of light").assertIsDisplayed()
    }
}
