package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.api.OpenAICompatibleClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KeyValidationTest {
    @Test fun duplicateKeysDoNotSendAnotherValidationRequest() = runBlocking {
        val result = KeyValidation.validate("same", "https://example.com/v1", listOf("same"), OpenAICompatibleClient(), "failed",
            validateRemote = { _, _ -> error("Duplicate key sent to server") })
        assertEquals(KeyValidationResult.Duplicate, result)
    }

    @Test fun nativeAndCompatibleProvidersShareValidationRules() = runBlocking {
        val result = KeyValidation.validate("key", "https://example.com/v1", emptyList(), OpenAICompatibleClient(), "failed",
            validateRemote = { key, endpoint ->
                assertEquals("key", key)
                assertEquals("https://example.com/v1", endpoint)
                Result.success(listOf("native-model"))
            })
        assertEquals(KeyValidationResult.Valid, result)
    }

    @Test fun failuresKeepTheirTypedCauseForLocalizedUiErrors() = runBlocking {
        val failure = java.io.IOException("network")
        val result = KeyValidation.validate("key", "https://example.com/v1", emptyList(), OpenAICompatibleClient(), "failed",
            validateRemote = { _, _ -> Result.failure<String>(failure) })
        assertSame(failure, (result as KeyValidationResult.Invalid).cause)
    }

    @Test fun cancellationIsNeverTurnedIntoAnInvalidKey() = runBlocking {
        try {
            KeyValidation.validate("key", "https://example.com/v1", emptyList(), OpenAICompatibleClient(), "failed",
                validateRemote = { _, _ -> throw CancellationException("cancelled") })
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
    }
}
