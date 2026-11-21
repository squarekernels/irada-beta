package com.ctxaware.irada

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.Manifest
import android.content.Intent
import android.provider.Settings as AndroidSettings
import com.ctxaware.irada.reminders.DeliveryStatus
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
@Serializable private data class TaskDetails(val id: String) : NavKey
@Serializable private data object Settings : NavKey

@Composable
fun IradaApp(model: CaptureViewModel = viewModel()) {
    val context = LocalContext.current
    val reminders = (context.applicationContext as IradaApplication).reminders
    val delivery by reminders.status.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { reminders.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reminders.refresh() }
    fun requestReminderPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && reminders.claimPermissionRequest()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val management: TaskManagementViewModel = viewModel()
    val managementState by management.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.undo)
    val changedLabel = stringResource(R.string.task_changed)
    val deletedLabel = stringResource(R.string.task_deleted)
    val errorLabel = stringResource(R.string.task_change_failed)
    LaunchedEffect(managementState.change, managementState.error) {
        val change = managementState.change
        if (managementState.error) {
            snackbar.showSnackbar(errorLabel, withDismissAction = true)
            management.clearError()
        } else if (change != null) {
            val result = snackbar.showSnackbar(if (change.after == null) deletedLabel else changedLabel,
                undoLabel, withDismissAction = true, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) management.undo(change) else management.dismiss(change.sequence)
        }
    }
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
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
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
                                onRetryTasks = model::loadTasks,
                                onOpenTask = { backStack.add(TaskDetails(it)) },
                                onToggleTask = management::toggle, taskBusy = managementState.busy
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
                                hasReview -> ReviewScreen(state, model, onEdit = { selectedTask = it }, onBack = ::back, onApprove = {
                                    if (state.review?.tasks?.any { it.reminderAt != null } == true) requestReminderPermission()
                                    model.approve()
                                })
                                else -> when (val current = preview) {
                                    PreviewState.Editing -> CaptureScreen(state, model::edit, model::retrySave, model::generatePreview, ::back)
                                    PreviewState.Processing -> ProcessingScreen(model::cancelPreview)
                                    is PreviewState.Failed -> PreviewErrorScreen(current.problem, model::generatePreview, model::cancelPreview)
                                    is PreviewState.Ready -> ProcessingScreen(model::cancelPreview)
                                }
                            }
                        }
                        entry<TaskDetails> { route ->
                            TaskDetailScreen(state.tasks.firstOrNull { it.id == route.id }, managementState.busy,
                                onSave = { before, after ->
                                    if (after.reminderAt != null && after.reminderAt != before.reminderAt) requestReminderPermission()
                                    management.change(before, after, ::back)
                                },
                                onToggle = management::toggle,
                                onDelete = { management.change(it, null, ::back) }, onBack = ::back)
                        }
                        entry<Settings> {
                            ShellScreen(R.string.settings, R.string.settings_pending, ::back, delivery)
                        }
                    }
                )
            }
            if (backStack.lastOrNull() == Home && state.tasks.any { it.status == "open" && it.reminderAt != null } && delivery != DeliveryStatus.ENABLED) {
                Box(Modifier.navigationBarsPadding()) { ReminderStatus(delivery) }
            }
            // Reserve the host's measured height so brief feedback never covers task actions.
            SnackbarHost(snackbar, if (snackbar.currentSnackbarData != null) Modifier.navigationBarsPadding() else Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellScreen(title: Int, message: Int, onBack: () -> Unit, delivery: DeliveryStatus) {
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
            ReminderStatus(delivery)
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onBack) { Text(stringResource(R.string.return_home)) }
        }
    }
}

@Composable
private fun ReminderStatus(status: DeliveryStatus) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(stringResource(when (status) {
            DeliveryStatus.ENABLED -> R.string.reminders_enabled
            DeliveryStatus.DISABLED -> R.string.reminders_disabled
            DeliveryStatus.ERROR -> R.string.reminders_failed
        }), style = MaterialTheme.typography.bodyMedium)
        if (status == DeliveryStatus.ERROR) TextButton(onClick = {
            (context.applicationContext as IradaApplication).reminders.refresh()
        }) { Text(stringResource(R.string.retry)) }
        if (status == DeliveryStatus.DISABLED) TextButton(onClick = {
            context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text(stringResource(R.string.notification_settings)) }
    }
}
