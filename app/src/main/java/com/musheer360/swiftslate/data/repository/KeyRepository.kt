package com.musheer360.swiftslate.data.repository

interface KeyRepository {
    val keystoreAvailable: Boolean
    suspend fun getKeys(providerId: String): List<String>
    suspend fun addKey(providerId: String, key: String): Boolean
    suspend fun removeKey(providerId: String, key: String): Boolean
    suspend fun removeKeysForProvider(providerId: String)
    suspend fun getNextKey(providerId: String): String?
    fun reportRateLimit(key: String, retryAfterSeconds: Long)
    fun markInvalid(key: String)
    fun getShortestWaitTimeMs(providerId: String): Long?
}
