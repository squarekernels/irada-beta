package com.ctxaware.irada.review

import com.ctxaware.irada.preview.PlanPreview
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Serializable
enum class ReviewField { TITLE, NOTES, DUE_DATE, REMINDER }

@Serializable
data class ReviewTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String = "",
    val dueDate: String? = null,
    val reminderAt: String? = null,
    val suggested: Boolean = false,
    val changedFields: Set<ReviewField> = emptySet(),
    val reason: String? = null
) {
    fun isSuggested(field: ReviewField) = suggested && field !in changedFields
    fun editTitle(value: String) = copy(title = value, changedFields = changedFields + ReviewField.TITLE)
    fun editNotes(value: String) = copy(notes = value, changedFields = changedFields + ReviewField.NOTES)
    fun editDueDate(value: String?) = copy(dueDate = value, changedFields = changedFields + ReviewField.DUE_DATE)
    fun editReminder(value: String?) = copy(reminderAt = value, changedFields = changedFields + ReviewField.REMINDER)
}

@Serializable
data class ReviewDraft(
    val tasks: List<ReviewTask>,
    val warnings: List<String> = emptyList(),
    val isStub: Boolean = true
) {
    fun update(id: String, edit: (ReviewTask) -> ReviewTask) = copy(tasks = tasks.map { if (it.id == id) edit(it) else it })
    fun add(task: ReviewTask = ReviewTask()) = if (tasks.size < 100) copy(tasks = tasks + task) else this
    fun remove(id: String) = copy(tasks = tasks.filterNot { it.id == id })
    fun insert(index: Int, task: ReviewTask) = if (tasks.size < 100 && tasks.none { it.id == task.id })
        copy(tasks = tasks.toMutableList().apply { add(index.coerceIn(0, size), task) }) else this
    fun move(id: String, delta: Int): ReviewDraft {
        val index = tasks.indexOfFirst { it.id == id }
        val next = index + delta
        if (index < 0 || next !in tasks.indices) return this
        return copy(tasks = tasks.toMutableList().apply { add(next, removeAt(index)) })
    }

    companion object {
        fun from(preview: PlanPreview) = ReviewDraft(preview.tasks.map {
            ReviewTask(title = it.title, notes = it.notes.orEmpty(), dueDate = it.dueDate,
                reminderAt = it.reminderAt, suggested = it.suggested, reason = it.reason)
        }, preview.warnings, preview.isStub)
    }
}

object ReviewCodec {
    private val json = Json { encodeDefaults = true }
    fun encode(review: ReviewDraft): String = json.encodeToString(review)
    fun decode(value: String?): ReviewDraft? {
        if (value == null) return null
        val review = json.decodeFromString<ReviewDraft>(value)
        // Empty titles/lists are valid pending edits, never approved tasks.
        require(review.tasks.size <= 100 && review.tasks.map { it.id }.distinct().size == review.tasks.size)
        review.tasks.forEach {
            require(it.id.isNotBlank())
            it.dueDate?.let(LocalDate::parse)
            it.reminderAt?.let(LocalDateTime::parse)
        }
        return review
    }
}
