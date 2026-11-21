package com.ctxaware.irada

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ctxaware.irada.data.IradaDatabase
import com.ctxaware.irada.data.PendingDraft
import com.ctxaware.irada.review.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DraftPersistenceTest {
    @Test fun exactInputAndIdentitySurviveDatabaseReopenAndReplacement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "draft-test-${UUID.randomUUID()}.db"
        val original = PendingDraft(input = "  Buy groceries\nCall Mum\n\nاردو ☕  ")
        try {
            IradaDatabase.open(context, name).let { db ->
                try { db.drafts().save(original) } finally { db.close() }
            }
            IradaDatabase.open(context, name).let { db ->
                try {
                    assertEquals(original, db.drafts().read())
                    val edited = original.copy(input = original.input + "\nBook dentist")
                    db.drafts().save(edited)
                    assertEquals(original.batchId, db.drafts().read()!!.batchId)
                    val replacement = PendingDraft()
                    db.drafts().save(replacement)
                    assertEquals(replacement, db.drafts().read())
                    assertNotEquals(original.batchId, replacement.batchId)
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun reviewAndOrderSurviveClosingDatabaseWithoutCreatingTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "review-test-${UUID.randomUUID()}.db"
        val one = ReviewTask(title = "Changed", dueDate = "2026-11-25", suggested = true,
            changedFields = setOf(ReviewField.TITLE, ReviewField.DUE_DATE))
        val two = ReviewTask(title = "Added", notes = "  Notes\nاردو  ")
        val review = ReviewDraft(listOf(two, one), listOf("Check this interpretation"))
        val draft = PendingDraft(input = "  Original\n", reviewJson = ReviewCodec.encode(review))
        try {
            IradaDatabase.open(context, name).let { db ->
                try { db.drafts().save(draft) } finally { db.close() }
            }
            IradaDatabase.open(context, name).let { db ->
                try {
                    assertEquals(draft, db.drafts().read())
                    assertEquals(review, ReviewCodec.decode(db.drafts().read()!!.reviewJson))
                } finally { db.close() }
            }
            context.openOrCreateDatabase(name, 0, null).use { raw ->
                raw.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null).use { cursor ->
                    val tables = mutableSetOf<String>()
                    while (cursor.moveToNext()) tables += cursor.getString(0)
                    assertEquals(setOf("pending_draft", "room_master_table", "android_metadata", "tasks", "approved_batches"), tables)
                    raw.rawQuery("SELECT COUNT(*) FROM tasks", null).use { tasks ->
                        assertTrue(tasks.moveToFirst()); assertEquals(0, tasks.getInt(0))
                    }
                }
                raw.rawQuery("SELECT COUNT(*) FROM pending_draft", null).use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0))
                }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migrationFromVersionOnePreservesRawInputAndBatchIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE pending_draft (slot INTEGER NOT NULL PRIMARY KEY, batchId TEXT NOT NULL, input TEXT NOT NULL)")
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                old.execSQL("INSERT INTO room_master_table VALUES (42, '75245822d502476ba7bd27ead24d5544')")
                old.execSQL("INSERT INTO pending_draft VALUES (1, ?, ?)", arrayOf("stable-batch", "  Exact\nاردو  "))
                old.version = 1
            }
            IradaDatabase.open(context, name).let { db ->
                try {
                    val migrated = db.drafts().read()!!
                    assertEquals("stable-batch", migrated.batchId)
                    assertEquals("  Exact\nاردو  ", migrated.input)
                    assertNull(migrated.reviewJson)
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migrationFromVersionTwoKeepsEditedReviewAndStartsWithNoApprovedTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v2-${UUID.randomUUID()}.db"
        val pending = PendingDraft(input = "  Original\nاردو  ", reviewJson = ReviewCodec.encode(
            ReviewDraft(listOf(ReviewTask(title = "Edited title", notes = "Exact notes", dueDate = "2026-11-25")), listOf("Keep warning"))))
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                old.execSQL("CREATE TABLE pending_draft (slot INTEGER NOT NULL PRIMARY KEY, batchId TEXT NOT NULL, input TEXT NOT NULL, reviewJson TEXT DEFAULT NULL)")
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                old.execSQL("INSERT INTO room_master_table VALUES (42, '67f62420360ea2d08d19bb8eb343069c')")
                old.execSQL("INSERT INTO pending_draft VALUES (1, ?, ?, ?)", arrayOf(pending.batchId, pending.input, pending.reviewJson))
                old.version = 2
            }
            IradaDatabase.open(context, name).let { db ->
                try {
                    assertEquals(pending, db.drafts().read())
                    assertTrue(db.approvals().tasks().isEmpty())
                    assertNull(db.approvals().receipt(pending.batchId))
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

}
