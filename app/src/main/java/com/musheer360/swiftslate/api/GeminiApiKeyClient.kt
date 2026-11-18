package com.musheer360.swiftslate.api

import com.musheer360.swiftslate.domain.EndpointValidation
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Native Gemini API requests use header authentication, never API keys in URLs. */
class GeminiApiKeyClient(private val http: AiHttpTransport) {
    suspend fun models(key: String, endpoint: String): Result<List<String>> = result {
        require(EndpointValidation.isSafeToSave(endpoint) && endpoint.isNotBlank())
        val models = mutableListOf<String>()
        var page: String? = null
        val seen = mutableSetOf<String>()
        do {
            val url = (endpoint.trimEnd('/') + "/models").toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "100")
            page?.let { url.addQueryParameter("pageToken", it) }
            val response = http.execute(Request.Builder().url(url.build()).header("x-goog-api-key", key).build())
            if (!response.isSuccessful) throw OpenAiProtocol.exception(response)
            val body = JSONObject(response.body)
            models.addAll(GeminiProtocol.modelIds(body))
            page = body.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (page != null && (!seen.add(page) || seen.size > 20)) error("invalid_pagination")
        } while (page != null)
        models.distinct().sorted()
    }

    suspend fun generate(key: String, endpoint: String, model: String, prompt: String, text: String, temperature: Double): Result<GenerateResult> = result {
        require(EndpointValidation.isSafeToSave(endpoint) && endpoint.isNotBlank())
        val url = (endpoint.trimEnd('/') + "/models/").toHttpUrl().newBuilder()
            .addPathSegment(model.removePrefix("models/") + ":generateContent").build()
        val body = GeminiProtocol.request(prompt, text, temperature)
        val response = http.execute(Request.Builder().url(url).header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        if (!response.isSuccessful) throw OpenAiProtocol.exception(response)
        GenerateResult(GeminiProtocol.text(response.body) { error ->
            OpenAiProtocol.exception(AiHttpResponse(error.optInt("code", 500), JSONObject().put("error", error).toString(), response.headers))
        })
    }
}
