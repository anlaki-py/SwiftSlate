package com.musheer360.swiftslate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_overrides")
data class CommandOverrideEntity(
    @PrimaryKey
    val builtInKey: String,
    val trigger: String,
    val prompt: String,
    val description: String = "",
    val isDeleted: Boolean = false
)
