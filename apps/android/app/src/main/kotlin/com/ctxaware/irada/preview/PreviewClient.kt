package com.ctxaware.irada.preview

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface PreviewService {
    suspend fun generate(request: PreviewRequest): PlanPreview
}

class PreviewNotConfigured : Exception()

class PreviewClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) : PreviewService {
    override suspend fun generate(request: PreviewRequest): PlanPreview {
        if (baseUrl.isBlank()) throw PreviewNotConfigured()
        val httpRequest = Request.Builder().url(baseUrl.trimEnd('/') + "/api/plan-preview")
            .post(PreviewCodec.encode(request).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (!it.isSuccessful) throw IOException("Preview service returned ${it.code}")
                            val body = it.body ?: throw InvalidPreview()
                            // Bound the response before parsing, including chunked responses.
                            if (body.contentLength() > 1_000_000) throw InvalidPreview()
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            val stream = body.byteStream()
                            while (true) {
                                val count = stream.read(buffer)
                                if (count == -1) break
                                if (output.size() + count > 1_000_000) throw InvalidPreview()
                                output.write(buffer, 0, count)
                            }
                            val bytes = output.toByteArray()
                            PreviewCodec.decode(bytes.toString(Charsets.UTF_8), request.input)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}
