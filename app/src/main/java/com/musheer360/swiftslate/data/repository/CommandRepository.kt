package com.musheer360.swiftslate.data.repository

import com.musheer360.swiftslate.model.Command
import kotlinx.coroutines.flow.Flow

interface CommandRepository {
    fun observeCommands(): Flow<List<Command>>
    fun getTriggerPrefix(): String
    suspend fun setTriggerPrefix(newPrefix: String): Boolean
    suspend fun getCommands(): List<Command>
    suspend fun getDeletedBuiltInCommands(): List<Command>
    suspend fun addCustomCommand(command: Command)
    suspend fun removeCustomCommand(trigger: String)
    suspend fun overrideBuiltInCommand(builtInKey: String, newTrigger: String, newPrompt: String, newDescription: String)
    suspend fun deleteBuiltInCommand(builtInKey: String): Boolean
    suspend fun resetBuiltInCommand(builtInKey: String)
    fun isUndeletable(builtInKey: String): Boolean
    fun isBuiltInOverridden(builtInKey: String): Boolean
    fun getTranslatePrefix(): String
    suspend fun findCommand(text: String): Command?
    suspend fun exportCommands(): String
    suspend fun importCommands(json: String): Boolean
}
