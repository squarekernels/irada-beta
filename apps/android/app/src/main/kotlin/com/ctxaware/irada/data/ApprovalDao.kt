package com.ctxaware.irada.data

import androidx.room3.*
import com.ctxaware.irada.review.ReviewCodec
import com.ctxaware.irada.tasks.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.UUID

class StaleDraft(val current: PendingDraft) : Exception()
class InvalidApproval : Exception()

@Dao
abstract class ApprovalDao {
    @Query("SELECT * FROM pending_draft WHERE slot = 1")
    abstract suspend fun draft(): PendingDraft?

    @Query("SELECT * FROM approved_batches WHERE batchId = :batchId")
    abstract suspend fun receipt(batchId: String): ApprovedBatch?

    @Upsert
    protected abstract suspend fun writeDraft(draft: PendingDraft)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertTasks(tasks: List<SavedTask>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertReceipt(receipt: ApprovedBatch)

    @Query("DELETE FROM pending_draft WHERE slot = 1 AND batchId = :batchId")
    protected abstract suspend fun consumeDraft(batchId: String): Int

    // Receipts retain the high-water mark after deletion, leaving room for a pending Undo.
    @Query("SELECT MAX(COALESCE((SELECT MAX(creationOrder) FROM tasks), 0), COALESCE((SELECT SUM(taskCount) FROM approved_batches), 0))")
    protected abstract suspend fun lastCreationOrder(): Long

    @Query("SELECT * FROM tasks ORDER BY creationOrder")
    abstract fun observeTasks(): Flow<List<SavedTask>>

    @Query("SELECT * FROM tasks ORDER BY creationOrder")
    abstract suspend fun tasks(): List<SavedTask>

    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun task(id: String): SavedTask?

    @Update
    protected abstract suspend fun updateTask(task: SavedTask)

    @Delete
    protected abstract suspend fun deleteTask(task: SavedTask)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun restoreTask(task: SavedTask)

    /** Compare full snapshots inside the transaction; stale screens/Undo cannot overwrite newer work. */
    @Transaction
    open suspend fun changeTask(before: SavedTask?, after: SavedTask?) {
        require(before != null || after != null)
        val id = before?.id ?: after!!.id
        require(after == null || after.id == id)
        check(task(id) == before) { "Task changed. Reopen it and try again." }
        when {
            after == null -> deleteTask(before!!)
            before == null -> restoreTask(after)
            else -> updateTask(after)
        }
    }

    // Serialised with approval at the database boundary, even for another DB instance.
    @Transaction
    open suspend fun saveIfPending(draft: PendingDraft): Boolean {
        if (receipt(draft.batchId) != null) return false
        writeDraft(draft)
        return true
    }

    @Transaction
    open suspend fun approve(expected: PendingDraft, approvedAt: Long, localNow: String): ApprovedBatch {
        receipt(expected.batchId)?.let { return it }
        // Match values as well as identity: an older screen cannot approve stale edits.
        val pending = draft()
        if (pending != expected) throw StaleDraft(pending ?: PendingDraft())
        val review = ReviewCodec.decode(pending.reviewJson)
        if (!ApprovalValidation.isValid(review, LocalDateTime.parse(localNow))) throw InvalidApproval()
        val firstOrder = lastCreationOrder()
        val tasks = review!!.tasks.mapIndexed { index, task ->
            SavedTask(
                id = UUID.nameUUIDFromBytes("${pending.batchId}:${task.id}".toByteArray(Charsets.UTF_8)).toString(),
                batchId = pending.batchId, proposalId = task.id,
                title = task.title, notes = task.notes.takeIf { it.isNotEmpty() },
                dueDate = task.dueDate, reminderAt = task.reminderAt, status = "open",
                sourceInput = pending.input, createdAt = approvedAt, updatedAt = approvedAt,
                creationOrder = firstOrder + index + 1
            )
        }
        insertTasks(tasks)
        val receipt = ApprovedBatch(pending.batchId, tasks.size, approvedAt)
        insertReceipt(receipt)
        check(consumeDraft(pending.batchId) == 1)
        return receipt
    }
}
