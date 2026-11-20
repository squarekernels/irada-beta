package com.ctxaware.irada

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals

class CaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureRecreatesResumesAndExplicitlyDiscards() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Go to tasks").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription("Add tasks").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Go to tasks").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Go to tasks").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Add tasks").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Add tasks").performClick()
        if (compose.onAllNodesWithText("Discard and start over").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Discard and start over").performClick()
        val input = "  Buy groceries\nCall Mum\n\nBook dentist  "
        compose.onNode(hasSetTextAction()).performTextReplacement(input)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Draft saved on this device").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction()).assertTextContains(input)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("A little space to think").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add tasks").performClick()
        compose.onNodeWithText("You have a draft").assertIsDisplayed()
        compose.onNode(hasText("Resume draft") and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains(input)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Add tasks").performClick()
        compose.onNodeWithText("Discard and start over").performClick()
        assertEquals("", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("A little space to think").assertIsDisplayed()
    }
}
