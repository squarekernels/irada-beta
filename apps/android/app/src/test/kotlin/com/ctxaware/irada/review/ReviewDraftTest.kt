package com.ctxaware.irada.review

import com.ctxaware.irada.preview.PlanPreview
import com.ctxaware.irada.preview.ProposedTask
import org.junit.Assert.*
import org.junit.Test

class ReviewDraftTest {
    @Test fun editingMarksOnlyChangedFieldsAsUserChoices() {
        val task = ReviewTask(title = "Buy groceries", dueDate = "2026-11-22",
            reminderAt = "2026-11-22T09:00:00", suggested = true, reason = "Tomorrow")
        val edited = task.editDueDate("2026-11-25").editNotes("  Milk\nBread  ")
        assertEquals(task.id, edited.id)
        assertFalse(edited.isSuggested(ReviewField.DUE_DATE))
        assertFalse(edited.isSuggested(ReviewField.NOTES))
        assertTrue(edited.isSuggested(ReviewField.TITLE))
        assertTrue(edited.isSuggested(ReviewField.REMINDER))
        assertEquals("2026-11-22T09:00:00", edited.reminderAt)
        assertEquals("Tomorrow", edited.reason)
    }

    @Test fun roundTripPreservesIdentityOrderWarningsAndExactEdits() {
        val original = ReviewDraft.from(PlanPreview("  Original\n", listOf(
            ProposedTask("One", null, "2026-11-22", null, true, "Tomorrow"),
            ProposedTask("Two", "notes", null, null, true, null)), true, "Stub", listOf("Check timing")))
        val first = original.tasks.first()
        val added = ReviewTask(title = "Third ☕", notes = "  Exact\nاردو  ")
        val edited = original.update(first.id) { it.editTitle("Changed").editDueDate(null) }
            .remove(original.tasks[1].id).add(added).move(added.id, -1)
        val restored = ReviewCodec.decode(ReviewCodec.encode(edited))!!
        assertEquals(listOf(added.id, first.id), restored.tasks.map { it.id })
        assertEquals(edited, restored)
        assertNull(restored.tasks[1].dueDate)
        assertFalse(restored.tasks[1].isSuggested(ReviewField.DUE_DATE))
        assertEquals(listOf("Check timing"), restored.warnings)
    }

    @Test fun removeUndoAndBoundaryMovesKeepOrderAndIds() {
        val one = ReviewTask(title = "One")
        val two = ReviewTask(title = "Two")
        val three = ReviewTask(title = "Three")
        val review = ReviewDraft(listOf(one, two, three))
        assertEquals(review, review.move(one.id, -1))
        assertEquals(review, review.move(three.id, 1))
        assertEquals(review, review.remove(two.id).insert(1, two))
        assertEquals(review, review.insert(0, one))
        assertEquals(listOf(two.id, one.id, three.id), review.move(two.id, -1).tasks.map { it.id })
    }

    @Test fun incompleteAndEmptyDraftsPersistButDuplicateIdsAreRejected() {
        val emptyTask = ReviewTask()
        val review = ReviewDraft(listOf(emptyTask))
        assertEquals(review, ReviewCodec.decode(ReviewCodec.encode(review)))
        assertEquals(ReviewDraft(emptyList()), ReviewCodec.decode(ReviewCodec.encode(review.remove(emptyTask.id))))
        try {
            ReviewCodec.decode(ReviewCodec.encode(ReviewDraft(listOf(emptyTask, emptyTask))))
            fail("Duplicate proposal identity accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
