package com.musheer360.swiftslate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey
    val trigger: String,
    val prompt: String,
    val type: String = CommandType.AI.name,
    val isBuiltIn: Boolean = false,
    val builtInKey: String? = null,
    val description: String = ""
)

fun CommandEntity.toDomain(): Command = Command(
    trigger = trigger,
    prompt = prompt,
    isBuiltIn = isBuiltIn,
    type = try { CommandType.valueOf(type) } catch (_: Exception) { CommandType.AI },
    builtInKey = builtInKey,
    isOverridden = false,
    description = description
)

fun Command.toEntity(): CommandEntity = CommandEntity(
    trigger = trigger,
    prompt = prompt,
    type = type.name,
    isBuiltIn = isBuiltIn,
    builtInKey = builtInKey,
    description = description
)
