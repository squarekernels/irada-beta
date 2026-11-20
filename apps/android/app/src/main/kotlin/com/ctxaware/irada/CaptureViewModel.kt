package com.ctxaware.irada

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ctxaware.irada.data.PendingDraft
import com.ctxaware.irada.preview.*
import java.util.Locale
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureState(
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val introduced: Boolean = false,
    val draft: PendingDraft = PendingDraft(),
    val revision: Long = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val introducing: Boolean = false
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as IradaApplication).drafts
    private val _state = MutableStateFlow(CaptureState())
    val state = _state.asStateFlow()
    private val previews = PreviewController(viewModelScope, (application as IradaApplication).previewService) { input ->
        PreviewRequest(input, Locale.getDefault().toLanguageTag(), ZoneId.systemDefault().id)
    }
    val preview = previews.state

    fun generatePreview() {
        val snapshot = _state.value
        if (!snapshot.loaded || snapshot.draft.input.isBlank() || snapshot.draft.input.length > 10000) return
        previews.generate(snapshot.draft.input) {
            // Re-save the snapshot and wait for Room before any network request.
            val revision = persist(snapshot.draft)
            if (!store.awaitSaved(revision)) throw DraftSaveFailed()
        }
    }

    fun cancelPreview() = previews.cancel()

    init {
        load()
        viewModelScope.launch {
            store.result.collect { result ->
                _state.update {
                    if (result.revision == it.revision)
                        it.copy(saving = false, saveFailed = result.failed)
                    else it
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loadFailed = false) }
        viewModelScope.launch {
            try {
                val (introduced, draft) = store.load()
                val revision = store.currentRevision
                val result = store.result.value
                _state.update { it.copy(loaded = true, introduced = introduced, draft = draft,
                    revision = revision, saving = result.revision < revision,
                    saveFailed = result.revision == revision && result.failed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loadFailed = true) }
            }
        }
    }

    fun finishIntroduction(onComplete: () -> Unit) {
        if (_state.value.introducing) return
        _state.update { it.copy(introducing = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                store.finishIntroduction()
                _state.update { it.copy(introducing = false, introduced = true) }
                onComplete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(introducing = false, saveFailed = true) }
            }
        }
    }

    fun edit(input: String) { previews.cancel(); persist(_state.value.draft.copy(input = input)) }
    fun discard() { previews.cancel(); persist(PendingDraft()) }
    fun retrySave() { persist(_state.value.draft) }

    private fun persist(draft: PendingDraft): Long {
        val revision = store.nextRevision()
        _state.update { it.copy(draft = draft, revision = revision, saving = true, saveFailed = false) }
        store.save(revision, draft)
        return revision
    }
}
