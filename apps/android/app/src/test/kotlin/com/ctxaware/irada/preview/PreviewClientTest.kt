package com.ctxaware.irada.preview

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PreviewClientTest {
    private val request = PreviewRequest("Buy groceries", "en-PK", "Asia/Karachi")
    private val valid = """{"input":"Buy groceries","tasks":[{"title":"Buy groceries","notes":null,"dueDate":null,"reminderAt":null,"suggested":true,"reason":null}],"isStub":true,"message":"Stub","warnings":[]}"""

    @Test fun sendsContextAndParsesUndatedProposal() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(valid))
        server.start()
        try {
            val preview = PreviewClient(server.url("/").toString()).generate(request)
            assertNull(preview.tasks.single().dueDate)
            assertNull(preview.tasks.single().reminderAt)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/plan-preview", recorded.path)
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("Asia/Karachi"))
            assertTrue(body.contains("en-PK"))
            assertTrue(body.contains("09:00"))
        } finally { server.shutdown() }
    }

    @Test fun rejectsMismatchedEmptyAndInvalidDateResponses() {
        for (body in listOf("{}", valid.replace("Buy groceries", "Other"),
            valid.replace("\"dueDate\":null", "\"dueDate\":\"2026-02-30\""),
            valid.replace("\"reminderAt\":null", "\"reminderAt\":\"2026-11-21T25:00:00\""),
            valid.replace("\"title\":\"Buy groceries\"", "\"title\":\" \""))) {
            try { PreviewCodec.decode(body, request.input); fail("Accepted invalid response") }
            catch (_: InvalidPreview) { }
        }
    }

    @Test fun serviceFailureDoesNotRetryAutomatically() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        try {
            try { PreviewClient(server.url("/").toString()).generate(request); fail() }
            catch (_: java.io.IOException) { }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun cancellingSlowCallReturnsWithoutAcceptingItsResponse() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody(valid))
        server.start()
        try {
            val client = PreviewClient(server.url("/").toString())
            val job = async { client.generate(request) }
            withContext(Dispatchers.IO) { checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun callTimeoutIsReportedAsFailure() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeadersDelay(500, TimeUnit.MILLISECONDS).setBody(valid))
        server.start()
        try {
            val http = OkHttpClient.Builder().callTimeout(100, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
            try { PreviewClient(server.url("/").toString(), http).generate(request); fail() }
            catch (_: java.io.IOException) { }
        } finally { server.shutdown() }
    }
}
