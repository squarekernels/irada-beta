package com.ctxaware.irada

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ctxaware.irada.data.IradaDatabase
import com.ctxaware.irada.data.PendingDraft
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
}
