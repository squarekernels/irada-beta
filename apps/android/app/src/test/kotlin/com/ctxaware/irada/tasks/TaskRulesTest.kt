package com.ctxaware.irada.tasks

import com.ctxaware.irada.review.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskRulesTest {
    private val today = LocalDate.of(2026, 11, 21)
    private fun task(title: String, due: String?, order: Long, status: String = "open") = SavedTask(
        title, "batch", title, title, null, due, null, status, "Source", 100, 100, order)

    @Test fun calendarGroupsAndCreationOrderRemainStable() {
        val tasks = listOf(task("Any second", null, 9), task("Upcoming late", "2026-11-25", 1),
            task("Today second", "2026-11-21", 7), task("Today first", "2026-11-21", 3),
            task("Overdue", "2026-11-20", 5), task("Any first", null, 2),
            task("Upcoming early", "2026-11-22", 8), task("Completed", "2026-11-21", 10, "completed"))
        val groups = groupOpenTasks(tasks, today)
        assertEquals(TaskBucket.entries, groups.map { it.bucket })
        assertEquals(listOf("Overdue", "Today first", "Today second", "Upcoming early", "Upcoming late", "Any first", "Any second"),
            groups.flatMap { it.tasks }.map { it.title })
    }

    @Test fun dateOnlyTaskBecomesOverdueOnlyOnTheFollowingCalendarDay() {
        val saved = listOf(task("Due today", "2026-11-21", 1))
        assertEquals(TaskBucket.TODAY, groupOpenTasks(saved, today).single().bucket)
        assertEquals(TaskBucket.OVERDUE, groupOpenTasks(saved, today.plusDays(1)).single().bucket)
        assertEquals(TaskBucket.UPCOMING, groupOpenTasks(saved, today.minusDays(1)).single().bucket)
    }

    @Test fun approvalRejectsIncompleteAndPastReminderButAllowsOverdueDate() {
        val now = LocalDateTime.of(2026, 11, 21, 15, 30)
        val good = ReviewTask(title = "  Keep exact title  ", dueDate = "2026-11-01", reminderAt = "2026-11-22T09:00:00")
        assertTrue(ApprovalValidation.isValid(ReviewDraft(listOf(good)), now))
        for (bad in listOf(good.copy(title = " "), good.copy(title = "a".repeat(201)),
            good.copy(notes = "a".repeat(5001)), good.copy(reminderAt = "2026-11-21T15:30:00"),
            good.copy(reminderAt = "2026-11-20T09:00:00"), good.copy(dueDate = "2026-02-30"))) {
            assertFalse(ApprovalValidation.isValid(ReviewDraft(listOf(bad)), now))
        }
        assertFalse(ApprovalValidation.isValid(ReviewDraft(emptyList()), now))
        assertFalse(ApprovalValidation.isValid(ReviewDraft(listOf(good, good)), now))
    }
}
