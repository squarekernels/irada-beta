package com.ctxaware.irada

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ctxaware.irada.tasks.*
import com.ctxaware.irada.ui.theme.ActionPurple
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: CaptureState, onCapture: () -> Unit, onSettings: () -> Unit, onResume: () -> Unit, onRetryTasks: () -> Unit) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            today = now.toLocalDate()
            val midnight = today.plusDays(1).atStartOfDay(now.zone).toInstant()
            delay(Duration.between(now.toInstant(), midnight).toMillis().coerceIn(1000, 60000))
        }
    }
    val groups = remember(state.tasks, today) { groupOpenTasks(state.tasks, today) }
    val captureLabel = stringResource(R.string.add_tasks)
    Scaffold(topBar = {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_irada), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.app_name))
            }
        }, actions = {
            IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings)) }
        })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { if (!state.approving) onCapture() },
            modifier = Modifier.semantics { contentDescription = captureLabel },
            containerColor = ActionPurple, contentColor = Color.White,
            icon = { Icon(painterResource(R.drawable.ic_add), null) }, text = { Text(captureLabel) })
    }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).testTag("home_list"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.approving) item(key = "approving") { Text(stringResource(R.string.approving_tasks)) }
            if (state.draft.input.isNotEmpty()) item(key = "draft") {
                OutlinedButton(onClick = onResume, enabled = !state.approving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.resume_draft)) }
            }
            when {
                !state.tasksLoaded -> item(key = "loading") { CircularProgressIndicator() }
                state.taskLoadFailed -> item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.tasks_load_failed))
                        OutlinedButton(onClick = onRetryTasks) { Text(stringResource(R.string.retry)) }
                    }
                }
                groups.isEmpty() -> item(key = "empty") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
                else -> groups.forEach { group ->
                    item(key = "section-${group.bucket}") {
                        Text(stringResource(when (group.bucket) {
                            TaskBucket.OVERDUE -> R.string.overdue
                            TaskBucket.TODAY -> R.string.today
                            TaskBucket.UPCOMING -> R.string.upcoming
                            TaskBucket.ANYTIME -> R.string.anytime
                        }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    items(group.tasks, key = { it.id }) { task -> SavedTaskRow(task, group.bucket) }
                }
            }
        }
    }
}

@Composable
private fun SavedTaskRow(task: SavedTask, bucket: TaskBucket) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("saved-task-${task.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            task.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(task.dueDate?.let {
                val date = LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                stringResource(if (bucket == TaskBucket.OVERDUE) R.string.overdue_date else R.string.due_value, date)
            } ?: stringResource(R.string.anytime),
                color = if (bucket == TaskBucket.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            task.reminderAt?.let {
                Text(stringResource(R.string.saved_reminder_intent, LocalDateTime.parse(it).format(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))))
                Text(stringResource(R.string.reminder_delivery_pending), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
