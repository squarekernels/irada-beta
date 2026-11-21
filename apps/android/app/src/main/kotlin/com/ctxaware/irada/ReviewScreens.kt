package com.ctxaware.irada

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ctxaware.irada.review.*
import com.ctxaware.irada.tasks.ApprovalValidation
import androidx.compose.ui.res.pluralStringResource
import com.ctxaware.irada.ui.theme.ActionPurple
import androidx.compose.ui.graphics.Color
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun dateLabel(value: String) = LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
private fun reminderLabel(value: String) = LocalDateTime.parse(value).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

@Composable
private fun DraftSaveStatus(state: CaptureState, onRetry: () -> Unit) {
    Text(stringResource(when {
        state.saveFailed -> R.string.save_failed
        state.saving -> R.string.draft_saving
        else -> R.string.draft_saved
    }), color = if (state.saveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium)
    if (state.saveFailed) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(state: CaptureState, model: CaptureViewModel, onEdit: (String) -> Unit, onBack: () -> Unit, onApprove: () -> Unit = model::approve) {
    val review = state.review ?: return
    DisposableEffect(Unit) { onDispose { model.dismissUndo() } }
    var discardReview by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val removedMessage = stringResource(R.string.proposal_removed)
    LaunchedEffect(state.removed) {
        if (state.removed != null) {
            val result = snackbar.showSnackbar(removedMessage, undoLabel, withDismissAction = true, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) model.undoRemoval() else model.dismissUndo()
        }
    }
    if (discardReview) AlertDialog(
        onDismissRequest = { discardReview = false },
        title = { Text(stringResource(R.string.discard_review_title)) },
        text = { Text(stringResource(R.string.discard_review_body)) },
        confirmButton = { TextButton(onClick = { discardReview = false; model.discardReview() }) {
            Text(stringResource(R.string.discard_proposals))
        } },
        dismissButton = { TextButton(onClick = { discardReview = false }) { Text(stringResource(R.string.keep_editing)) } }
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.review_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
        }) },
        bottomBar = { SnackbarHost(snackbar, if (snackbar.currentSnackbarData != null) Modifier.navigationBarsPadding() else Modifier) }
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).testTag("review_list"),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "introduction") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (review.isStub) Text(stringResource(R.string.placeholder_notice), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.review_not_approved), style = MaterialTheme.typography.bodyLarge)
                    DraftSaveStatus(state, model::retrySave)
                    Text(stringResource(R.string.original_input), style = MaterialTheme.typography.titleMedium)
                    Text(state.draft.input)
                }
            }
            itemsIndexed(review.warnings, key = { index, _ -> "warning-$index" }) { _, warning ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.check_interpretation), style = MaterialTheme.typography.titleSmall)
                        Text(warning)
                    }
                }
            }
            if (review.tasks.isEmpty()) item(key = "empty") { Text(stringResource(R.string.empty_review)) }
            itemsIndexed(review.tasks, key = { _, task -> task.id }) { index, task ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("proposal-${task.id}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${task.title.ifBlank { stringResource(R.string.untitled_task) }}", style = MaterialTheme.typography.titleLarge)
                        OriginLabel(task, ReviewField.TITLE)
                        if (task.notes.isNotEmpty()) { Text(task.notes); OriginLabel(task, ReviewField.NOTES) }
                        Text(task.dueDate?.let { stringResource(R.string.due_value, dateLabel(it)) } ?: stringResource(R.string.anytime))
                        if (task.dueDate != null || ReviewField.DUE_DATE in task.changedFields) OriginLabel(task, ReviewField.DUE_DATE)
                        Text(task.reminderAt?.let { stringResource(R.string.reminder_value, reminderLabel(it)) } ?: stringResource(R.string.no_reminder))
                        if (task.reminderAt != null || ReviewField.REMINDER in task.changedFields) OriginLabel(task, ReviewField.REMINDER)
                        if (task.title.isBlank() || task.title.length > 200 || task.notes.length > 5000) {
                            Text(stringResource(R.string.proposal_incomplete), color = MaterialTheme.colorScheme.error)
                        }
                        task.reason?.let { Text(stringResource(R.string.original_interpretation, it), style = MaterialTheme.typography.bodyMedium) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val editLabel = stringResource(R.string.edit_task_number, index + 1)
                            val removeLabel = stringResource(R.string.remove_task_number, index + 1)
                            TextButton(onClick = { onEdit(task.id) }, enabled = !state.approving, modifier = Modifier.semantics { contentDescription = editLabel }) {
                                Text(stringResource(R.string.edit_task))
                            }
                            TextButton(onClick = { model.removeProposal(task.id) }, enabled = !state.approving, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                                Text(stringResource(R.string.remove_task))
                            }
                            val upLabel = stringResource(R.string.move_task_up, index + 1)
                            val downLabel = stringResource(R.string.move_task_down, index + 1)
                            TextButton(onClick = { model.moveProposal(task.id, -1) }, enabled = !state.approving && index > 0,
                                modifier = Modifier.semantics { contentDescription = upLabel }) { Text(stringResource(R.string.move_up)) }
                            TextButton(onClick = { model.moveProposal(task.id, 1) }, enabled = !state.approving && index < review.tasks.lastIndex,
                                modifier = Modifier.semantics { contentDescription = downLabel }) { Text(stringResource(R.string.move_down)) }
                        }
                    }
                }
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.approvalError?.let { error ->
                        Text(stringResource(when (error) {
                            ApprovalError.WRITE -> R.string.approval_write_failed
                            ApprovalError.CHANGED -> R.string.approval_changed
                            ApprovalError.INVALID -> R.string.approval_invalid
                        }), color = MaterialTheme.colorScheme.error)
                    }
                    if (review.tasks.isNotEmpty() && state.approvalError != ApprovalError.INVALID && !ApprovalValidation.isValid(review)) {
                        Text(stringResource(R.string.approval_invalid), color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onApprove,
                        enabled = !state.approving && !state.saving && !state.saveFailed && ApprovalValidation.isValid(review),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionPurple, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.approving) stringResource(R.string.approving_tasks)
                            else pluralStringResource(R.plurals.approve_tasks, review.tasks.size, review.tasks.size))
                    }
                    if (review.tasks.any { it.reminderAt != null }) Text(stringResource(R.string.reminder_delivery_pending), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { model.addProposal()?.let(onEdit) }, enabled = !state.approving && review.tasks.size < 100,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_proposal)) }
                    TextButton(onClick = { discardReview = true }, enabled = !state.approving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_original_input)) }
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_draft_home)) }
                }
            }
        }
    }
}

