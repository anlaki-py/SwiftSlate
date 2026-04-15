package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.model.AiProvider

sealed class KeyValidationResult {
    data object Valid : KeyValidationResult()
    data object Duplicate : KeyValidationResult()
    data class Invalid(val message: String) : KeyValidationResult()
}

object KeyValidation {

    suspend fun validate(
        key: String,
        provider: AiProvider,
        existingKeys: List<String>,
        client: OpenAICompatibleClient,
        fallbackErrorMessage: String
    ): KeyValidationResult {
        if (existingKeys.contains(key)) {
            return KeyValidationResult.Duplicate
        }

        // We can just use the provider's configured endpoint
        val result = client.validateKey(key, provider.endpoint)

        return if (result.isSuccess) {
            KeyValidationResult.Valid
        } else {
            KeyValidationResult.Invalid(
                result.exceptionOrNull()?.message ?: fallbackErrorMessage
            )
        }
    }

    fun getApiKeyUrl(provider: AiProvider): Pair<String?, String?> {
        val lowerName = provider.name.lowercase()
        return when {
            lowerName.contains("groq") -> "https://console.groq.com/keys" to "Groq"
            lowerName.contains("gemini") || lowerName.contains("google") -> "https://aistudio.google.com/api-keys" to "Gemini"
            else -> null to null
        }
    }
}
