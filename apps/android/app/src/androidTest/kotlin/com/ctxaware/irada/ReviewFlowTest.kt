package com.ctxaware.irada

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ctxaware.irada.preview.*
import com.ctxaware.irada.review.ReviewCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNodeWithTag("review_list").performScrollToNode(matcher)
    }
    private fun saved(app: IradaApplication) = runBlocking {
        assertTrue(app.drafts.awaitSaved(app.drafts.currentRevision))
    }

    @Test fun editDateRemoveAddReorderLeaveAndRestoreEntirePendingBatch() {
        val app = ApplicationProvider.getApplicationContext<IradaApplication>()
        val service = app.previewService
        app.previewService = PreviewService { request ->
            PlanPreview(request.input, listOf(
                ProposedTask("Buy groceries", null, "2026-11-22", null, true, "Based on tomorrow"),
                ProposedTask("Call Mum", null, null, null, true, null)), true, "Stub", listOf("Check this timing"))
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
                val input = "Buy groceries tomorrow\nCall Mum"
                compose.onNode(hasSetTextAction()).performTextReplacement(input)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Draft saved on this device").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Make a plan").performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Review your plan").fetchSemanticsNodes().isNotEmpty() }
                scrollTo(hasContentDescription("Edit task 1"))
                compose.onNodeWithContentDescription("Edit task 1").performClick()
                compose.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("Edited groceries")
                compose.onNode(hasSetTextAction() and hasText("Notes (optional)")).performTextReplacement("  Milk\nBread  ")
                compose.onNodeWithText("Choose due date").performScrollTo().performClick()
                compose.onNodeWithText("Wednesday, November 25, 2026").performClick()
                compose.onNodeWithText("Set date").performClick()
                compose.onNodeWithText("Set a proposed reminder").performScrollTo().performClick()
                compose.onNodeWithText("Wednesday, November 25, 2026").performClick()
                compose.onNodeWithText("Set date").performClick()
                compose.onNodeWithText("Set time").performClick()
                compose.onNodeWithText("Done").performClick()
                saved(app)
                scrollTo(hasText("Due: Nov 25, 2026"))
                compose.onNodeWithText("Due: Nov 25, 2026").assertExists()
                scrollTo(hasContentDescription("Remove task 2"))
                compose.onNodeWithContentDescription("Remove task 2").performClick()
                compose.onNodeWithText("Undo").performClick()
                scrollTo(hasContentDescription("Remove task 2"))
                compose.onNodeWithContentDescription("Remove task 2").performClick()
                scrollTo(hasText("Add a task"))
                compose.onNodeWithText("Add a task").performClick()
                compose.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("Added task")
                compose.onNodeWithText("Done").performClick()
                scrollTo(hasContentDescription("Move task 2 up"))
                compose.onNodeWithContentDescription("Move task 2 up").assertIsDisplayed().assertIsEnabled().performClick()
                compose.waitForIdle()
                saved(app)
                val afterMove = ReviewCodec.decode(runBlocking { app.drafts.load().second.reviewJson })!!
                assertEquals(listOf("Added task", "Edited groceries"), afterMove.tasks.map { it.title })
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("A little space to think").assertIsDisplayed()
                compose.onNodeWithText("Resume draft").performClick()
                scrollTo(hasText("1. Added task"))
                compose.onNodeWithText("1. Added task").assertExists()
                compose.onNodeWithText("Anytime").assertExists()
                scrollTo(hasText("2. Edited groceries"))
                compose.onNodeWithText("2. Edited groceries").assertExists()
                scenario.recreate()
                compose.onNodeWithText("Review your plan").assertIsDisplayed()
                scrollTo(hasText("Edit original input"))
                compose.onNodeWithText("Edit original input").performClick()
                compose.onNodeWithText("Keep editing").performClick()
                compose.onNodeWithText("Review your plan").assertIsDisplayed()
                saved(app)
                val draft = runBlocking { app.drafts.load().second }
                val review = ReviewCodec.decode(draft.reviewJson)!!
                assertEquals(input, draft.input)
                assertEquals(listOf("Added task", "Edited groceries"), review.tasks.map { it.title })
                assertEquals("2026-11-25", review.tasks[1].dueDate)
                assertEquals("2026-11-25T09:00:00", review.tasks[1].reminderAt)
                assertEquals("  Milk\nBread  ", review.tasks[1].notes)
                assertEquals(listOf("Check this timing"), review.warnings)
            }
            // New Activity/ViewModel resumes the pending review rather than re-generating it.
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Resume draft").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Resume draft").performClick()
                scrollTo(hasText("1. Added task"))
                compose.onNodeWithText("1. Added task").assertExists()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("A little space to think").assertIsDisplayed()
            }
        } finally { app.previewService = service }
    }
}
