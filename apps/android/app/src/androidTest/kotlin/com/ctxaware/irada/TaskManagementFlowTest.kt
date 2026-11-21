package com.ctxaware.irada

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ctxaware.irada.data.*
import com.ctxaware.irada.review.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.*

class TaskManagementFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<IradaApplication>()
    private lateinit var taskId: String

    @Before fun seed() = runBlocking {
        app.drafts.load()
        app.drafts.finishIntroduction()
        val db = IradaDatabase.open(app)
        try {
            app.openOrCreateDatabase("irada.db", 0, null).use { raw ->
                raw.execSQL("DELETE FROM tasks")
                raw.execSQL("DELETE FROM pending_draft")
                raw.execSQL("DELETE FROM approved_batches")
            }
            val draft = PendingDraft(input = "Synthetic Stage 5 input", reviewJson = ReviewCodec.encode(ReviewDraft(listOf(
                ReviewTask(title = "Buy groceries", notes = "Milk and bread", dueDate = LocalDate.now().minusDays(1).toString(),
                    reminderAt = LocalDate.now().plusDays(2).atTime(9, 0).toString())))))
            db.approvals().saveIfPending(draft)
            db.approvals().approve(draft, System.currentTimeMillis(), LocalDateTime.now().toString())
            taskId = db.approvals().tasks().single().id
        } finally { db.close() }
    }
    @After fun clean() {
        // Opt-in synthetic fixture for manual offline/large-text review on the validation app.
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("keepStage5Demo") == "true") return
        app.openOrCreateDatabase("irada.db", 0, null).use { raw ->
            raw.execSQL("DROP TRIGGER IF EXISTS reject_ui_edit")
            raw.execSQL("DELETE FROM tasks")
            raw.execSQL("DELETE FROM pending_draft")
            raw.execSQL("DELETE FROM approved_batches")
        }
    }
    private fun homeReady() = compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() }
    private fun row() = compose.onNodeWithTag("home_list").performScrollToNode(hasText("Buy groceries"))
    private fun detailAction(text: String) = compose.onNodeWithText(text).performScrollTo().assertIsEnabled().performClick()
    private fun saved() = IradaDatabase.open(app).let { db -> try { runBlocking { db.approvals().tasks().single() } } finally { db.close() } }

    @Test fun offlineRescheduleCompleteSearchDeleteUndoAndRelaunch() {
        val originalService = app.previewService
        app.previewService = com.ctxaware.irada.preview.PreviewService { error("Task management must not request a preview") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                homeReady()
                row()
                compose.onNodeWithText("Buy groceries").performClick()
                detailAction("Reschedule")
                val today = LocalDate.now()
                compose.onNode(hasText("Today,", substring = true) and hasClickAction()).performClick()
                compose.onNodeWithText("Set date").performClick()
                detailAction("Save changes")
                homeReady()
                assertEquals(today.toString(), saved().dueDate)
                assertNotNull(saved().reminderAt)
                row()
                compose.onNodeWithContentDescription("Complete Buy groceries").performScrollTo().performClick()
                compose.onNodeWithText("Completed", useUnmergedTree = true).performScrollTo().performClick()
                row()
                compose.onNodeWithText("Buy groceries").performClick()
                detailAction("Delete task")
                homeReady()
                compose.onNodeWithText("Undo").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("saved-task-$taskId").fetchSemanticsNodes().isNotEmpty() }
                assertEquals("completed", saved().status)
                scenario.recreate()
                homeReady()
                compose.onNodeWithText("Completed", useUnmergedTree = true).performScrollTo().performClick()
                row()
                compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("bread")
                row()
                assertEquals("Milk and bread", saved().notes)
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                homeReady()
                compose.onNodeWithText("Completed", useUnmergedTree = true).performClick()
                row()
                compose.onNodeWithContentDescription("Reopen Buy groceries").performScrollTo().performClick()
                compose.waitUntil(10_000) { saved().status == "open" }
            }
        } finally { app.previewService = originalService }
    }

    @Test fun savingEditedFieldsSurvivesRelaunchAndWriteFailureKeepsEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            homeReady(); row()
            compose.onNodeWithText("Buy groceries").performClick()
            compose.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("Edited title")
            compose.onNode(hasSetTextAction() and hasText("Notes (optional)")).performTextReplacement("Exact edited notes")
            detailAction("Make Anytime")
            detailAction("Remove reminder")
            app.openOrCreateDatabase("irada.db", 0, null).use { raw ->
                raw.execSQL("CREATE TRIGGER reject_ui_edit BEFORE UPDATE ON tasks BEGIN SELECT RAISE(ABORT, 'failure'); END")
            }
            try {
                detailAction("Save changes")
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Change could not be saved. Your saved tasks are unchanged. Reopen the task and try again.").fetchSemanticsNodes().isNotEmpty() }
                assertEquals("Buy groceries", saved().title)
                compose.onNode(hasSetTextAction() and hasText("Edited title")).assertExists()
            } finally {
                app.openOrCreateDatabase("irada.db", 0, null).use { it.execSQL("DROP TRIGGER reject_ui_edit") }
            }
            detailAction("Save changes")
            homeReady()
            compose.waitUntil(10_000) { saved().title == "Edited title" }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            homeReady()
            compose.onNodeWithTag("home_list").performScrollToNode(hasText("Edited title"))
            val task = saved()
            assertEquals("Exact edited notes", task.notes)
            assertNull(task.dueDate)
            assertNull(task.reminderAt)
            assertEquals("Synthetic Stage 5 input", task.sourceInput)
        }
    }

    @Test fun unsavedEditorBackRequiresExplicitDiscardAndRotationKeepsText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            homeReady(); row()
            compose.onNodeWithText("Buy groceries").performClick()
            compose.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("Unsaved title")
            scenario.recreate()
            compose.onNode(hasSetTextAction() and hasText("Unsaved title")).assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNode(hasSetTextAction() and hasText("Unsaved title")).assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Discard changes").performClick()
            homeReady(); row()
            assertEquals("Buy groceries", saved().title)
        }
    }
}
