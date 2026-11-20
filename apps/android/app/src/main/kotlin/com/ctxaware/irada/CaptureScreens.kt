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

@Composable
fun IntroductionScreen(state: CaptureState, onStart: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(painterResource(R.drawable.ic_irada), null, Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.intro_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.intro_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.intro_privacy), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.intro_internet), style = MaterialTheme.typography.bodyLarge)
        if (state.saveFailed) Text(stringResource(R.string.intro_failed), color = MaterialTheme.colorScheme.error)
        Button(onClick = onStart, enabled = !state.introducing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_capture))
        }
        OutlinedButton(onClick = onHome, enabled = !state.introducing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.go_home))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(state: CaptureState, onEdit: (String) -> Unit, onRetry: () -> Unit, onGenerate: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                }
            )
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.capture_body), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = state.draft.input,
                onValueChange = onEdit,
                label = { Text(stringResource(R.string.capture_label)) },
                placeholder = { Text(stringResource(R.string.capture_example)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(when {
                state.saveFailed -> R.string.save_failed
                state.saving -> R.string.draft_saving
                state.revision == 0L && state.draft.input.isEmpty() -> R.string.draft_autosave
                else -> R.string.draft_saved
            }), color = if (state.saveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.saveFailed) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (state.draft.input.length > 10000) {
                Text(stringResource(R.string.input_too_long), color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onGenerate,
                enabled = state.draft.input.isNotBlank() && state.draft.input.length <= 10000 && !state.saving && !state.saveFailed,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.make_plan))
            }
            Text(stringResource(R.string.capture_preview_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.return_home))
            }
        }
    }
}
