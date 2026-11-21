package com.ctxaware.irada.tasks

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.ctxaware.irada.review.ReviewDraft
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "tasks", indices = [Index(value = ["batchId", "proposalId"], unique = true), Index(value = ["creationOrder"], unique = true)])
data class SavedTask(
    @PrimaryKey val id: String,
    val batchId: String,
    val proposalId: String,
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val reminderAt: String?,
    val status: String,
    val sourceInput: String,
    val createdAt: Long,
    val updatedAt: Long,
    val creationOrder: Long
)

@Entity(tableName = "approved_batches")
data class ApprovedBatch(@PrimaryKey val batchId: String, val taskCount: Int, val approvedAt: Long)

object ApprovalValidation {
    fun isValid(review: ReviewDraft?, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (review == null || review.tasks.size !in 1..100) return false
        return try {
            review.tasks.map { it.id }.distinct().size == review.tasks.size && review.tasks.all { task ->
                task.id.isNotBlank() && task.title.isNotBlank() && task.title.length <= 200 && task.notes.length <= 5000 &&
                    (task.dueDate == null || LocalDate.parse(task.dueDate).toString() == task.dueDate) &&
                    (task.reminderAt == null || LocalDateTime.parse(task.reminderAt).isAfter(now))
            }
        } catch (_: Exception) { false }
    }
}

enum class TaskBucket { OVERDUE, TODAY, UPCOMING, ANYTIME }
data class TaskGroup(val bucket: TaskBucket, val tasks: List<SavedTask>)

fun groupOpenTasks(tasks: List<SavedTask>, today: LocalDate): List<TaskGroup> {
    val sorted = tasks.filter { it.status == "open" }.sortedWith(
        compareBy<SavedTask> { it.dueDate == null }.thenBy { it.dueDate }.thenBy { it.creationOrder }
    )
    val grouped = sorted.groupBy { task ->
        val due = task.dueDate?.let(LocalDate::parse)
        when {
            due == null -> TaskBucket.ANYTIME
            due < today -> TaskBucket.OVERDUE
            due == today -> TaskBucket.TODAY
            else -> TaskBucket.UPCOMING
        }
    }
    return TaskBucket.entries.mapNotNull { bucket -> grouped[bucket]?.let { TaskGroup(bucket, it) } }
}


enum class TaskFilter { OPEN, COMPLETED, ALL }

fun matchingTasks(tasks: List<SavedTask>, query: String, filter: TaskFilter): List<SavedTask> {
    val needle = query.trim()
    return tasks.filter { task ->
        (filter == TaskFilter.ALL || task.status == if (filter == TaskFilter.OPEN) "open" else "completed") &&
            (needle.isEmpty() || task.title.contains(needle, ignoreCase = true) || task.notes.orEmpty().contains(needle, ignoreCase = true))
    }.sortedWith(compareBy<SavedTask> { it.dueDate == null }.thenBy { it.dueDate }.thenBy { it.creationOrder })
}

fun validTaskEdit(before: SavedTask, after: SavedTask, now: LocalDateTime = LocalDateTime.now()): Boolean = try {
    before.copy(title = after.title, notes = after.notes, dueDate = after.dueDate,
        reminderAt = after.reminderAt, status = after.status, updatedAt = after.updatedAt) == after &&
        after.title.isNotBlank() && after.title.length <= 200 && after.notes.orEmpty().length <= 5000 &&
        after.status in listOf("open", "completed") &&
        (after.dueDate == null || LocalDate.parse(after.dueDate).toString() == after.dueDate) &&
        (after.reminderAt == null || after.reminderAt == before.reminderAt || LocalDateTime.parse(after.reminderAt).isAfter(now))
} catch (_: Exception) { false }
