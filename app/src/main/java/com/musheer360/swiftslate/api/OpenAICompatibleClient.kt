package com.musheer360.swiftslate.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Unified client for any OpenAI-compatible chat-completions API.
 *
 * Handles Gemini (via its OpenAI-compat layer), Groq, and arbitrary
 * custom endpoints through a single code path. Always requests
 * structured output with `strict: true` for reliable JSON adherence.
 */
class OpenAICompatibleClient {

    /**
     * Validates an API key by hitting the provider's `/models` endpoint.
     *
     * @param apiKey Bearer token to validate.
     * @param endpoint Base URL of the provider (e.g. `https://api.groq.com/openai/v1`).
     * @return [Result.success] with `"Valid"` or [Result.failure] with a user-facing message.
     */
    suspend fun validateKey(
        apiKey: String,
        endpoint: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val baseUrl = endpoint.trimEnd('/')
            connection = URL("$baseUrl/models")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("User-Agent", "SwiftSlate")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                // Drain the response body to free the connection
                connection.inputStream?.use { stream ->
                    val buf = ByteArray(1024)
                    while (stream.read(buf) != -1) { /* drain */ }
                }
                Result.success("Valid")
            } else {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val apiMessage = ApiClientUtils.extractApiErrorMessage(errorBody)

                when (responseCode) {
                    429 -> Result.failure(Exception("Rate limited. Please try again later."))
                    401, 403 -> {
                        val detail = if (apiMessage.isNotEmpty()) apiMessage else "Invalid API key"
                        Result.failure(Exception(detail))
                    }
                    else -> {
                        val detail = if (apiMessage.isNotEmpty()) apiMessage else "Unexpected error"
                        Result.failure(Exception("Error $responseCode: $detail"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Sends a text-transformation request to the chat-completions endpoint.
     *
     * Uses `response_format: json_schema` with `strict: true` so the model
     * returns a guaranteed `{"text": "..."}` object. Falls back to raw text
     * extraction if JSON parsing fails (e.g. on providers without schema support).
     *
     * Retries once on transient network errors (timeout, DNS, connection refused).
     *
     * @param prompt The transformation instruction (appended to the system prompt).
     * @param text The user's input text to transform.
     * @param apiKey Bearer token for the provider.
     * @param model Model identifier (e.g. `gemini-2.5-flash-lite`, `llama-3.3-70b-versatile`).
     * @param temperature Sampling temperature.
     * @param endpoint Base URL of the provider.
     * @return [GenerateResult] on success, or a descriptive failure.
     */
    suspend fun generate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        endpoint: String
    ): Result<GenerateResult> = withContext(Dispatchers.IO) {
        var result = doGenerate(prompt, text, apiKey, model, temperature, endpoint)

        // Retry once for transient errors (network failures + 5xx server errors)
        if (result.isFailure && result.exceptionOrNull().isTransient()) {
            kotlinx.coroutines.delay(1000)
            result = doGenerate(prompt, text, apiKey, model, temperature, endpoint)
        }

        result.map { GenerateResult(it) }
    }

    /**
     * Executes a single chat-completions request with structured output.
     *
     * @param prompt Transformation instruction.
     * @param text Input text to transform.
     * @param apiKey Bearer authentication token.
     * @param model Provider model identifier.
     * @param temperature Sampling temperature (0.0–2.0).
     * @param endpoint Provider base URL.
     * @return The extracted text on success, or a typed failure.
     */
    private fun doGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        endpoint: String
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val baseUrl = endpoint.trimEnd('/')
            connection = URL("$baseUrl/chat/completions")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("User-Agent", "SwiftSlate")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            val jsonBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", ApiClientUtils.SYSTEM_PROMPT_PREFIX + prompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "---BEGIN TEXT---\n$text\n---END TEXT---")
                    })
                })
                put("temperature", temperature)

                // Structured output with strict schema enforcement
                put("response_format", JSONObject().apply {
                    put("type", "json_schema")
                    put("json_schema", JSONObject().apply {
                        put("name", "text_output")
                        put("strict", true)
                        put("schema", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                put("text", JSONObject().apply {
                                    put("type", "string")
                                })
                            })
                            put("required", JSONArray().apply { put("text") })
                            put("additionalProperties", false)
                        })
                    })
                })
            }

            connection.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val response = ApiClientUtils.readResponseBounded(connection)

                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)

                    val finishReason = choice.optString("finish_reason", "")
                    if (finishReason == "content_filter") {
                        return Result.failure(Exception("Response blocked by content filter"))
                    }

                    val message = choice.optJSONObject("message")
                    var resultText = message?.optString("content", "") ?: ""
                    if (resultText.isBlank()) {
                        return Result.failure(Exception("Model returned empty response"))
                    }

                    // Try structured JSON extraction first
                    val (extracted, _) = ApiClientUtils.tryExtractStructuredText(resultText)
                    if (extracted != null) return Result.success(extracted)

                    // Fall back to raw text (strip markdown fences if present)
                    resultText = ApiClientUtils.stripMarkdownFences(resultText)
                    if (finishReason == "length") {
                        resultText += "\n\n[Note: Response may be truncated]"
                    }
                    Result.success(resultText)
                } else {
                    Result.failure(Exception("No choices found in response"))
                }
            } else if (responseCode == 429) {
                val retryAfter = connection.getHeaderField("Retry-After")
                val seconds = retryAfter?.toIntOrNull()
                val msg = if (seconds != null) {
                    "Rate limit exceeded, retry after ${seconds}s"
                } else {
                    "Rate limit exceeded"
                }
                Result.failure(ApiException(ApiError.RateLimit(msg, seconds), msg))
            } else if (responseCode == 401 || responseCode == 403) {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val apiMessage = ApiClientUtils.extractApiErrorMessage(errorBody)
                val detail = if (apiMessage.isNotEmpty()) apiMessage else "Invalid API key"
                Result.failure(ApiException(ApiError.InvalidKey(detail), detail))
            } else {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val detail = ApiClientUtils.sanitizeErrorForUser(
                    responseCode, errorBody, "Unexpected error"
                )
                val apiError = if (responseCode in 500..599) {
                    ApiError.ServerError(detail)
                } else {
                    ApiError.Other(detail)
                }
                Result.failure(ApiException(apiError, detail))
            }
        } catch (e: Exception) {
            val apiError = when (e) {
                is ApiException -> e.apiError
                is SocketTimeoutException,
                is UnknownHostException,
                is ConnectException -> ApiError.Network(e.message ?: "Network error")
                else -> ApiError.Other(e.message ?: "Unknown error")
            }
            if (e is ApiException) {
                Result.failure(e)
            } else {
                Result.failure(ApiException(apiError, e.message ?: "Unknown error"))
            }
        } finally {
            connection?.disconnect()
        }
    }
}
