package com.ctxaware.irada.preview

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PreviewState {
    data object Editing : PreviewState
    data object Processing : PreviewState
    data class Ready(val preview: PlanPreview) : PreviewState
    data class Failed(val problem: Problem) : PreviewState
}
enum class Problem { CONNECTION, INVALID, SAVE, CONFIGURATION }
class DraftSaveFailed : Exception()

/** One request at a time. IDs reject late results even from non-cooperative services. */
class PreviewController(
    private val scope: CoroutineScope,
    private val service: PreviewService,
    private val context: (String) -> PreviewRequest
) {
    private var generation = 0L
    private var job: Job? = null
    private val _state = MutableStateFlow<PreviewState>(PreviewState.Editing)
    val state = _state.asStateFlow()

    fun generate(input: String, save: suspend () -> Unit) {
        if (_state.value == PreviewState.Processing) return
        val id = ++generation
        _state.value = PreviewState.Processing
        job = scope.launch {
            try {
                save()
                ensureActive()
                val preview = service.generate(context(input))
                if (id == generation) _state.value = PreviewState.Ready(preview)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (id == generation) _state.value = PreviewState.Failed(when (error) {
                    is DraftSaveFailed -> Problem.SAVE
                    is InvalidPreview -> Problem.INVALID
                    is PreviewNotConfigured -> Problem.CONFIGURATION
                    else -> Problem.CONNECTION
                })
            }
        }
    }

    fun cancel() {
        ++generation
        job?.cancel()
        job = null
        _state.value = PreviewState.Editing
    }
}
