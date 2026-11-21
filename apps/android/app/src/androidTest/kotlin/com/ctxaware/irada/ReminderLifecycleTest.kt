package com.ctxaware.irada

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ctxaware.irada.reminders.*
import com.ctxaware.irada.tasks.SavedTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class ReminderLifecycleTest {
    private val app = ApplicationProvider.getApplicationContext<IradaApplication>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private suspend fun awaitNotification(id: String, visible: Boolean = true) {
        val manager = app.getSystemService(NotificationManager::class.java)
        repeat(50) {
            if (manager.activeNotifications.any { it.tag == id } == visible) return
            kotlinx.coroutines.delay(100)
        }
        fail("Notification visibility did not become $visible")
    }
    private fun task() = SavedTask(UUID.randomUUID().toString(), "batch", "proposal", "Reminder test", null,
        null, LocalDateTime.now().minusMinutes(1).toString(), "open", "Source", 1, 1, 1)

    @Test fun staleDeliveryCompletionDeleteUndoAndRecreationAreIdempotent() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val name = "reminder-test-${UUID.randomUUID()}"
        var saved = listOf(task())
        val original = saved.single()
        val manager = app.getSystemService(NotificationManager::class.java)
        fun engine() = ReminderCoordinator(app, { saved }, name)
        val engine = engine()
        try {
            engine.reconcile()
            assertFalse(engine.claimPermissionRequest())
            assertEquals(DeliveryStatus.ENABLED, engine.status.value)
            val changed = original.copy(title = "Edited reminder", updatedAt = 2)
            engine.change { saved = listOf(changed) }
            engine.deliver(original.id, reminderVersion(original))
            awaitNotification(original.id, false)
            engine.deliver(changed.id, reminderVersion(changed))
            awaitNotification(original.id)
            val delivered = manager.activeNotifications.single { it.tag == original.id }
            engine.deliver(changed.id, reminderVersion(changed))
            assertEquals(delivered.postTime, manager.activeNotifications.single { it.tag == original.id }.postTime)
            assertEquals("Edited reminder", delivered.notification.extras.getString("android.title"))
            engine().deliver(changed.id, reminderVersion(changed))
            assertEquals(delivered.postTime, manager.activeNotifications.single { it.tag == original.id }.postTime)
            val done = changed.copy(status = "completed", updatedAt = 3)
            engine.change { saved = listOf(done) }
            awaitNotification(original.id, false)
            engine.deliver(changed.id, reminderVersion(changed))
            engine.change { saved = emptyList() }
            engine.change { saved = listOf(changed) } // Undo must not replay an already delivered reminder.
            engine().reconcile()
            engine.deliver(changed.id, reminderVersion(changed))
            awaitNotification(original.id, false)
            // A genuinely rescheduled reminder can deliver once again.
            val rescheduled = changed.copy(reminderAt = LocalDateTime.now().minusSeconds(10).toString(), updatedAt = 4)
            engine.change { saved = listOf(rescheduled) }
            engine.deliver(rescheduled.id, reminderVersion(rescheduled))
            awaitNotification(original.id)
        } finally {
            engine.change { saved = emptyList() }
            app.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }

    @Test fun committedTaskSchedulesRealReceiverAndCancelsAfterCompletion() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val store = app.drafts
        val task = task().copy(reminderAt = LocalDateTime.now().plusSeconds(2).toString(), creationOrder = 999999)
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            store.changeTask(null, task)
            val intent = android.content.Intent(app, ReminderReceiver::class.java)
                .setAction(ReminderCoordinator.ACTION_DELIVER)
                .setData(android.net.Uri.Builder().scheme("irada-reminder").authority(app.packageName).appendPath(task.id).build())
            val pending = android.app.PendingIntent.getBroadcast(app, 0, intent,
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
            assertNotNull("Committed reminder must create a real alarm PendingIntent", pending)
            kotlinx.coroutines.delay(2200)
            pending!!.send() // Exercise the manifest receiver independently of Android's inexact batching delay.
            repeat(30) {
                if (manager.activeNotifications.none { it.tag == task.id }) kotlinx.coroutines.delay(100)
            }
            assertTrue(manager.activeNotifications.any { it.tag == task.id })
            store.changeTask(task, task.copy(status = "completed", updatedAt = 2))
            awaitNotification(task.id, false)
        } finally {
            store.savedTasks().firstOrNull { it.id == task.id }?.let { store.changeTask(it, null) }
        }
        if (InstrumentationRegistry.getArguments().getString("keepStage6Demo") == "true") {
            val draft = com.ctxaware.irada.data.PendingDraft(input = "Remind me to review Stage 6", reviewJson =
                com.ctxaware.irada.review.ReviewCodec.encode(com.ctxaware.irada.review.ReviewDraft(listOf(
                    com.ctxaware.irada.review.ReviewTask(title = "Review Stage 6 reminders", reminderAt = LocalDateTime.now().plusMinutes(3).withSecond(0).withNano(0).toString())))))
            val revision = store.nextRevision()
            store.save(revision, draft)
            assertTrue(store.awaitSaved(revision))
            store.finishIntroduction()
            app.getSharedPreferences("reminders", 0).edit().remove("asked").commit()
        }
    }

    @Test fun blockedChannelPreservesTaskAndPreventsDelivery() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val name = "reminder-disabled-${UUID.randomUUID()}"
        val original = task()
        var saved = listOf(original)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(name, "Disabled test channel", NotificationManager.IMPORTANCE_NONE))
        val engine = ReminderCoordinator(app, { saved }, name, name)
        try {
            engine.reconcile()
            assertEquals(DeliveryStatus.DISABLED, engine.status.value)
            assertEquals(original, saved.single())
            engine.deliver(original.id, reminderVersion(original))
            awaitNotification(original.id, false)
        } finally {
            engine.change { saved = emptyList() }
            manager.deleteNotificationChannel(name)
            app.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }
}
