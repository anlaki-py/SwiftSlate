package com.musheer360.swiftslate.service

/**
 * Maps raw API and network error messages to concise, user-friendly descriptions.
 * Used by [AiCommandProcessor] to display actionable feedback via [OverlayToastManager].
 */
object ErrorMessageMapper {

    /**
     * Translates a raw error string into a short, user-friendly message.
     * Matches against known error patterns (permission, rate-limit, safety, network, etc.)
     * and returns the original string when no pattern matches.
     *
     * @param raw The raw error message from the API or network layer.
     * @return A human-readable error message.
     */
    fun map(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("permission_denied") ||
                lower.contains("permission denied") ->
                "Your API key doesn't have access to this model."
            lower.contains("invalid api key") ||
                lower.contains("api key not valid") ||
                lower.contains("api_key_invalid") ->
                "Invalid API key. Please check your key in Settings."
            lower.contains("rate limit") ||
                lower.contains("resource_exhausted") ||
                lower.contains("quota") ->
                "Rate limited. Try again shortly."
            lower.contains("model not found") ||
                lower.contains("model_not_found") ||
                lower.contains("not found for api version") ->
                "Model not found. Check your model selection in Settings."
            lower.contains("safety") ||
                lower.contains("content_filter") ||
                lower.contains("recitation") ||
                lower.contains("blocked by safety") ||
                lower.contains("finish_reason: safety") ->
                "Response blocked by safety filters. Try rephrasing."
            lower.contains("empty response") ||
                lower.contains("no content found") ||
                lower.contains("no choices found") ->
                "Model returned an empty response. Try again."
            lower.contains("timeout") ||
                lower.contains("timed out") ->
                "Request timed out. Check your connection."
            lower.contains("unable to resolve host") ||
                lower.contains("no address associated") ||
                lower.contains("network is unreachable") ||
                lower.contains("no route to host") ->
                "No internet connection."
            lower.contains("connection refused") ||
                lower.contains("connect failed") ->
                "Could not reach the API. Check your endpoint URL."
            lower.contains("bad request") ->
                "Request failed. Check your settings."
            else -> raw
        }
    }
}
