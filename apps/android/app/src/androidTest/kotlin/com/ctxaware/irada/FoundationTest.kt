package com.ctxaware.irada

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class FoundationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun reachHome() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Go to tasks").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription("Add tasks").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Go to tasks").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Go to tasks").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Add tasks").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun homeCaptureAndSettingsHaveWorkingBackNavigation() {
        compose.onNodeWithText("A little space to think").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add tasks").performClick()
        if (compose.onAllNodesWithText("Resume draft").fetchSemanticsNodes().size > 1)
            compose.onNodeWithText("Discard and start over").performClick()
        compose.onNodeWithText("What needs doing?").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Back to tasks").performClick()
        compose.onNodeWithContentDescription("Add tasks").assertIsDisplayed()
    }

    @Test fun recreationPreservesDestination() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Back to tasks").assertIsDisplayed()
    }
}
