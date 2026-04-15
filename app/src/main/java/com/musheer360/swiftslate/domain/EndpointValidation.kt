package com.musheer360.swiftslate.domain

/**
 * Result of validating a custom endpoint URL.
 * Either [Valid] (URL is acceptable) or [Error] (display message to user).
 */
sealed class EndpointValidationResult {
    /** Endpoint passed all validation checks or is blank (disabled). */
    data object Valid : EndpointValidationResult()

    /**
     * Endpoint failed validation.
     *
     * @param messageKey Identifies which error to show: `"scheme"` or `"spaces"`.
     */
    data class Error(val messageKey: String) : EndpointValidationResult()
}

/**
 * Pure validation logic for custom API endpoint URLs.
 * Enforces HTTPS requirement with a localhost exception for local development.
 */
object EndpointValidation {

    /** Hosts allowed over plain HTTP (local development only). */
    private val LOCALHOST_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /**
     * Validates a custom endpoint URL string.
     *
     * Rules:
     * - Blank URLs are valid (means "use default").
     * - URLs must not contain spaces.
     * - `https://` is always accepted.
     * - `http://` is accepted only for localhost / loopback hosts.
     * - All other schemes are rejected.
     *
     * @param url The endpoint URL to validate.
     * @return [EndpointValidationResult.Valid] or [EndpointValidationResult.Error].
     */
    fun validate(url: String): EndpointValidationResult {
        if (url.isBlank()) return EndpointValidationResult.Valid

        if (url.contains(" ")) {
            return EndpointValidationResult.Error("spaces")
        }

        if (url.startsWith("https://")) {
            return EndpointValidationResult.Valid
        }

        if (url.startsWith("http://")) {
            val host = try {
                java.net.URL(url).host
            } catch (_: Exception) {
                ""
            }
            return if (host in LOCALHOST_HOSTS) {
                EndpointValidationResult.Valid
            } else {
                EndpointValidationResult.Error("scheme")
            }
        }

        return EndpointValidationResult.Error("scheme")
    }

    /**
     * Checks whether a URL is structurally valid for persisting.
     * Used by the save-on-dispose safety flush.
     *
     * @param url The endpoint URL to check.
     * @return True if the URL is blank, HTTPS, or allowed HTTP localhost.
     */
    fun isSafeToSave(url: String): Boolean {
        return validate(url) is EndpointValidationResult.Valid
    }
}
