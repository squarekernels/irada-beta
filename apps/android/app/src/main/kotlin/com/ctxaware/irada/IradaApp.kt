package com.ctxaware.irada

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import com.ctxaware.irada.preview.PreviewState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ctxaware.irada.ui.theme.ActionPurple
import kotlinx.serialization.Serializable

@Serializable private data object Introduction : NavKey
@Serializable private data object Home : NavKey
@Serializable private data object Capture : NavKey
@Serializable private data object Settings : NavKey

@Composable
fun IradaApp(model: CaptureViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    if (!state.loaded) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.loadFailed) {
                Text(stringResource(R.string.load_failed))
                TextButton(onClick = model::load) { Text(stringResource(R.string.retry)) }
            } else CircularProgressIndicator()
        }
        return
    }
    val backStack = rememberNavBackStack(if (state.introduced) Home else Introduction)
    var choosingDraft by rememberSaveable { mutableStateOf(false) }
    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    fun capture() {
        if (state.draft.input.isNotEmpty()) choosingDraft = true
        else backStack.add(Capture)
    }
    if (choosingDraft) {
        AlertDialog(
            onDismissRequest = { choosingDraft = false },
            title = { Text(stringResource(R.string.draft_title)) },
            text = { Text(stringResource(R.string.draft_question)) },
            confirmButton = {
                TextButton(onClick = { choosingDraft = false; backStack.add(Capture) }) {
                    Text(stringResource(R.string.resume_draft))
                }
            },
            dismissButton = {
                TextButton(onClick = { model.discard(); choosingDraft = false; backStack.add(Capture) }) {
                    Text(stringResource(R.string.discard_start))
                }
            }
        )
    }
    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        entryProvider = entryProvider {
            entry<Introduction> {
                IntroductionScreen(state, onStart = {
                    model.finishIntroduction {
                        backStack.clear(); backStack.add(Home); backStack.add(Capture)
                    }
                }, onHome = {
                    model.finishIntroduction { backStack.clear(); backStack.add(Home) }
                })
            }
            entry<Home> {
                HomeScreen(
                    onCapture = ::capture,
                    onSettings = { backStack.add(Settings) },
                    hasDraft = state.draft.input.isNotEmpty(),
                    onResume = { backStack.add(Capture) }
                )
            }
            entry<Capture> {
                val preview by model.preview.collectAsStateWithLifecycle()
                BackHandler(enabled = preview != PreviewState.Editing) { model.cancelPreview() }
                when (val current = preview) {
                    PreviewState.Editing -> CaptureScreen(state, model::edit, model::retrySave,
                        model::generatePreview, ::back)
                    PreviewState.Processing -> ProcessingScreen(model::cancelPreview)
                    is PreviewState.Failed -> PreviewErrorScreen(current.problem,
                        model::generatePreview, model::cancelPreview)
                    is PreviewState.Ready -> PlanPreviewScreen(current.preview, model::cancelPreview)
                }
            }
            entry<Settings> {
                ShellScreen(R.string.settings, R.string.settings_pending, ::back)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onCapture: () -> Unit, onSettings: () -> Unit, hasDraft: Boolean, onResume: () -> Unit) {
    val captureLabel = stringResource(R.string.add_tasks)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_irada), null,
                            Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCapture,
                modifier = Modifier.semantics { contentDescription = captureLabel },
                containerColor = ActionPurple,
                contentColor = Color.White,
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text(stringResource(R.string.add_tasks)) }
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.widthIn(max = 480.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    if (hasDraft) {
                        OutlinedButton(onClick = onResume) { Text(stringResource(R.string.resume_draft)) }
                    }
                    Text(stringResource(R.string.empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellScreen(title: Int, message: Int, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                }
            )
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onBack) { Text(stringResource(R.string.return_home)) }
        }
    }
}
