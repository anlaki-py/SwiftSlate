package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.data.remote.OpenAiClient

sealed class KeyValidationResult {
    data object Valid : KeyValidationResult()
    data object Duplicate : KeyValidationResult()
    data class Invalid(val message: String) : KeyValidationResult()
}

object KeyValidation {

    suspend fun validate(
        key: String,
        endpoint: String,
        existingKeys: List<String>,
        client: OpenAiClient,
        fallbackErrorMessage: String
    ): KeyValidationResult {
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
