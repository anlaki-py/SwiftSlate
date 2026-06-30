package com.musheer360.swiftslate.data.repository

import com.musheer360.swiftslate.data.local.AppDatabase
import com.musheer360.swiftslate.data.local.toDomain
import com.musheer360.swiftslate.data.local.toEntity
import com.musheer360.swiftslate.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : ProviderRepository {

    private val providerDao = db.providerDao()

    override fun observeProviders(): Flow<List<Provider>> {
        return providerDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeActiveProvider(): Flow<Provider?> {
        return providerDao.observeActive().map { it?.toDomain() }
    }

    override suspend fun getProviders(): List<Provider> {
        return providerDao.getAll().map { it.toDomain() }
    }

    override suspend fun getActiveProvider(): Provider? {
        return providerDao.getActive()?.toDomain()
    }

    override suspend fun setActiveProvider(id: String) {
        providerDao.clearActive()
        providerDao.setActive(id)
    }

    override suspend fun addProvider(name: String, endpoint: String): Provider {
        val provider = Provider(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            endpoint = endpoint.trim().trimEnd('/')
        )
        val providers = getProviders()
        providerDao.upsert(provider.toEntity(isActive = providers.isEmpty()))
        return provider
    }

    override suspend fun updateProvider(
        id: String,
        name: String?,
        endpoint: String?,
        selectedModel: String?
    ): Boolean {
        val current = providerDao.getById(id) ?: return false
        providerDao.upsert(
            current.copy(
                name = name?.trim() ?: current.name,
                endpoint = endpoint?.trim()?.trimEnd('/') ?: current.endpoint,
                selectedModel = selectedModel ?: current.selectedModel
            )
        )
        return true
    }

    override suspend fun removeProvider(id: String): Boolean {
        val current = providerDao.getById(id) ?: return false
        providerDao.deleteById(id)
        val remaining = providerDao.getAll()
        if (current.isActive) {
            val nextId = remaining.firstOrNull()?.id
            if (nextId != null) {
                providerDao.setActive(nextId)
            }
        }
        return true
    }
}
