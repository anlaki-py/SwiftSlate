package com.musheer360.swiftslate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {

    @Query("SELECT * FROM commands")
    fun observeAll(): Flow<List<CommandEntity>>

    @Query("SELECT * FROM commands")
    suspend fun getAll(): List<CommandEntity>

    @Query("SELECT * FROM commands WHERE trigger = :trigger")
    suspend fun getByTrigger(trigger: String): CommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(command: CommandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(commands: List<CommandEntity>)

    @Query("DELETE FROM commands WHERE trigger = :trigger")
    suspend fun deleteByTrigger(trigger: String)

    @Query("DELETE FROM commands WHERE isBuiltIn = 0")
    suspend fun deleteAllCustom()

    @Query("SELECT * FROM commands WHERE isBuiltIn = 1")
    suspend fun getBuiltIn(): List<CommandEntity>

    @Query("SELECT COUNT(*) FROM commands")
    suspend fun count(): Int
}
