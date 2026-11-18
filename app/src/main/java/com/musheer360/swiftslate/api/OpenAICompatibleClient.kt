package com.musheer360.swiftslate.api

import com.musheer360.swiftslate.domain.EndpointValidation
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Cancellable OpenAI chat-completions client. Negotiates structured output only when the
 * server explicitly rejects that parameter; never retries a possibly accepted generation.
 */
class OpenAICompatibleClient(private val http: AiHttpTransport = AiHttpTransport()) {
    private val formats = ConcurrentHashMap<String, Int>()
    private val defaultTemperatureOnly: MutableSet<String> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    suspend fun validateKey(apiKey: String, endpoint: String): Result<String> =
        fetchModels(apiKey, endpoint).map { "Valid" }

    suspend fun fetchModels(apiKey: String, endpoint: String): Result<List<String>> = result {
        val response = http.execute(request(endpoint, "models", apiKey).get().build())
        if (!response.isSuccessful) throw OpenAiProtocol.exception(response)
        OpenAiProtocol.modelIds(response.body)
    }

    /** Generates transformed text while preserving cancellation, refusals, and truncation errors. */
    suspend fun generate(
        prompt: String, text: String, apiKey: String, model: String,
        temperature: Double, endpoint: String
    ): Result<GenerateResult> = result {
        require(model.isNotBlank() && apiKey.isNotBlank())
        val cacheKey = "$endpoint|$model"
        var format = formats[cacheKey] ?: 2
        var includeTemperature = cacheKey !in defaultTemperatureOnly
        var response: AiHttpResponse
        do {
            val body = OpenAiProtocol.request(prompt, text, model, temperature, format)
            if (!includeTemperature) body.remove("temperature")
            response = http.execute(request(endpoint, "chat/completions", apiKey)
                .post(body.toString().toRequestBody(JSON)).build())
            if (!response.isSuccessful && format > 0 && OpenAiProtocol.unsupportedFormat(response)) format--
            else if (!response.isSuccessful && includeTemperature && OpenAiProtocol.unsupportedTemperature(response)) includeTemperature = false
            else break
        } while (true)
        if (!response.isSuccessful) throw OpenAiProtocol.exception(response)
        if (formats.size > 128) formats.clear()
        formats[cacheKey] = format
        if (!includeTemperature) {
            if (defaultTemperatureOnly.size > 128) defaultTemperatureOnly.clear()
            defaultTemperatureOnly.add(cacheKey)
        }
        GenerateResult(OpenAiProtocol.text(response.body))
    }

    private fun request(endpoint: String, path: String, key: String): Request.Builder {
        require(EndpointValidation.isSafeToSave(endpoint) && endpoint.isNotBlank())
        val url = (endpoint.trimEnd('/') + "/").toHttpUrl().newBuilder().addPathSegments(path).build()
        require(url.username.isEmpty() && url.password.isEmpty())
        return Request.Builder().url(url).header("Authorization", "Bearer $key")
            .header("Accept", "application/json").header("User-Agent", "SwiftSlate")
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Unlike runCatching, request failures must never consume a coroutine cancellation. */
internal suspend fun <T> result(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
