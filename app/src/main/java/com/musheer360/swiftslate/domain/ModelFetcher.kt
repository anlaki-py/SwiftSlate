package com.musheer360.swiftslate.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches available model IDs from an OpenAI-compatible `/models` endpoint.
 *
 * Used when the user selects a provider to dynamically populate the model picker.
 * All network I/O runs on [Dispatchers.IO].
 */
object ModelFetcher {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_CHARS = 1_048_576

    /**
     * Fetches the list of model IDs from the provider's `/models` endpoint.
     *
     * @param apiKey Bearer token for authentication.
     * @param endpoint Base URL of the OpenAI-compatible API (e.g. "https://api.groq.com/openai/v1").
     * @return [Result.success] with a sorted list of model ID strings,
     *         or [Result.failure] with a descriptive error.
     */
    suspend fun fetchModels(
        apiKey: String,
        endpoint: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val baseUrl = endpoint.trimEnd('/')
            connection = URL("$baseUrl/models").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("User-Agent", "SwiftSlate")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val msg = when (responseCode) {
                    401, 403 -> "Invalid API key"
                    429 -> "Rate limited — try again later"
                    else -> "Error $responseCode"
                }
                return@withContext Result.failure(Exception(msg))
            }

            val body = readResponseBounded(connection)
            val models = parseModelIds(body)
            Result.success(models.sorted())
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Network error"))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Reads the response body up to [MAX_RESPONSE_CHARS] to prevent OOM.
     *
     * @param connection The open HTTP connection with a successful response.
     * @return The response body as a string.
     */
    private fun readResponseBounded(connection: HttpURLConnection): String {
        return connection.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                var total = 0
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    total += n
                    if (total > MAX_RESPONSE_CHARS) throw Exception("Response too large")
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        }
    }

    /**
     * Extracts model IDs from the standard OpenAI `/models` JSON response.
     *
     * Expected format: `{ "data": [ { "id": "model-name", ... }, ... ] }`
     *
     * @param body Raw JSON response body.
     * @return List of model ID strings, empty if parsing fails.
     */
    private fun parseModelIds(body: String): List<String> {
        return try {
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return emptyList()
            (0 until data.length()).mapNotNull { i ->
                data.getJSONObject(i).optString("id", null)
            }.filter { it.isNotBlank() }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
