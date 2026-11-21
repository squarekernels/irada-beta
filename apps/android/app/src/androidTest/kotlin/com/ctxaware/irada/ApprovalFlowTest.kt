package com.ctxaware.irada

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ctxaware.irada.data.*
import com.ctxaware.irada.preview.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class ApprovalFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<IradaApplication>()
    @Before fun cleanBeforeTest() = cleanTasks()
    @After fun cleanAfterTest() = cleanTasks()
    private fun openCapture() {
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
        compose.onNode(hasSetTextAction()).performTextReplacement("Synthetic approval input")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Draft saved on this device").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Make a plan").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review your plan").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun scrollReview(matcher: SemanticsMatcher) = compose.onNodeWithTag("review_list").performScrollToNode(matcher)
    private fun scrollHome(text: String) = compose.onNodeWithTag("home_list").performScrollToNode(hasText(text))
    private fun cleanTasks() {
        runBlocking { app.drafts.load() }
        app.openOrCreateDatabase("irada.db", 0, null).use { raw ->
            raw.execSQL("DROP TRIGGER IF EXISTS fail_ui_batch")
            raw.execSQL("DELETE FROM pending_draft")
            raw.execSQL("DELETE FROM tasks")
            raw.execSQL("DELETE FROM approved_batches")
        }
    }

    @Test fun doubleTapApprovesEditedBatchExactlyOnceAndHomeGroupsSavedTasks() {
        val original = app.previewService
        val today = LocalDate.now()
        val calls = AtomicInteger()
        app.previewService = PreviewService { request ->
            calls.incrementAndGet()
            PlanPreview(request.input, listOf(
                ProposedTask("Late task", null, today.minusDays(1).toString(), null, true, null),
                ProposedTask("Today task", null, today.toString(), null, true, null),
                ProposedTask("Future task", "Future notes", today.plusDays(1).toString(), "${today.plusDays(1)}T09:00:00", true, null),
                ProposedTask("Anytime task", null, null, null, true, null)), true, "Stub")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                openCapture()
                scrollReview(hasContentDescription("Edit task 1"))
                compose.onNodeWithContentDescription("Edit task 1").performClick()
                compose.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("Edited late task")
                compose.onNodeWithText("Done").performClick()
                scrollReview(hasText("Approve 4 tasks"))
                runBlocking { assertTrue(app.drafts.awaitSaved(app.drafts.currentRevision)) }
                compose.onNodeWithText("Approve 4 tasks").assertIsEnabled()
                compose.onNodeWithText("Approve 4 tasks").performTouchInput { doubleClick() }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
                for (text in listOf("Overdue", "Edited late task", "Today", "Today task", "Upcoming", "Future task", "Anytime task")) scrollHome(text)
                compose.onNodeWithText("Resume draft").assertDoesNotExist()
                IradaDatabase.open(app).let { db ->
                    try { runBlocking {
                        val tasks = db.approvals().tasks()
                        assertEquals(4, tasks.size)
                        assertEquals(4, tasks.map { it.id }.distinct().size)
                        assertEquals("Edited late task", tasks[0].title)
                        assertEquals("Future notes", tasks[2].notes)
                        assertNotNull(tasks[2].reminderAt)
                        assertNull(db.approvals().draft())
                    } } finally { db.close() }
                }
                assertEquals(1, calls.get())
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
                scrollHome("Edited late task")
                scrollHome("Anytime task")
                compose.onNodeWithText("Resume draft").assertDoesNotExist()
            }
        } finally { app.previewService = original; cleanTasks() }
    }

    @Test fun realWriteFailureLeavesReviewAndNoTasksThenExplicitRetrySucceeds() {
        val original = app.previewService
        val calls = AtomicInteger()
        app.previewService = PreviewService { request ->
            calls.incrementAndGet()
            PlanPreview(request.input, listOf(
                ProposedTask("Keep this edit", "Exact notes", null, null, true, null),
                ProposedTask("Fail this write", null, null, null, true, null)), true, "Stub")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                openCapture()
                runBlocking { assertTrue(app.drafts.awaitSaved(app.drafts.currentRevision)) }
                val expected = runBlocking { app.drafts.load().second }
                app.openOrCreateDatabase("irada.db", 0, null).use { raw ->
                    raw.execSQL("CREATE TRIGGER fail_ui_batch BEFORE INSERT ON tasks WHEN NEW.title = 'Fail this write' BEGIN SELECT RAISE(ABORT, 'simulated failure'); END")
                }
                scrollReview(hasText("Approve 2 tasks"))
                compose.onNodeWithText("Approve 2 tasks").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Tasks could not be saved. Your review is still here. Please try approval again.").fetchSemanticsNodes().isNotEmpty() }
                IradaDatabase.open(app).let { db ->
                    try { runBlocking {
                        assertEquals(expected, db.approvals().draft())
                        assertTrue(db.approvals().tasks().isEmpty())
                        assertNull(db.approvals().receipt(expected.batchId))
                    } } finally { db.close() }
                }
                app.openOrCreateDatabase("irada.db", 0, null).use { it.execSQL("DROP TRIGGER fail_ui_batch") }
                compose.onNodeWithText("Approve 2 tasks").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
                scrollHome("Keep this edit")
                scrollHome("Fail this write")
                assertEquals(1, calls.get())
            }
        } finally { app.previewService = original; cleanTasks() }
    }
}
