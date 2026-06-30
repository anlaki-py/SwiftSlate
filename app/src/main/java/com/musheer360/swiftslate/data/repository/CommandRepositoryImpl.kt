package com.musheer360.swiftslate.data.repository

import com.musheer360.swiftslate.data.local.AppDatabase
import com.musheer360.swiftslate.data.local.CommandOverrideEntity
import com.musheer360.swiftslate.manager.CommandConstants
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.musheer360.swiftslate.data.local.CommandEntity
import com.musheer360.swiftslate.data.local.toDomain
import com.musheer360.swiftslate.data.local.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : CommandRepository {

    private val commandDao = db.commandDao()
    private val overrideDao = db.commandOverrideDao()

    private var cachedPrefix: String = CommandConstants.DEFAULT_PREFIX

    override fun observeCommands(): Flow<List<Command>> {
        return commandDao.observeAll().map { entities ->
            val prefix = cachedPrefix
            val overrides = overrideDao.getAll().associateBy { it.builtInKey }

            entities.map { entity ->
                val override = overrides[entity.builtInKey]
                if (entity.isBuiltIn && override != null) {
                    entity.copy(
                        trigger = override.trigger,
                        prompt = override.prompt,
                        description = override.description.ifBlank { entity.description }
                    )
                } else {
                    entity
                }
            }.map { it.toDomain() }
                .filter { cmd ->
                    val override = overrides[cmd.builtInKey]
                    if (cmd.isBuiltIn && cmd.builtInKey != "translate") {
                        override?.isDeleted != true
                    } else true
                }
                .sortedByDescending { it.trigger.length }
        }
    }

    override fun getTriggerPrefix(): String = cachedPrefix

    override suspend fun setTriggerPrefix(newPrefix: String): Boolean {
        if (newPrefix.length != 1 || newPrefix[0].isLetterOrDigit() || newPrefix[0].isWhitespace()) return false
        cachedPrefix = newPrefix

        val overrides = overrideDao.getAll()
        for (override in overrides) {
            val oldTrigger = override.trigger
            val stripped = if (oldTrigger.isNotEmpty() && !oldTrigger[0].isLetterOrDigit()) {
                oldTrigger.substring(1)
            } else oldTrigger
            overrideDao.upsert(override.copy(trigger = newPrefix + stripped))
        }

        val allCommands = commandDao.getAll()
        for (cmd in allCommands) {
            if (!cmd.isBuiltIn && cmd.trigger.isNotEmpty() && !cmd.trigger[0].isLetterOrDigit()) {
                val stripped = cmd.trigger.substring(1)
                commandDao.upsert(cmd.copy(trigger = newPrefix + stripped))
            }
        }
        return true
    }

    override suspend fun getCommands(): List<Command> {
        val entities = commandDao.getAll()
        val overrides = overrideDao.getAll().associateBy { it.builtInKey }
        val prefix = cachedPrefix

        return entities.map { entity ->
            val override = overrides[entity.builtInKey]
            if (entity.isBuiltIn && override != null) {
                entity.copy(
                    trigger = override.trigger,
                    prompt = override.prompt,
                    description = override.description.ifBlank { entity.description }
                )
            } else {
                entity
            }
        }.map { it.toDomain() }
            .filter { cmd ->
                if (cmd.isBuiltIn && cmd.builtInKey != "translate") {
                    overrides[cmd.builtInKey]?.isDeleted != true
                } else true
            }
            .sortedByDescending { it.trigger.length }
    }

    override suspend fun getDeletedBuiltInCommands(): List<Command> {
        val overrides = overrideDao.getAll().filter { it.isDeleted }
        return overrides.map { override ->
            val def = CommandConstants.BUILT_IN_DEFINITIONS.find { it.key == override.builtInKey }
            Command(
                trigger = override.trigger,
                prompt = override.prompt.ifBlank { def?.prompt ?: "" },
                isBuiltIn = true,
                type = CommandType.AI,
                builtInKey = override.builtInKey,
                description = def?.description ?: ""
            )
        }
    }

    override suspend fun addCustomCommand(command: Command) {
        commandDao.upsert(command.toEntity())
    }

    override suspend fun removeCustomCommand(trigger: String) {
        commandDao.deleteByTrigger(trigger)
    }

    override suspend fun overrideBuiltInCommand(
        builtInKey: String, newTrigger: String, newPrompt: String, newDescription: String
    ) {
        overrideDao.upsert(
            CommandOverrideEntity(
                builtInKey = builtInKey,
                trigger = newTrigger,
                prompt = newPrompt,
                description = newDescription
            )
        )
        val existing = commandDao.getByTrigger(newTrigger)
        if (existing == null) {
            val def = CommandConstants.BUILT_IN_DEFINITIONS.find { it.key == builtInKey }
            commandDao.upsert(
                CommandEntity(
                    trigger = newTrigger,
                    prompt = newPrompt,
                    type = CommandType.AI.name,
                    isBuiltIn = true,
                    builtInKey = builtInKey,
                    description = newDescription.ifBlank { def?.description ?: "" }
                )
            )
        }
    }

    override suspend fun deleteBuiltInCommand(builtInKey: String): Boolean {
        if (builtInKey in CommandConstants.UNDELETABLE_KEYS) return false
        overrideDao.upsert(
            CommandOverrideEntity(
                builtInKey = builtInKey,
                trigger = "",
                prompt = "",
                isDeleted = true
            )
        )
        return true
    }

    override suspend fun resetBuiltInCommand(builtInKey: String) {
        overrideDao.deleteByKey(builtInKey)
        val def = CommandConstants.BUILT_IN_DEFINITIONS.find { it.key == builtInKey }
        if (def != null) {
            commandDao.upsert(
                CommandEntity(
                    trigger = "$cachedPrefix${def.key}",
                    prompt = def.prompt,
                    type = CommandType.AI.name,
                    isBuiltIn = true,
                    builtInKey = builtInKey,
                    description = def.description
                )
            )
        }
    }

    override fun isUndeletable(builtInKey: String): Boolean =
        builtInKey in CommandConstants.UNDELETABLE_KEYS

    override fun isBuiltInOverridden(builtInKey: String): Boolean {
        return runBlockingOrNull { overrideDao.getByKey(builtInKey) != null }
    }

    override fun getTranslatePrefix(): String {
        val name = runBlockingOrNull {
            val override = overrideDao.getByKey("translate")
            override?.trigger?.removePrefix(cachedPrefix) ?: CommandConstants.DEFAULT_TRANSLATE_TRIGGER_NAME
        } ?: CommandConstants.DEFAULT_TRANSLATE_TRIGGER_NAME
        return "$cachedPrefix$name:"
    }

    override suspend fun findCommand(text: String): Command? {
        val commands = getCommands()
        for (cmd in commands) {
            if (cmd.builtInKey == "translate") continue
            if (text.endsWith(cmd.trigger)) return cmd
        }

        val translatePrefix = getTranslatePrefix()
        val translateIdx = text.lastIndexOf(translatePrefix)
        if (translateIdx >= 0) {
            val langPart = text.substring(translateIdx + translatePrefix.length)
            if (langPart.length in 2..5 && langPart.all { it.isLetterOrDigit() }) {
                val override = overrideDao.getByKey("translate")
                val promptTemplate = override?.prompt
                    ?: CommandConstants.DEFAULT_TRANSLATE_PROMPT
                val prompt = promptTemplate.replace(CommandConstants.LANG_PLACEHOLDER, langPart)
                val description = override?.description
                    ?: CommandConstants.DEFAULT_TRANSLATE_DESCRIPTION
                return Command(
                    trigger = "$translatePrefix$langPart",
                    prompt = prompt,
                    isBuiltIn = true,
                    type = CommandType.AI,
                    builtInKey = "translate",
                    description = description
                )
            }
        }
        return null
    }

    override suspend fun exportCommands(): String {
        val custom = commandDao.getAll().filter { !it.isBuiltIn }
        val json = kotlinx.serialization.json.Json { prettyPrint = false }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(com.musheer360.swiftslate.data.remote.CommandExport.serializer()),
            custom.map { cmd ->
                com.musheer360.swiftslate.data.remote.CommandExport(
                    trigger = cmd.trigger,
                    prompt = cmd.prompt,
                    type = cmd.type,
                    description = cmd.description
                )
            }
        )
    }

    override suspend fun importCommands(json: String): Boolean {
        return try {
            val jsonLib = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val imports = jsonLib.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(
                    com.musheer360.swiftslate.data.remote.CommandExport.serializer()
                ),
                json
            )
            if (imports.size > 100) return false
            for (cmd in imports) {
                if (cmd.trigger.isBlank() || cmd.prompt.isBlank()) return false
                if (cmd.trigger.length > 50 || cmd.prompt.length > 5000) return false
                if (!cmd.trigger.startsWith(cachedPrefix)) return false
                if (cmd.type != CommandType.AI.name && cmd.type != CommandType.TEXT_REPLACER.name) return false
            }
            commandDao.deleteAllCustom()
            commandDao.upsertAll(imports.map {
                CommandEntity(
                    trigger = it.trigger,
                    prompt = it.prompt,
                    type = it.type,
                    description = it.description
                )
            })
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun <T> runBlockingOrNull(block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.runBlocking { block() }
        } catch (_: Exception) {
            null
        }
    }
}
