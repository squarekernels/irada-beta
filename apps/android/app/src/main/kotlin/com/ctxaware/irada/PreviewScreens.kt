package com.ctxaware.irada

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ctxaware.irada.preview.PlanPreview
import com.ctxaware.irada.preview.Problem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewFrame(title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.edit_input))
            }
        })
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
fun ProcessingScreen(onCancel: () -> Unit) {
    PreviewFrame(R.string.processing_title, onCancel) {
        CircularProgressIndicator()
        Text(stringResource(R.string.processing_body), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
fun PreviewErrorScreen(problem: Problem, onRetry: () -> Unit, onEdit: () -> Unit) {
    PreviewFrame(R.string.preview_failed, onEdit) {
        Text(stringResource(when (problem) {
            Problem.CONNECTION -> R.string.needs_internet
            Problem.INVALID -> R.string.invalid_preview
            Problem.SAVE -> R.string.save_before_preview_failed
            Problem.CONFIGURATION -> R.string.preview_not_configured
        }), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.input_safe))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_input)) }
    }
}

@Composable
fun PlanPreviewScreen(preview: PlanPreview, onEdit: () -> Unit) {
    PreviewFrame(R.string.preview_title, onEdit) {
        if (preview.isStub) Text(stringResource(R.string.placeholder_notice), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.preview_not_approved), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.original_input), style = MaterialTheme.typography.titleMedium)
        Text(preview.input)
        preview.warnings.forEach { warning ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.check_interpretation), style = MaterialTheme.typography.titleSmall)
                    Text(warning)
                }
            }
        }
        preview.tasks.forEachIndexed { index, task ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${task.title}", style = MaterialTheme.typography.titleLarge)
                    if (task.suggested) Text(stringResource(R.string.suggested), style = MaterialTheme.typography.labelLarge)
                    task.notes?.let { Text(it) }
                    Text(task.dueDate?.let {
                        stringResource(R.string.due_value, LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    } ?: stringResource(R.string.anytime))
                    Text(task.reminderAt?.let {
                        stringResource(R.string.reminder_value, LocalDateTime.parse(it).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)))
                    } ?: stringResource(R.string.no_reminder))
                    task.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_input)) }
    }
}
