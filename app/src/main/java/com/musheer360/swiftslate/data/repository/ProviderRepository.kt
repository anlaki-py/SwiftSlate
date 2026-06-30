package com.musheer360.swiftslate.data.repository

import com.musheer360.swiftslate.model.Provider
import kotlinx.coroutines.flow.Flow

interface ProviderRepository {
    fun observeProviders(): Flow<List<Provider>>
    fun observeActiveProvider(): Flow<Provider?>
    suspend fun getProviders(): List<Provider>
    suspend fun getActiveProvider(): Provider?
    suspend fun setActiveProvider(id: String)
    suspend fun addProvider(name: String, endpoint: String): Provider
    suspend fun updateProvider(id: String, name: String? = null, endpoint: String? = null, selectedModel: String? = null): Boolean
    suspend fun removeProvider(id: String): Boolean
}
