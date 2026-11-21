package com.ctxaware.irada

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ctxaware.irada.data.*
import com.ctxaware.irada.review.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ApprovalTransactionTest {
    private val now = "2026-11-21T15:30:00"
    private fun batch(secondTitle: String = "Second") = PendingDraft(input = "  Original\nاردو  ", reviewJson = ReviewCodec.encode(
        ReviewDraft(listOf(ReviewTask(title = " Edited first ", notes = "  Notes\nExact  ", dueDate = "2026-11-20"),
            ReviewTask(title = secondTitle, reminderAt = "2026-11-22T09:00:00")))))

    @Test fun simultaneousDuplicateApprovalCommitsOnceAndSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "approve-${UUID.randomUUID()}.db"
        val expected = batch()
        try {
            IradaDatabase.open(context, name).let { db ->
                try {
                    val dao = db.approvals()
                    dao.saveIfPending(expected)
                    // Independent connections exercise the persisted receipt, beyond any UI/store lock.
                    val otherDb = IradaDatabase.open(context, name)
                    val outcomes = try {
                        coroutineScope { listOf(async(Dispatchers.IO) { dao.approve(expected, 1000, now) },
                            async(Dispatchers.IO) { otherDb.approvals().approve(expected, 2000, now) }).awaitAll() }
                    } finally { otherDb.close() }
                    assertEquals(outcomes[0], outcomes[1])
                    assertNull(dao.draft())
                    val tasks = dao.tasks()
                    assertEquals(2, tasks.size)
                    assertEquals(listOf(" Edited first ", "Second"), tasks.map { it.title })
                    assertEquals("  Notes\nExact  ", tasks[0].notes)
                    assertEquals("2026-11-20", tasks[0].dueDate)
                    assertEquals("2026-11-22T09:00:00", tasks[1].reminderAt)
                    assertEquals(listOf(1L, 2L), tasks.map { it.creationOrder })
                    assertTrue(tasks.all { it.status == "open" && it.sourceInput == expected.input })
                    assertFalse(dao.saveIfPending(expected))
                    assertNull(dao.draft())
                    val next = batch().copy(batchId = "different-batch")
                    assertTrue(dao.saveIfPending(next))
                    dao.approve(expected, 3000, now)
                    assertEquals(next, dao.draft())
                } finally { db.close() }
            }
            IradaDatabase.open(context, name).let { db ->
                try {
                    assertEquals(2, db.approvals().tasks().size)
                    assertEquals(2, db.approvals().receipt(expected.batchId)!!.taskCount)
                    db.approvals().approve(expected, 4000, now)
                    assertEquals(2, db.approvals().tasks().size)
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun staleReviewCannotApproveNewerEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "stale-${UUID.randomUUID()}.db"
        try {
            IradaDatabase.open(context, name).let { db ->
                try {
                    val old = batch()
                    val newer = old.copy(reviewJson = ReviewCodec.encode(ReviewDraft(listOf(ReviewTask(title = "New edit")))))
                    db.approvals().saveIfPending(newer)
                    try { db.approvals().approve(old, 1000, now); fail("Approved stale review") } catch (_: StaleDraft) { }
                    assertEquals(newer, db.approvals().draft())
                    assertTrue(db.approvals().tasks().isEmpty())
                    assertNull(db.approvals().receipt(old.batchId))
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun secondInsertFailureRollsBackTasksReceiptAndDraftConsumption() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "rollback-${UUID.randomUUID()}.db"
        val expected = batch("Fail this write")
        try {
            IradaDatabase.open(context, name).let { db ->
                try {
                    val dao = db.approvals()
                    dao.saveIfPending(expected)
                    context.openOrCreateDatabase(name, 0, null).use { raw ->
                        raw.execSQL("CREATE TRIGGER fail_batch BEFORE INSERT ON tasks WHEN NEW.title = 'Fail this write' BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END")
                    }
                    assertNotNull(runCatching { dao.approve(expected, 1000, now) }.exceptionOrNull())
                    assertEquals(expected, dao.draft())
                    assertTrue(dao.tasks().isEmpty())
                    assertNull(dao.receipt(expected.batchId))
                    context.openOrCreateDatabase(name, 0, null).use { it.execSQL("DROP TRIGGER fail_batch") }
                    dao.approve(expected, 2000, now)
                    assertEquals(2, dao.tasks().size)
                    assertNull(dao.draft())
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun invalidApprovalPreservesDraftAndLaterBatchKeepsReviewOrder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "invalid-${UUID.randomUUID()}.db"
        try {
            val db = IradaDatabase.open(context, name)
            try {
                val dao = db.approvals()
                for (review in listOf(ReviewDraft(emptyList()), ReviewDraft(listOf(ReviewTask(title = " "))),
                    ReviewDraft(listOf(ReviewTask(title = "Expired", reminderAt = now))))) {
                    val invalid = PendingDraft(input = "Keep input", reviewJson = ReviewCodec.encode(review))
                    dao.saveIfPending(invalid)
                    try { dao.approve(invalid, 1000, now); fail("Approved invalid batch") } catch (_: InvalidApproval) { }
                    assertEquals(invalid, dao.draft())
                    assertTrue(dao.tasks().isEmpty())
                    assertNull(dao.receipt(invalid.batchId))
                }
                val first = batch()
                dao.saveIfPending(first)
                dao.approve(first, 2000, now)
                val second = PendingDraft(input = "Another batch", reviewJson = ReviewCodec.encode(
                    ReviewDraft(listOf(ReviewTask(title = "Last"), ReviewTask(title = "Before last")))))
                dao.saveIfPending(second)
                dao.approve(second, 3000, now)
                assertEquals(listOf(1L, 2L, 3L, 4L), dao.tasks().map { it.creationOrder })
                assertEquals(listOf(" Edited first ", "Second", "Last", "Before last"), dao.tasks().map { it.title })
                assertNull(dao.draft())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
