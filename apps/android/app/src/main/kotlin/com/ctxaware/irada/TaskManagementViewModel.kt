package com.ctxaware.irada

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ctxaware.irada.tasks.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single brief Undo reverses exactly the successful write that produced it. */
data class TaskChange(val before: SavedTask, val after: SavedTask?, val sequence: Long)
data class TaskManagementState(val busy: Boolean = false, val change: TaskChange? = null, val error: Boolean = false)

class TaskManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as IradaApplication).drafts
    private val mutable = MutableStateFlow(TaskManagementState())
    val state = mutable.asStateFlow()
    private var sequence = 0L

    fun change(before: SavedTask, after: SavedTask?, onSuccess: () -> Unit = {}) {
        if (mutable.value.busy) return
        if (after != null && !validTaskEdit(before, after)) {
            mutable.value = mutable.value.copy(error = true)
            return
        }
        mutable.value = TaskManagementState(busy = true)
        viewModelScope.launch {
            try {
                val saved = after?.copy(updatedAt = maxOf(System.currentTimeMillis(), before.updatedAt + 1))
                store.changeTask(before, saved)
                mutable.value = TaskManagementState(change = TaskChange(before, saved, ++sequence))
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = TaskManagementState(error = true) }
        }
    }

    fun toggle(task: SavedTask) = change(task, task.copy(status = if (task.status == "open") "completed" else "open"))
    fun dismiss(sequence: Long) {
        if (mutable.value.change?.sequence == sequence) mutable.value = mutable.value.copy(change = null)
    }
    fun clearError() { mutable.value = mutable.value.copy(error = false) }
    fun undo(change: TaskChange) {
        if (mutable.value.busy || mutable.value.change != change) return
        mutable.value = TaskManagementState(busy = true)
        viewModelScope.launch {
            try {
                store.changeTask(change.after, change.before)
                mutable.value = TaskManagementState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = TaskManagementState(error = true) }
        }
    }
}
