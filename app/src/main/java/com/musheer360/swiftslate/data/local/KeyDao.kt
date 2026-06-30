package com.musheer360.swiftslate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyDao {

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId")
    fun observeByProvider(providerId: String): Flow<List<KeyEntity>>

    @Query("SELECT * FROM api_keys WHERE providerId = :providerId")
    suspend fun getByProvider(providerId: String): List<KeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: KeyEntity)

    @Query("DELETE FROM api_keys WHERE providerId = :providerId AND encryptedKey = :encryptedKey")
    suspend fun deleteByValue(providerId: String, encryptedKey: String)

    @Query("DELETE FROM api_keys WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Query("SELECT COUNT(*) FROM api_keys WHERE providerId = :providerId")
    suspend fun countByProvider(providerId: String): Int
}
