package com.musheer360.swiftslate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    @Query("SELECT * FROM providers")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ProviderEntity?

    @Query("SELECT * FROM providers WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ProviderEntity?>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity)

    @Query("UPDATE providers SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE providers SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: String)
}
