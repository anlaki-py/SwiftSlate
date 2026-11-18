package com.musheer360.swiftslate.api

import org.json.JSONArray
import org.json.JSONObject

/** OpenAI wire-format handling shared by key validation, model discovery, and generation. */
internal object OpenAiProtocol {
    fun modelIds(body: String): List<String> {
        val entries = JSONObject(body).getJSONArray("data")
        return (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString("id") }
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    fun request(prompt: String, text: String, model: String, temperature: Double, format: Int): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content",
                    ApiClientUtils.SYSTEM_PROMPT_PREFIX + prompt +
                        if (format > 0) "\nReturn a JSON object with the transformed text in its text field." else ""))
                put(JSONObject().put("role", "user").put("content", "---BEGIN TEXT---\n$text\n---END TEXT---"))
            })
            put("temperature", temperature.coerceIn(0.0, 2.0))
            if (format == 1) put("response_format", JSONObject().put("type", "json_object"))
            if (format == 2) put("response_format", JSONObject().put("type", "json_schema").put("json_schema",
                JSONObject().put("name", "text_output").put("strict", true).put("schema",
                    JSONObject().put("type", "object").put("properties",
                        JSONObject().put("text", JSONObject().put("type", "string")))
                        .put("required", JSONArray().put("text")).put("additionalProperties", false))))
        }

    fun unsupportedFormat(response: AiHttpResponse): Boolean {
        if (response.code !in setOf(400, 422)) return false
        val error = runCatching { JSONObject(response.body).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message").orEmpty().lowercase()
        val parameter = error?.optString("param").orEmpty().lowercase()
        return parameter.startsWith("response_format") ||
            listOf("response_format", "json_schema", "json schema").any { it in message } &&
            listOf("unsupported", "not support", "not available", "not permitted", "invalid").any { it in message }
    }

    fun unsupportedTemperature(response: AiHttpResponse): Boolean {
        if (response.code !in setOf(400, 422)) return false
        val error = runCatching { JSONObject(response.body).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message").orEmpty().lowercase()
        return error?.optString("param") == "temperature" &&
            ("unsupported" in message || "not support" in message || "only" in message && "default" in message)
    }

    fun text(body: String): String {
        val choice = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
            ?: throw ApiException(ApiError.Other("invalid_response"), "invalid_response")
        when (choice.optString("finish_reason")) {
            "length" -> throw ApiException(ApiError.Other("truncated_response"), "truncated_response")
            "content_filter" -> throw ApiException(ApiError.Other("blocked_response"), "blocked_response")
        }
        val message = choice.getJSONObject("message")
        if (!message.isNull("refusal") && message.optString("refusal").isNotBlank()) {
            throw ApiException(ApiError.Other("blocked_response"), "blocked_response")
        }
        val content = message.opt("content")
        val raw = when (content) {
            is String -> content
            is JSONArray -> (0 until content.length()).mapNotNull { index ->
                content.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text")
            }.joinToString("")
            else -> ""
        }
        val extracted = ApiClientUtils.tryExtractStructuredText(raw).first ?: ApiClientUtils.stripMarkdownFences(raw)
        if (extracted.isBlank()) throw ApiException(ApiError.Other("empty_response"), "empty_response")
        return extracted
    }

    fun exception(response: AiHttpResponse): ApiException {
        val code = "http_${response.code}"
        val error = when (response.code) {
            401 -> ApiError.InvalidKey(code)
            429 -> ApiError.RateLimit(code, response.headers["Retry-After"]?.toIntOrNull()?.coerceIn(1, 86400))
            in 500..599 -> ApiError.ServerError(code)
            else -> ApiError.Other(code)
        }
        return ApiException(error, code)
    }
}