@Composable
private fun OriginLabel(task: ReviewTask, field: ReviewField) {
    Text(stringResource(if (task.isSuggested(field)) R.string.suggested else R.string.your_choice),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalEditorScreen(task: ReviewTask, state: CaptureState, model: CaptureViewModel, onBack: () -> Unit) {
    var datePicker by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    var reminderDateForTime by rememberSaveable(task.id) { mutableStateOf<String?>(null) }
    val reminder = task.reminderAt?.let(LocalDateTime::parse)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissKeyboard() { focus.clearFocus(); keyboard?.hide() }
    if (datePicker != null) {
        val forReminder = datePicker == "reminder"
        CalendarDateDialog(
            initial = if (forReminder) reminder?.toLocalDate() else task.dueDate?.let(LocalDate::parse),
            onDismiss = { datePicker = null },
            onSet = { date ->
                if (forReminder) {
                    if (reminder == null) reminderDateForTime = date.toString()
                    else model.editProposal(task.id) { it.copy(reminderAt = LocalDateTime.of(date, reminder.toLocalTime()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), changedFields = it.changedFields + ReviewField.REMINDER) }
                } else model.editProposal(task.id) { it.editDueDate(date.toString()) }
                datePicker = null
            }
        )
    }
    if (reminderDateForTime != null) {
        ReminderTimeDialog(initial = reminder?.toLocalTime() ?: LocalTime.of(9, 0),
            onDismiss = { reminderDateForTime = null }, onSet = { time ->
                val value = LocalDateTime.of(LocalDate.parse(reminderDateForTime), time).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                model.editProposal(task.id) { it.copy(reminderAt = value, changedFields = it.changedFields + ReviewField.REMINDER) }
                reminderDateForTime = null
            })
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.edit_task_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
        }) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DraftSaveStatus(state, model::retrySave)
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done_editing)) }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.proposal_editor_notice))
            OutlinedTextField(value = task.title, onValueChange = { value -> model.editProposal(task.id) { it.editTitle(value) } },
                label = { Text(stringResource(R.string.task_title)) }, modifier = Modifier.fillMaxWidth(),
                isError = task.title.isBlank() || task.title.length > 200,
                supportingText = { Text(stringResource(R.string.title_limit, task.title.length)) })
            OriginLabel(task, ReviewField.TITLE)
            OutlinedTextField(value = task.notes, onValueChange = { value -> model.editProposal(task.id) { it.editNotes(value) } },
                label = { Text(stringResource(R.string.task_notes)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                isError = task.notes.length > 5000,
                supportingText = { Text(stringResource(R.string.notes_limit, task.notes.length)) })
            if (task.notes.isNotEmpty() || ReviewField.NOTES in task.changedFields) OriginLabel(task, ReviewField.NOTES)
            Text(stringResource(R.string.due_date_label), style = MaterialTheme.typography.titleMedium)
            Text(task.dueDate?.let(::dateLabel) ?: stringResource(R.string.anytime))
            if (task.dueDate != null || ReviewField.DUE_DATE in task.changedFields) OriginLabel(task, ReviewField.DUE_DATE)
            OutlinedButton(onClick = { dismissKeyboard(); datePicker = "due" }) { Text(stringResource(R.string.choose_due_date)) }
            if (task.dueDate != null) TextButton(onClick = { model.editProposal(task.id) { it.editDueDate(null) } }) { Text(stringResource(R.string.make_anytime)) }
            Text(stringResource(R.string.reminder_label), style = MaterialTheme.typography.titleMedium)
            Text(task.reminderAt?.let(::reminderLabel) ?: stringResource(R.string.no_reminder))
            if (task.reminderAt != null || ReviewField.REMINDER in task.changedFields) OriginLabel(task, ReviewField.REMINDER)
            if (reminder != null && !reminder.isAfter(LocalDateTime.now())) Text(stringResource(R.string.reminder_in_past), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { dismissKeyboard(); datePicker = "reminder" }) { Text(stringResource(if (reminder == null) R.string.set_reminder else R.string.change_reminder_date)) }
            if (reminder != null) {
                OutlinedButton(onClick = { dismissKeyboard(); reminderDateForTime = reminder.toLocalDate().toString() }) { Text(stringResource(R.string.change_reminder_time)) }
                TextButton(onClick = { model.editProposal(task.id) { it.copy(reminderAt = null, changedFields = it.changedFields + ReviewField.REMINDER) } }) { Text(stringResource(R.string.remove_reminder)) }
            }
            task.reason?.let { Text(stringResource(R.string.original_interpretation, it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarDateDialog(initial: LocalDate?, onDismiss: () -> Unit, onSet: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { onSet(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        }, enabled = state.selectedDateMillis != null) { Text(stringResource(R.string.set_date)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) {
        DatePicker(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onSet: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.reminder_time)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state) } },
        confirmButton = { TextButton(onClick = { onSet(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.set_time)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
