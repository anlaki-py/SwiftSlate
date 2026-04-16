package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.api.OpenAICompatibleClient

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
 * Handles API key validation logic — delegates the actual HTTP check
 * to [OpenAICompatibleClient] using the provider's endpoint directly.
 */
object KeyValidation {

    /**
     * Validates an API key by calling the provider's model-list endpoint.
     *
     * @param key The API key to validate (already trimmed).
     * @param endpoint The provider's OpenAI-compatible base URL.
     * @param existingKeys Keys already stored — checked for duplicates first.
     * @param client The HTTP client used for the actual validation call.
     * @param fallbackErrorMessage Fallback string if the provider returns no message.
     * @return A [KeyValidationResult] indicating success, duplicate, or failure.
     */
    suspend fun validate(
        key: String,
        endpoint: String,
        existingKeys: List<String>,
        client: OpenAICompatibleClient,
        fallbackErrorMessage: String
    ): KeyValidationResult {
        // Duplicate guard — avoid hitting the network for a key we already have
        if (existingKeys.contains(key)) {
            return KeyValidationResult.Duplicate
        }

        val result = client.validateKey(key, endpoint)

        return if (result.isSuccess) {
            KeyValidationResult.Valid
        } else {
            KeyValidationResult.Invalid(
                result.exceptionOrNull()?.message ?: fallbackErrorMessage
            )
        }
    }
}
