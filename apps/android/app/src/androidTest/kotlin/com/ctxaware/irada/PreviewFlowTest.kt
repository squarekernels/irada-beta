package com.ctxaware.irada

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ctxaware.irada.preview.*
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class PreviewFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cancelErrorManualRetryAndPreviewKeepInputAndHomeEmpty() {
        val application = ApplicationProvider.getApplicationContext<IradaApplication>()
        val original = application.previewService
        val calls = AtomicInteger()
        var mode = "slow"
        application.previewService = PreviewService { request ->
            calls.incrementAndGet()
            when (mode) {
                "slow" -> delay(60_000)
                "failed" -> throw IOException()
            }
            PlanPreview(request.input, listOf(ProposedTask("Buy groceries", null, null, null, true, null)),
                true, "Placeholder")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
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
                val input = "Buy groceries\n"
                compose.onNode(hasSetTextAction()).performTextReplacement(input)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Draft saved on this device").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Make a plan").performScrollTo().performClick()
                compose.waitUntil(10_000) { calls.get() == 1 }
                compose.onNodeWithText("Making your plan…").assertIsDisplayed()
                scenario.recreate()
                compose.onNodeWithText("Making your plan…").assertIsDisplayed()
                assertEquals(1, calls.get())
                compose.onNodeWithText("Cancel").performClick()
                compose.onNode(hasSetTextAction()).assertTextContains(input)
                mode = "failed"
                compose.onNodeWithText("Make a plan").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Could not make a plan").fetchSemanticsNodes().isNotEmpty() }
                mode = "success"
                compose.waitForIdle()
                assertEquals(2, calls.get())
                compose.onNodeWithText("Retry").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Review your plan").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Anytime").assertExists()
                compose.onNodeWithText("No reminder").assertExists()
                assertEquals(3, calls.get())
                scenario.recreate()
                compose.onNodeWithText("Review your plan").assertIsDisplayed()
                assertEquals(3, calls.get())
                compose.onNodeWithTag("review_list").performScrollToNode(hasText("Edit original input"))
                compose.onNodeWithText("Edit original input").performClick()
                compose.onNodeWithText("Discard proposals").performClick()
                compose.onNode(hasSetTextAction()).assertTextContains(input)
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("A little space to think").assertIsDisplayed()
                compose.onNodeWithText("Resume draft").assertIsDisplayed()
            }
        } finally { application.previewService = original }
    }
}
