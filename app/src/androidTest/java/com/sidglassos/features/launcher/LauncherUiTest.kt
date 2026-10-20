package com.sidglassos.features.launcher

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Starts HOME directly for testing; never requests or changes the user's default launcher. */
@RunWith(AndroidJUnit4::class)
class LauncherUiTest {
    // Queue composition effects while retaining the Compose test clock and normal synchronization.
    // https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html
    @get:Rule val compose = createAndroidComposeRule<LauncherActivity>(effectContext = StandardTestDispatcher())

    @Test fun drawerSearchReportsAnEmptyResultWithoutLaunchingAnything() {
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Your apps").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("sidglass-no-installed-app-can-match-928173")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No matching apps.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No matching apps.").assertIsDisplayed()
    }

    @Test fun homeIntentClosesTheDrawer() {
        compose.onNodeWithText("All apps").performClick()
        compose.onNodeWithText("Your apps").assertIsDisplayed()
        var scenarioLaunchIntent: Intent? = null
        try {
            compose.activityRule.scenario.onActivity { activity ->
                scenarioLaunchIntent = Intent(activity.intent)
                // Exercise the real callback-to-UI path on the scenario's main thread without
                // starting an Android HOME task. Default-home selection, task reuse and OEM
                // Home gestures remain separate device checks.
                // https://developer.android.com/reference/android/app/Instrumentation#callActivityOnNewIntent(android.app.Activity,android.content.Intent)
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,
                    Intent(activity, LauncherActivity::class.java)
                        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            compose.onNodeWithText("Your apps").assertDoesNotExist()
            compose.onNodeWithText("All apps").assertIsDisplayed()
        } finally {
            // The production callback correctly updates Activity.intent. ActivityScenario's
            // lifecycle observer filters events against its original launch intent's action
            // and categories, so restore that fixture metadata only after the UI assertions
            // to let the rule observe DESTROYED during teardown. Do not call onNewIntent again.
            // https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java
            scenarioLaunchIntent?.let { original ->
                compose.activityRule.scenario.onActivity { activity -> activity.intent = original }
            }
        }
    }
}
