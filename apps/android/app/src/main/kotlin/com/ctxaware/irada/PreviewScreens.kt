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
import com.ctxaware.irada.preview.Problem

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
