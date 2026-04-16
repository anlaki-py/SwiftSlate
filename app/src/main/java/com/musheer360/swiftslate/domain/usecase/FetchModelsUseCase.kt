package com.musheer360.swiftslate.domain.usecase

import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FetchModelsUseCase(
    private val keyManager: KeyManager,
    private val openAIClient: OpenAICompatibleClient
) {
    suspend operator fun invoke(provider: AiProvider): Result<List<String>> = withContext(Dispatchers.IO) {
        val key = keyManager.getKeys(provider.id).firstOrNull()
        if (key == null) {
            return@withContext Result.failure(Exception("Please add an API key first in the Keys tab."))
        }
        
        openAIClient.fetchModels(key, provider.endpoint)
    }
}
