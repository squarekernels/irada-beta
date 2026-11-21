package com.ctxaware.irada

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ctxaware.irada.data.PendingDraft
import com.ctxaware.irada.preview.*
import com.ctxaware.irada.review.*
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
    val introducing: Boolean = false,
    val review: ReviewDraft? = null,
    val removed: RemovedProposal? = null
)

data class RemovedProposal(val task: ReviewTask, val index: Int)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as IradaApplication).drafts
    private val _state = MutableStateFlow(CaptureState())
    val state = _state.asStateFlow()
    private val previews = PreviewController(
        viewModelScope,
        (application as IradaApplication).previewService,
        context = { input -> PreviewRequest(input, Locale.getDefault().toLanguageTag(), ZoneId.systemDefault().id) },
        onPreview = { proposal -> persistReview(ReviewDraft.from(proposal)) }
    )
    val preview = previews.state

    fun generatePreview() {
        val snapshot = _state.value
        if (!snapshot.loaded || snapshot.review != null || snapshot.draft.input.isBlank() || snapshot.draft.input.length > 10000) return
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
                val review = ReviewCodec.decode(draft.reviewJson)
                val revision = store.currentRevision
                val result = store.result.value
                _state.update { it.copy(loaded = true, introduced = introduced, draft = draft,
                    revision = revision, review = review, saving = result.revision < revision,
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

    fun edit(input: String) {
        if (_state.value.review != null) return
        previews.cancel()
        persist(_state.value.draft.copy(input = input))
    }
    fun discard() { previews.cancel(); persist(PendingDraft()) }
    fun retrySave() { persist(_state.value.draft) }

    fun editProposal(id: String, edit: (ReviewTask) -> ReviewTask) {
        _state.value.review?.let { persistReview(it.update(id, edit)) }
    }

    fun addProposal(): String? {
        val review = _state.value.review ?: return null
        if (review.tasks.size >= 100) return null
        val task = ReviewTask()
        dismissUndo()
        persistReview(review.add(task))
        return task.id
    }

    fun removeProposal(id: String) {
        val review = _state.value.review ?: return
        val index = review.tasks.indexOfFirst { it.id == id }
        if (index < 0) return
        persistReview(review.remove(id))
        _state.update { it.copy(removed = RemovedProposal(review.tasks[index], index)) }
    }

    fun undoRemoval() {
        val removed = _state.value.removed ?: return
        _state.value.review?.let { persistReview(it.insert(removed.index, removed.task)) }
        dismissUndo()
    }

    fun dismissUndo() { _state.update { it.copy(removed = null) } }

    fun moveProposal(id: String, delta: Int) {
        _state.value.review?.let { persistReview(it.move(id, delta)) }
    }

    // Called only after explicit confirmation. Original words and batch identity stay intact.
    fun discardReview() {
        previews.cancel()
        persist(_state.value.draft.copy(reviewJson = null))
    }

    private fun persistReview(review: ReviewDraft) {
        persist(_state.value.draft.copy(reviewJson = ReviewCodec.encode(review)))
    }

    private fun persist(draft: PendingDraft): Long {
        val revision = store.nextRevision()
        _state.update { it.copy(draft = draft, review = ReviewCodec.decode(draft.reviewJson),
            removed = if (draft.reviewJson == null) null else it.removed,
            revision = revision, saving = true, saveFailed = false) }
        store.save(revision, draft)
        return revision
    }
}
