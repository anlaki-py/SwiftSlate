package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.data.remote.OpenAiClient

object ModelFetcher {

    suspend fun fetchModels(
        apiKey: String,
        endpoint: String,
        client: OpenAiClient
    ): Result<List<String>> {
        return client.fetchModels(apiKey, endpoint)
    }
}
