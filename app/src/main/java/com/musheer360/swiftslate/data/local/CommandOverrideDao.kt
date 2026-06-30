package com.musheer360.swiftslate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommandOverrideDao {

    @Query("SELECT * FROM command_overrides WHERE builtInKey = :key")
    suspend fun getByKey(key: String): CommandOverrideEntity?

    @Query("SELECT * FROM command_overrides")
    suspend fun getAll(): List<CommandOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: CommandOverrideEntity)

    @Query("DELETE FROM command_overrides WHERE builtInKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM command_overrides")
    suspend fun deleteAll()
}
