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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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
    LaunchedEffect(state.approvedBatchId) {
        if (state.approvedBatchId != null) {
            choosingDraft = false
            backStack.clear()
            backStack.add(Home)
            model.acknowledgeApproval()
        }
    }
    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    fun capture() {
        if (state.approving) return
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
                    state = state, onCapture = ::capture,
                    onSettings = { backStack.add(Settings) },
                    onResume = { if (!state.approving) backStack.add(Capture) },
                    onRetryTasks = model::loadTasks
                )
            }
            entry<Capture> {
                val preview by model.preview.collectAsStateWithLifecycle()
                var selectedTask by rememberSaveable(state.draft.batchId) { mutableStateOf<String?>(null) }
                val editingTask = state.review?.tasks?.firstOrNull { it.id == selectedTask }
                val hasReview = state.review != null
                BackHandler(enabled = selectedTask != null || hasReview || preview != PreviewState.Editing) {
                    when {
                        selectedTask != null -> selectedTask = null
                        hasReview -> back()
                        else -> model.cancelPreview()
                    }
                }
                when {
                    editingTask != null -> ProposalEditorScreen(editingTask, state, model, onBack = { selectedTask = null })
                    hasReview -> ReviewScreen(state, model, onEdit = { selectedTask = it }, onBack = ::back)
                    else -> when (val current = preview) {
                        PreviewState.Editing -> CaptureScreen(state, model::edit, model::retrySave, model::generatePreview, ::back)
                        PreviewState.Processing -> ProcessingScreen(model::cancelPreview)
                        is PreviewState.Failed -> PreviewErrorScreen(current.problem, model::generatePreview, model::cancelPreview)
                        is PreviewState.Ready -> ProcessingScreen(model::cancelPreview)
                    }
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
