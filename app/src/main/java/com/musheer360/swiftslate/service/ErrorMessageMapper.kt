package com.musheer360.swiftslate.service

import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import com.musheer360.swiftslate.R

/**
 * Maps raw API and network error messages to concise, user-friendly descriptions.
 * Used by [AiCommandProcessor] to display actionable feedback via [OverlayToastManager].
 */
object ErrorMessageMapper {
    /** Resolves typed protocol failures without exposing server responses or credentials. */
    fun describe(context: android.content.Context, error: Throwable): String {
        val google = error as? GoogleException
        if (google != null) {
            val resource = when (google.failure) {
                GoogleFailure.NETWORK -> R.string.google_error_network
                GoogleFailure.NO_ACCOUNTS -> R.string.google_error_no_accounts
                GoogleFailure.ALL_DISABLED -> R.string.google_error_disabled
                GoogleFailure.LOGIN_REQUIRED -> R.string.google_error_login_required
                GoogleFailure.RATE_LIMITED -> R.string.google_error_rate_limited
                GoogleFailure.ACCESS_DENIED -> R.string.google_error_access_denied
                GoogleFailure.VERIFY_ACCOUNT -> R.string.google_error_verify_account
                GoogleFailure.CLIENT_OUTDATED -> R.string.google_error_client_outdated
                GoogleFailure.CONFIGURATION -> R.string.google_error_configuration
                GoogleFailure.LOGIN_CANCELLED -> R.string.google_error_cancelled
                GoogleFailure.LOGIN_TIMEOUT -> R.string.google_error_timeout
                GoogleFailure.LOGIN_PORT_BUSY -> R.string.google_error_port_busy
                GoogleFailure.INVALID_CALLBACK -> R.string.google_error_callback
                GoogleFailure.STORAGE -> R.string.google_error_storage
                GoogleFailure.MODEL_UNAVAILABLE -> R.string.google_error_model
                GoogleFailure.BLOCKED -> R.string.ai_error_blocked
                GoogleFailure.TRUNCATED -> R.string.ai_error_truncated
                GoogleFailure.SERVER -> R.string.google_error_server
                else -> R.string.google_error_response
            }
            return context.getString(resource)
        }
        val resource = when (error.message) {
            "blocked_response" -> R.string.ai_error_blocked
            "truncated_response" -> R.string.ai_error_truncated
            "empty_response" -> R.string.ai_error_empty
            "invalid_response", "response_too_large" -> R.string.ai_error_invalid_response
            "http_401" -> R.string.ai_error_authentication
            "http_403" -> R.string.ai_error_forbidden
            "no_keys" -> R.string.ai_error_no_keys
            "keys_unavailable" -> R.string.ai_error_keys_unavailable
            "http_429" -> R.string.google_error_rate_limited
            else -> if (error is com.musheer360.swiftslate.api.ApiException)
                R.string.ai_error_bad_request else null
        }
        return if (resource != null) context.getString(resource) else map(error.message ?: "")
    }


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
