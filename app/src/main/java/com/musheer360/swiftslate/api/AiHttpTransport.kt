package com.musheer360.swiftslate.api

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A bounded HTTP response, with no open sockets or credentials exposed to the UI. */
data class AiHttpResponse(val code: Int, val body: String, val headers: Headers) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Shared connection pool for all AI and OAuth traffic. Cancelling a coroutine cancels its
 * socket, including response-body reads. POST requests are never implicitly replayed.
 */
class AiHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {
    suspend fun execute(request: Request): AiHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val output = ByteArrayOutputStream()
                        it.body.byteStream().use { stream ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count == -1) break
                                if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("response_too_large")
                                output.write(buffer, 0, count)
                            }
                        }
                        AiHttpResponse(it.code, output.toString(Charsets.UTF_8.name()), it.headers)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
