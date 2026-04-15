package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.model.ProviderType

/**
 * Result of validating an API key against a provider.
 */
sealed class KeyValidationResult {
    /**
     * Key is valid and was accepted by the provider.
     */
    data object Valid : KeyValidationResult()

    /**
     * Key is a duplicate of one already stored.
     */
    data object Duplicate : KeyValidationResult()

    /**
     * Key validation failed.
     *
     * @param message Error message from the provider or a fallback.
     */
    data class Invalid(val message: String) : KeyValidationResult()
}

/**
 * Handles API key validation logic — resolves the correct endpoint
 * for each provider type and delegates the actual HTTP check to
 * [OpenAICompatibleClient].
 */
object KeyValidation {

    /** Gemini OpenAI-compatible endpoint. */
    private const val GEMINI_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/openai"

    /** Groq OpenAI-compatible endpoint. */
    private const val GROQ_ENDPOINT =
        "https://api.groq.com/openai/v1"

    /**
     * Validates an API key by calling the provider's model-list endpoint.
     *
     * @param key The API key to validate (already trimmed).
     * @param providerType The current provider type (gemini/groq/custom).
     * @param customEndpoint The user-configured custom endpoint, or blank.
     * @param existingKeys Keys already stored — checked for duplicates first.
     * @param client The HTTP client used for the actual validation call.
     * @param fallbackErrorMessage Fallback string if the provider returns no message.
     * @return A [KeyValidationResult] indicating success, duplicate, or failure.
     */
    suspend fun validate(
        key: String,
        providerType: String,
        customEndpoint: String,
        existingKeys: List<String>,
        client: OpenAICompatibleClient,
        fallbackErrorMessage: String
    ): KeyValidationResult {
        // Duplicate guard — avoid hitting the network for a key we already have
        if (existingKeys.contains(key)) {
            return KeyValidationResult.Duplicate
        }

        val endpoint = resolveEndpoint(providerType, customEndpoint)
        val result = client.validateKey(key, endpoint)

        return if (result.isSuccess) {
            KeyValidationResult.Valid
        } else {
            KeyValidationResult.Invalid(
                result.exceptionOrNull()?.message ?: fallbackErrorMessage
            )
        }
    }

    /**
     * Resolves the OpenAI-compatible base URL for a given provider type.
     *
     * @param providerType One of [ProviderType] constants.
     * @param customEndpoint User-configured endpoint for custom providers.
     * @return The base URL to validate against.
     */
    private fun resolveEndpoint(providerType: String, customEndpoint: String): String {
        return when {
            providerType == ProviderType.GROQ -> GROQ_ENDPOINT
            providerType == ProviderType.CUSTOM && customEndpoint.isNotBlank() -> customEndpoint
            else -> GEMINI_ENDPOINT
        }
    }

    /**
     * Returns the provider's API key console URL for the "Get API Key" link.
     *
     * @param providerType One of [ProviderType] constants.
     * @return A pair of (url, providerName) or (null, null) for custom providers.
     */
    fun getApiKeyUrl(providerType: String): Pair<String?, String?> {
        return when (providerType) {
            ProviderType.GROQ -> "https://console.groq.com/keys" to "Groq"
            ProviderType.CUSTOM -> null to null
            else -> "https://aistudio.google.com/api-keys" to "Gemini"
        }
    }
}
