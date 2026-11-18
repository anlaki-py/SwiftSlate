package com.musheer360.swiftslate.api

import com.musheer360.swiftslate.data.KeyManager
import com.musheer360.swiftslate.feature.google.data.GoogleAccountRepository
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.model.ProviderProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Routes model discovery and transformations to the provider's native authentication protocol. */
class AiRequestRouter(
    keySource: () -> KeyManager,
    private val openAi: OpenAICompatibleClient,
    private val gemini: GeminiApiKeyClient,
    private val google: GoogleAccountRepository
) {
    private val keys by lazy(keySource)

    suspend fun models(provider: Provider): Result<List<String>> = result {
        if (provider.protocol == ProviderProtocol.GOOGLE_ACCOUNT) return@result google.models()
        withContext(Dispatchers.IO) {
            withKey(provider) { key ->
                if (provider.protocol == ProviderProtocol.GEMINI_API_KEY) gemini.models(key, provider.endpoint)
                else openAi.fetchModels(key, provider.endpoint)
            }
        }
    }

    suspend fun generate(provider: Provider, prompt: String, text: String, temperature: Double): Result<GenerateResult> = result {
        if (provider.protocol == ProviderProtocol.GOOGLE_ACCOUNT) {
            return@result GenerateResult(google.generate(provider.selectedModel, prompt, text, temperature))
        }
        withContext(Dispatchers.IO) {
            withKey(provider) { key ->
                if (provider.protocol == ProviderProtocol.GEMINI_API_KEY) {
                    gemini.generate(key, provider.endpoint, provider.selectedModel, prompt, text, temperature)
                } else openAi.generate(prompt, text, key, provider.selectedModel, temperature, provider.endpoint)
            }
        }
    }

    private suspend fun <T> withKey(provider: Provider, request: suspend (String) -> Result<T>): T {
        val count = keys.getKeys(provider.id).size
        val attempted = mutableSetOf<String>()
        var failure: Throwable? = null
        repeat(count) {
            val key = keys.getNextKey(provider.id) ?: return@repeat
            if (!attempted.add(key)) return@repeat
            val response = request(key)
            if (response.isSuccess) return response.getOrThrow()
            val error = response.exceptionOrNull()!!
            failure = error
            when (val api = (error as? ApiException)?.apiError) {
                is ApiError.RateLimit -> keys.reportRateLimit(key, api.retryAfterSeconds?.toLong() ?: 60)
                is ApiError.InvalidKey -> keys.markInvalid(key)
                else -> throw error
            }
        }
        throw failure ?: ApiException(ApiError.Other(if (count == 0) "no_keys" else "keys_unavailable"),
            if (count == 0) "no_keys" else "keys_unavailable")
    }
}
