package com.ctxaware.irada.preview

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewControllerTest {
    private fun preview(input: String) = PlanPreview(input,
        listOf(ProposedTask("Buy groceries", null, null, null, true, null)), true, "Stub")
    private fun request(input: String) = PreviewRequest(input, "en-PK", "Asia/Karachi")

    @Test fun waitsForDurableSaveAndIgnoresDuplicateSubmission() = runTest {
        val saved = CompletableDeferred<Unit>()
        var calls = 0
        val controller = PreviewController(backgroundScope, PreviewService { calls++; preview(it.input) }, ::request)
        controller.generate("Buy groceries") { saved.await() }
        controller.generate("Duplicate") { error("must not run") }
        runCurrent()
        assertEquals(0, calls)
        assertEquals(PreviewState.Processing, controller.state.value)
        saved.complete(Unit)
        runCurrent()
        assertEquals(1, calls)
        assertEquals("Buy groceries", (controller.state.value as PreviewState.Ready).preview.input)
    }

    @Test fun failedSaveSendsNothingAndRetryIsExplicit() = runTest {
        var calls = 0
        val controller = PreviewController(backgroundScope, PreviewService { calls++; preview(it.input) }, ::request)
        controller.generate("Buy groceries") { throw DraftSaveFailed() }
        runCurrent()
        assertEquals(PreviewState.Failed(Problem.SAVE), controller.state.value)
        advanceTimeBy(60_000)
        assertEquals(0, calls)
        controller.generate("Buy groceries") {}
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun cancellationWhileSavingPreventsSend() = runTest {
        var calls = 0
        val controller = PreviewController(backgroundScope, PreviewService { calls++; preview(it.input) }, ::request)
        controller.generate("Buy groceries") { delay(1000) }
        runCurrent()
        controller.cancel()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(PreviewState.Editing, controller.state.value)
        assertEquals(0, calls)
    }

    @Test fun lateNonCooperativeResponseCannotReplaceNewerRequest() = runTest {
        lateinit var old: Continuation<PlanPreview>
        val persisted = mutableListOf<String>()
        val controller = PreviewController(backgroundScope, PreviewService {
            if (it.input == "Old") suspendCoroutine { continuation -> old = continuation }
            else preview(it.input)
        }, ::request, onPreview = { persisted += it.input })
        controller.generate("Old") {}
        runCurrent()
        controller.cancel()
        controller.generate("New") {}
        runCurrent()
        old.resume(preview("Old"))
        runCurrent()
        assertEquals("New", (controller.state.value as PreviewState.Ready).preview.input)
        assertEquals(listOf("New"), persisted)
    }

    @Test fun connectivityRecoveryDoesNotRetryAndRetryRefreshesTimeZone() = runTest {
        var zone = "Asia/Karachi"
        var available = false
        val requests = mutableListOf<PreviewRequest>()
        val controller = PreviewController(backgroundScope, PreviewService {
            requests += it
            if (!available) throw java.io.IOException()
            preview(it.input)
        }, context = { PreviewRequest(it, "en", zone) })
        controller.generate("Buy groceries tomorrow") {}
        runCurrent()
        assertEquals(PreviewState.Failed(Problem.CONNECTION), controller.state.value)
        available = true
        zone = "America/Los_Angeles"
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, requests.size)
        controller.generate("Buy groceries tomorrow") {}
        runCurrent()
        assertEquals("America/Los_Angeles", requests.last().timeZone)
        assertTrue(controller.state.value is PreviewState.Ready)
    }
}
