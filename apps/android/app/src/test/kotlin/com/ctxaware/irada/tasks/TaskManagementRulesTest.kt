package com.ctxaware.irada.tasks

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TaskManagementRulesTest {
    private val task = SavedTask("id", "batch", "proposal", "Buy groceries", "Milk and bread", "2026-11-20",
        "2026-11-20T09:00", "open", "Exact original", 10, 10, 1)
    private val now = LocalDateTime.of(2026, 11, 21, 12, 0)

    @Test fun searchMatchesTitleAndNotesAcrossStatusAndKeepsDateOrder() {
        val completed = task.copy(id = "done", title = "Laundry", notes = "Detergent", status = "completed", dueDate = null, creationOrder = 2)
        assertEquals(listOf(task), matchingTasks(listOf(completed, task), "  GROCERIES  ", TaskFilter.ALL))
        assertEquals(listOf(completed), matchingTasks(listOf(completed, task), "detergent", TaskFilter.COMPLETED))
        assertTrue(matchingTasks(listOf(completed), "", TaskFilter.OPEN).isEmpty())
        assertEquals(listOf(task, completed), matchingTasks(listOf(completed, task), "", TaskFilter.ALL))
    }

    @Test fun existingPastReminderDoesNotPreventEverydayManagement() {
        assertTrue(validTaskEdit(task, task.copy(status = "completed"), now))
        assertTrue(validTaskEdit(task, task.copy(dueDate = "2026-11-21"), now))
        assertTrue(validTaskEdit(task, task.copy(reminderAt = null), now))
        assertFalse(validTaskEdit(task, task.copy(reminderAt = "2026-11-21T11:00"), now))
        assertTrue(validTaskEdit(task, task.copy(reminderAt = "2026-11-22T09:00"), now))
    }

    @Test fun editsCannotChangeIdentityProvenanceOrOrdering() {
        for (edit in listOf(task.copy(id = "new"), task.copy(batchId = "new"), task.copy(proposalId = "new"),
            task.copy(sourceInput = "new"), task.copy(createdAt = 11), task.copy(creationOrder = 2),
            task.copy(title = " "), task.copy(title = "x".repeat(201)), task.copy(notes = "x".repeat(5001)),
            task.copy(dueDate = "2026-02-30"), task.copy(status = "overdue"))) assertFalse(validTaskEdit(task, edit, now))
    }
}
