package com.ctxaware.irada

import androidx.test.core.app.ApplicationProvider
import com.ctxaware.irada.data.*
import com.ctxaware.irada.review.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class TaskManagementTransactionTest {
    @Test fun changesAndDeleteUndoSurviveReopenAndRejectStaleSnapshots() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<IradaApplication>()
        val name = "management-${UUID.randomUUID()}.db"
        var db = IradaDatabase.open(app, name)
        try {
            val draft = PendingDraft(input = "Keep original", reviewJson = ReviewCodec.encode(ReviewDraft(listOf(
                ReviewTask(title = "Late task", notes = "Exact notes", dueDate = "2026-01-01")))))
            db.approvals().saveIfPending(draft)
            db.approvals().approve(draft, 100, LocalDateTime.now().toString())
            val original = db.approvals().tasks().single()
            val edited = original.copy(dueDate = "2026-11-21", updatedAt = 101)
            db.approvals().changeTask(original, edited)
            try { db.approvals().changeTask(original, original.copy(title = "Stale")); fail("Accepted stale edit") } catch (_: IllegalStateException) { }
            val done = edited.copy(status = "completed", updatedAt = 102)
            db.approvals().changeTask(edited, done)
            db.approvals().changeTask(done, null)
            assertTrue(db.approvals().tasks().isEmpty())
            db.approvals().changeTask(null, done)
            db.close()
            db = IradaDatabase.open(app, name)
            assertEquals(done, db.approvals().tasks().single())
            val reopened = done.copy(status = "open", updatedAt = 103)
            db.approvals().changeTask(done, reopened)
            try { db.approvals().changeTask(done, edited); fail("Accepted stale undo") } catch (_: IllegalStateException) { }
            assertEquals(reopened, db.approvals().tasks().single())
        } finally { db.close(); app.deleteDatabase(name) }
    }

    @Test fun approvalAfterDeletingNewestTaskDoesNotReuseItsOrderAndBreakUndo() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<IradaApplication>()
        val name = "management-${UUID.randomUUID()}.db"
        val db = IradaDatabase.open(app, name)
        try {
            suspend fun approve(title: String) {
                val draft = PendingDraft(input = title, reviewJson = ReviewCodec.encode(ReviewDraft(listOf(ReviewTask(title = title)))))
                db.approvals().saveIfPending(draft)
                db.approvals().approve(draft, 100, LocalDateTime.now().toString())
            }
            approve("First")
            val first = db.approvals().tasks().single()
            db.approvals().changeTask(first, null)
            approve("Second")
            assertTrue(db.approvals().tasks().single().creationOrder > first.creationOrder)
            db.approvals().changeTask(null, first)
            assertEquals(listOf("First", "Second"), db.approvals().tasks().map { it.title })
        } finally { db.close(); app.deleteDatabase(name) }
    }

    @Test fun failedMutationRollsBackAndCanBeRetried() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<IradaApplication>()
        val name = "management-${UUID.randomUUID()}.db"
        val db = IradaDatabase.open(app, name)
        try {
            val pending = PendingDraft(input = "Input", reviewJson = ReviewCodec.encode(ReviewDraft(listOf(ReviewTask(title = "Keep me")))))
            db.approvals().saveIfPending(pending)
            db.approvals().approve(pending, 100, LocalDateTime.now().toString())
            val original = db.approvals().tasks().single()
            app.openOrCreateDatabase(name, 0, null).use { raw ->
                raw.execSQL("CREATE TRIGGER reject_change BEFORE UPDATE ON tasks BEGIN SELECT RAISE(ABORT, 'failure'); END")
            }
            val changed = original.copy(title = "Edited", updatedAt = 101)
            var failed = false
            try { db.approvals().changeTask(original, changed) } catch (_: Exception) { failed = true }
            assertTrue(failed)
            assertEquals(original, db.approvals().tasks().single())
            app.openOrCreateDatabase(name, 0, null).use { it.execSQL("DROP TRIGGER reject_change") }
            db.approvals().changeTask(original, changed)
            assertEquals(changed, db.approvals().tasks().single())
        } finally { db.close(); app.deleteDatabase(name) }
    }
}
