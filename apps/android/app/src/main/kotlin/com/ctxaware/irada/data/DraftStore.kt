package com.ctxaware.irada.data

import android.content.Context
import androidx.room3.*
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** The fixed slot permits one pending batch; batchId survives edits and relaunches. */
@Entity(tableName = "pending_draft")
data class PendingDraft(
    @PrimaryKey val slot: Int = 1,
    val batchId: String = UUID.randomUUID().toString(),
    val input: String = ""
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM pending_draft WHERE slot = 1")
    suspend fun read(): PendingDraft?

    @Upsert
    suspend fun save(draft: PendingDraft)
}

@Database(entities = [PendingDraft::class], version = 1, exportSchema = true)
abstract class IradaDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao

    companion object {
        fun open(context: Context, name: String = "irada.db") =
            Room.databaseBuilder<IradaDatabase>(context.applicationContext, name)
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}

private val Context.preferences by preferencesDataStore(name = "settings")
private val introduced = booleanPreferencesKey("introduced")

data class WriteResult(val revision: Long, val failed: Boolean)

class DraftStore(context: Context) {
    private val revisions = AtomicLong()
    @Volatile private var cached: PendingDraft? = null
    private val database = IradaDatabase.open(context)
    private val preferences = context.applicationContext.preferences
    // Application lifetime: navigation and Activity destruction cannot cancel queued writes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Pair<Long, PendingDraft>>(Channel.UNLIMITED)
    private val _result = MutableStateFlow(WriteResult(0, false))
    val result = _result.asStateFlow()

    init {
        scope.launch {
            for ((revision, draft) in writes) {
                try {
                    database.drafts().save(draft)
                    _result.value = WriteResult(revision, false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _result.value = WriteResult(revision, true)
                }
            }
        }
    }

    suspend fun load(): Pair<Boolean, PendingDraft> {
        val seen = preferences.data.first()[introduced] ?: false
        val draft = cached ?: (database.drafts().read() ?: PendingDraft()).also { cached = it }
        return seen to draft
    }

    val currentRevision: Long get() = revisions.get()

    fun nextRevision(): Long = revisions.incrementAndGet()

    suspend fun finishIntroduction() { preferences.edit { it[introduced] = true } }

    suspend fun awaitSaved(revision: Long): Boolean =
        !result.first { it.revision >= revision }.failed

    fun save(revision: Long, draft: PendingDraft) {
        cached = draft
        check(writes.trySend(revision to draft).isSuccess)
    }
}
