package com.ctxaware.irada

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ctxaware.irada.tasks.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(task: SavedTask?, busy: Boolean, onSave: (SavedTask, SavedTask) -> Unit,
    onToggle: (SavedTask) -> Unit, onDelete: (SavedTask) -> Unit, onBack: () -> Unit) {
    if (task == null) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text(stringResource(R.string.task_unavailable))
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }
    var title by rememberSaveable(task.id, task.updatedAt) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id, task.updatedAt) { mutableStateOf(task.notes.orEmpty()) }
    var due by rememberSaveable(task.id, task.updatedAt) { mutableStateOf(task.dueDate) }
    var reminder by rememberSaveable(task.id, task.updatedAt) { mutableStateOf(task.reminderAt) }
    var datePicker by rememberSaveable { mutableStateOf<String?>(null) }
    var timeDate by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val after = task.copy(title = title, notes = notes.takeIf { it.isNotEmpty() }, dueDate = due, reminderAt = reminder)
    val dirty = after != task
    fun back() { if (dirty) discard = true else onBack() }
    BackHandler { if (!busy) back() }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text(stringResource(R.string.discard_task_edits)) },
        text = { Text(stringResource(R.string.discard_task_edits_body)) },
        confirmButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.discard_changes)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } })
    datePicker?.let { kind ->
        CalendarDateDialog(if (kind == "due") due?.let(LocalDate::parse) else reminder?.let(LocalDateTime::parse)?.toLocalDate(),
            onDismiss = { datePicker = null }, onSet = { date ->
                if (kind == "due") due = date.toString() else timeDate = date.toString()
                datePicker = null
            })
    }
    timeDate?.let { date ->
        ReminderTimeDialog(reminder?.let(LocalDateTime::parse)?.toLocalTime() ?: LocalTime.of(9, 0),
            onDismiss = { timeDate = null }, onSet = { time -> reminder = LocalDate.parse(date).atTime(time).toString(); timeDate = null })
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.task_details)) }, navigationIcon = {
        IconButton(onClick = { back() }, enabled = !busy) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
    }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (task.status == "open") R.string.filter_open else R.string.filter_completed), style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.task_title)) },
                enabled = !busy, isError = title.isBlank() || title.length > 200, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.title_limit, title.length)) })
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.task_notes)) },
                enabled = !busy, isError = notes.length > 5000, minLines = 3, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.notes_limit, notes.length)) })
            Text(stringResource(R.string.due_date_label), style = MaterialTheme.typography.titleMedium)
            Text(due?.let { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) } ?: stringResource(R.string.anytime))
            OutlinedButton(onClick = { focus.clearFocus(); datePicker = "due" }, enabled = !busy) { Text(stringResource(R.string.reschedule_task)) }
            if (due != null) TextButton(onClick = { due = null }, enabled = !busy) { Text(stringResource(R.string.make_anytime)) }
            Text(stringResource(R.string.reminder_label), style = MaterialTheme.typography.titleMedium)
            Text(reminder?.let { LocalDateTime.parse(it).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)) } ?: stringResource(R.string.no_reminder))
            Text(stringResource(R.string.reschedule_reminder_independent), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { focus.clearFocus(); datePicker = "reminder" }, enabled = !busy) { Text(stringResource(if (reminder == null) R.string.set_task_reminder else R.string.change_task_reminder)) }
            if (reminder != null) {
                Text(stringResource(R.string.reminder_delivery_pending))
                TextButton(onClick = { reminder = null }, enabled = !busy) { Text(stringResource(R.string.remove_reminder)) }
            }
            if (!validTaskEdit(task, after)) Text(stringResource(R.string.task_edit_invalid), color = MaterialTheme.colorScheme.error)
            Button(onClick = { focus.clearFocus(); onSave(task, after) }, enabled = dirty && !busy && validTaskEdit(task, after),
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.task_saving else R.string.save_changes)) }
            if (dirty) Text(stringResource(R.string.save_before_other_actions))
            OutlinedButton(onClick = { onToggle(task) }, enabled = !busy && !dirty, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (task.status == "open") R.string.complete_task else R.string.reopen_task))
            }
            TextButton(onClick = { onDelete(task) }, enabled = !busy && !dirty, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete_task)) }
            Text(stringResource(R.string.original_input), style = MaterialTheme.typography.titleMedium)
            Text(task.sourceInput)
        }
    }
}
