package com.ctxaware.irada.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.ctxaware.irada.IradaApplication
import com.ctxaware.irada.MainActivity
import com.ctxaware.irada.R
import com.ctxaware.irada.tasks.SavedTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

/** Wall-clock intent follows the current zone. Gaps advance by the DST gap; overlaps use the first offset. */
fun reminderInstant(local: String, zone: ZoneId): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
fun reminderVersion(task: SavedTask): String = "${task.reminderAt}|${task.updatedAt}"

enum class DeliveryStatus { ENABLED, DISABLED, ERROR }

/** One process-wide coordinator serializes committed task changes, reconciliation and receiver delivery. */
@android.annotation.SuppressLint("UseKtx") // Commit results enforce durable ordering.
class ReminderCoordinator(private val context: Context, private val tasks: suspend () -> List<SavedTask>,
    preferenceName: String = "reminders", private val channel: String = CHANNEL) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val ledger = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(DeliveryStatus.DISABLED)
    val status = _status.asStateFlow()

    init {
        notifications.createNotificationChannel(NotificationChannel(channel,
            context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun enabled(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE

    // Only a deliberate approval/save can claim the first runtime prompt. Denial never blocks saving.
    fun claimPermissionRequest(): Boolean {
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            ledger.getBoolean("asked", false)) return false
        check(ledger.edit().putBoolean("asked", true).commit())
        return true
    }

    fun refresh() { scope.launch { reconcile() } }

    suspend fun <T> change(write: suspend () -> T): T = lock.withLock {
        val result = write() // Scheduling failure must never turn a successful database write into a failed task edit.
        reconcileSafely()
        result
    }

    suspend fun reconcile() = lock.withLock { reconcileSafely() }

    private suspend fun reconcileSafely() {
        try {
            val saved = tasks().associateBy { it.id }
            val active = saved.values.filter { it.status == "open" && it.reminderAt != null }.associateBy { it.id }
            val permitted = enabled()
            val old = ledger.all.filterKeys { it.startsWith("scheduled:") }
            for ((key, value) in old) {
                val id = key.removePrefix("scheduled:")
                if (!permitted || active[id]?.let(::reminderVersion) != value) {
                    alarms.cancel(alarm(id))
                    notifications.cancel(id, 0)
                    check(ledger.edit().remove(key).commit())
                }
            }
            if (permitted) for (task in active.values) {
                val version = reminderVersion(task)
                if (ledger.getBoolean("delivered:${task.id}:${task.reminderAt}", false)) continue
                val trigger = reminderInstant(task.reminderAt!!, ZoneId.systemDefault()).toEpochMilli()
                // Persist before scheduling so interrupted work is repaired at the next lifecycle entry.
                check(ledger.edit().putString("scheduled:${task.id}", version).commit())
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.coerceAtLeast(System.currentTimeMillis()), alarm(task.id, version))
            }
            _status.value = if (permitted) DeliveryStatus.ENABLED else DeliveryStatus.DISABLED
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _status.value = DeliveryStatus.ERROR }
    }

    suspend fun deliver(id: String, version: String) = lock.withLock {
        val task = tasks().firstOrNull { it.id == id } ?: return@withLock
        if (task.status != "open" || task.reminderAt == null || reminderVersion(task) != version || !enabled()) return@withLock
        if (ledger.getBoolean("delivered:$id:${task.reminderAt}", false)) return@withLock
        // A queued broadcast from the old zone must not deliver a newly-future wall-clock reminder.
        if (reminderInstant(task.reminderAt, ZoneId.systemDefault()).isAfter(Instant.now())) {
            reconcileSafely(); return@withLock
        }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_reminder).setContentTitle(task.title)
            .setContentText(context.getString(R.string.reminder_notification))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        // At-most-once alert across duplicate broadcasts, process recreation and zone changes.
        check(ledger.edit().putBoolean("delivered:$id:${task.reminderAt}", true).commit())
        try { notifications.notify(id, 0, notification) }
        catch (_: SecurityException) { _status.value = DeliveryStatus.DISABLED }
    }

    private fun alarm(id: String, version: String = ""): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_DELIVER)
            .setData(Uri.Builder().scheme("irada-reminder").authority(context.packageName).appendPath(id).build())
            .putExtra("version", version), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val CHANNEL = "task_reminders"
        const val ACTION_DELIVER = "com.ctxaware.irada.DELIVER_REMINDER"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val coordinator = (context.applicationContext as IradaApplication).reminders
                if (intent.action == ReminderCoordinator.ACTION_DELIVER) {
                    val id = intent.data?.lastPathSegment ?: return@launch
                    val version = intent.getStringExtra("version") ?: return@launch
                    coordinator.deliver(id, version)
                } else if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED,
                        Intent.ACTION_TIME_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) coordinator.reconcile()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Reconciliation exposes a retryable status without crashing the receiver process.
                (context.applicationContext as IradaApplication).reminders.refresh()
            } finally { pending.finish() }
        }
    }
}
