package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages all commands — built-in and custom. Provides methods to
 * retrieve, add, remove, override, and find commands by trigger text.
 */
class CommandManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("commands", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val overrides = CommandOverrides(context)

    @Volatile private var cachedCommands: List<Command>? = null
    @Volatile private var cacheTimestamp = 0L

    companion object {
        const val DEFAULT_PREFIX = "?"
        const val PREF_TRIGGER_PREFIX = "trigger_prefix"
        private const val CACHE_TTL_MS = 5_000L
    }

    /** @return The current single-character trigger prefix (e.g. "?"). */
    fun getTriggerPrefix(): String {
        return settingsPrefs.getString(PREF_TRIGGER_PREFIX, DEFAULT_PREFIX) ?: DEFAULT_PREFIX
    }

    /**
     * Changes the trigger prefix and migrates all command triggers accordingly.
     * Migrates both custom commands and built-in overrides.
     *
     * @param newPrefix The new single-character prefix.
     * @return True if the prefix was valid and applied.
     */
    @Synchronized fun setTriggerPrefix(newPrefix: String): Boolean {
        if (newPrefix.length != 1 || newPrefix[0].isLetterOrDigit() || newPrefix[0].isWhitespace()) return false
        settingsPrefs.edit().putString(PREF_TRIGGER_PREFIX, newPrefix).apply()
        // Migrate built-in override triggers to new prefix
        overrides.migratePrefix(newPrefix)
        // Migrate custom command triggers
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = try { JSONArray(customStr) } catch (_: Exception) {
            prefs.edit().putString("custom_commands", "[]").apply()
            cachedCommands = null
            return true
        }
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val oldTrigger = obj.getString("trigger")
            val migrated = if (!oldTrigger.startsWith(newPrefix)) {
                val stripped = if (oldTrigger.isNotEmpty() && !oldTrigger[0].isLetterOrDigit()) {
                    oldTrigger.substring(1)
                } else oldTrigger
                newPrefix + stripped
            } else oldTrigger
            val newObj = JSONObject()
            newObj.put("trigger", migrated)
            newObj.put("prompt", obj.getString("prompt"))
            newObj.put("type", obj.optString("type", CommandType.AI.name))
            newArr.put(newObj)
        }
        prefs.edit().putString("custom_commands", newArr.toString()).apply()
        cachedCommands = null
        return true
    }

    /**
     * Returns built-in commands with overrides applied, deletions filtered,
     * and the translate display entry included.
     */
    private fun getBuiltInCommands(): List<Command> {
        val prefix = getTriggerPrefix()
        val commands = mutableListOf<Command>()
        // Standard built-in commands (fix, improve, etc.)
        for ((key, defaultPrompt) in CommandConstants.BUILT_IN_DEFINITIONS) {
            if (overrides.isDeleted(key)) continue
            val override = overrides.getOverride(key)
            if (override != null) {
                commands.add(Command(
                    override.first, override.second, true,
                    CommandType.AI, builtInKey = key, isOverridden = true
                ))
            } else {
                commands.add(Command(
                    "$prefix$key", defaultPrompt, true,
                    CommandType.AI, builtInKey = key
                ))
            }
        }
        // Translate command — always present (undeletable), uses display trigger with <lang>
        val translateOverride = overrides.getOverride("translate")
        if (translateOverride != null) {
            commands.add(Command(
                "${translateOverride.first}:<lang>", translateOverride.second, true,
                CommandType.AI, builtInKey = "translate", isOverridden = true
            ))
        } else {
            commands.add(Command(
                "${prefix}${CommandConstants.DEFAULT_TRANSLATE_TRIGGER_NAME}:<lang>",
                CommandConstants.DEFAULT_TRANSLATE_PROMPT, true,
                CommandType.AI, builtInKey = "translate"
            ))
        }
        return commands
    }

    @Volatile private var migrating = false

    /**
     * Returns all active commands (built-in + custom), sorted by trigger length descending.
     * Results are cached for up to [CACHE_TTL_MS] milliseconds.
     */
    @Synchronized fun getCommands(): List<Command> {
        val now = System.currentTimeMillis()
        val cached = cachedCommands
        if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) return cached
        val prefix = getTriggerPrefix()
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val customCommands = mutableListOf<Command>()
        var needsMigration = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val trigger = obj.getString("trigger")
            if (!trigger.startsWith(prefix)) needsMigration = true
            customCommands.add(Command(trigger, obj.getString("prompt"), false,
                try { CommandType.valueOf(obj.optString("type", CommandType.AI.name)) }
                catch (_: Exception) { CommandType.AI }))
        }
        // Self-heal prefix mismatch (e.g. crash between two apply() calls in setTriggerPrefix)
        if (needsMigration && !migrating) {
            migrating = true
            try {
                setTriggerPrefix(prefix)
                return getCommands()
            } finally {
                migrating = false
            }
        }
        val result = (getBuiltInCommands() + customCommands).sortedByDescending { it.trigger.length }
        cachedCommands = result
        cacheTimestamp = System.currentTimeMillis()
        return result
    }

    /**
     * Returns built-in commands that have been deleted (hidden) by the user.
     * Used by the UI to show a "Deleted" section with restore buttons.
     */
    @Synchronized fun getDeletedBuiltInCommands(): List<Command> {
        val prefix = getTriggerPrefix()
        return CommandConstants.BUILT_IN_DEFINITIONS
            .filter { (key, _) -> overrides.isDeleted(key) }
            .map { (key, prompt) -> Command("$prefix$key", prompt, true, CommandType.AI, builtInKey = key) }
    }

    /**
     * Saves an override for a built-in command and invalidates the cache.
     *
     * @param builtInKey The built-in command key (e.g. "fix").
     * @param newTrigger The new trigger string.
     * @param newPrompt The new prompt string.
     */
    @Synchronized fun overrideBuiltInCommand(builtInKey: String, newTrigger: String, newPrompt: String) {
        overrides.saveOverride(builtInKey, newTrigger, newPrompt)
        cachedCommands = null
    }

    /**
     * Marks a built-in command as deleted and invalidates the cache.
     *
     * @param builtInKey The built-in command key.
     * @return False if the command is protected from deletion.
     */
    @Synchronized fun deleteBuiltInCommand(builtInKey: String): Boolean {
        val result = overrides.markDeleted(builtInKey)
        if (result) cachedCommands = null
        return result
    }

    /**
     * Resets a built-in command to its default definition (removes override and un-deletes).
     *
     * @param builtInKey The built-in command key.
     */
    @Synchronized fun resetBuiltInCommand(builtInKey: String) {
        overrides.reset(builtInKey)
        cachedCommands = null
    }

    /** @return True if this built-in key is protected from deletion. */
    fun isUndeletable(builtInKey: String): Boolean = overrides.isUndeletable(builtInKey)

    /** @return True if this built-in command has a user override. */
    fun isBuiltInOverridden(builtInKey: String): Boolean = overrides.isOverridden(builtInKey)

    /**
     * Returns the full translate prefix string for trigger matching.
     * E.g. "?translate:" or "?tr:" if the user overrode the trigger name.
     */
    fun getTranslatePrefix(): String {
        val prefix = getTriggerPrefix()
        val name = overrides.getTranslateTriggerName(prefix)
        return "${prefix}${name}:"
    }

    /** @param command The custom command to add (replaces any existing with same trigger). */
    @Synchronized fun addCustomCommand(command: Command) {
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("trigger") != command.trigger) {
                newArr.put(obj)
            }
        }
        val newObj = JSONObject()
        newObj.put("trigger", command.trigger)
        newObj.put("prompt", command.prompt)
        newObj.put("type", command.type.name)
        newArr.put(newObj)
        prefs.edit().putString("custom_commands", newArr.toString()).apply()
        cachedCommands = null
    }

    /** @param trigger The trigger of the custom command to remove. */
    @Synchronized fun removeCustomCommand(trigger: String) {
        val customStr = prefs.getString("custom_commands", "[]") ?: "[]"
        val arr = JSONArray(customStr)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("trigger") != trigger) {
                newArr.put(obj)
            }
        }
        prefs.edit().putString("custom_commands", newArr.toString()).apply()
        cachedCommands = null
    }

    /** @return JSON string of all custom commands for backup. */
    @Synchronized fun exportCommands(): String {
        return prefs.getString("custom_commands", "[]") ?: "[]"
    }

    /**
     * Imports custom commands from a JSON string, replacing all existing custom commands.
     *
     * @param json The JSON array string to import.
     * @return True if the import was valid and applied.
     */
    @Synchronized fun importCommands(json: String): Boolean {
        return try {
            val arr = JSONArray(json)
            if (arr.length() > 100) return false
            val prefix = getTriggerPrefix()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val trigger = obj.optString("trigger", "")
                val prompt = obj.optString("prompt", "")
                if (trigger.isBlank() || prompt.isBlank()) return false
                if (trigger.length > 50 || prompt.length > 5000) return false
                if (!trigger.startsWith(prefix)) return false
                val type = obj.optString("type", CommandType.AI.name)
                if (type != CommandType.AI.name && type != CommandType.TEXT_REPLACER.name) return false
            }
            prefs.edit().putString("custom_commands", arr.toString()).apply()
            cachedCommands = null
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds a command matching the end of the given text.
     * Checks regular commands first, then handles the parametric translate trigger.
     *
     * @param text The full text to search for a trigger in.
     * @return The matching Command, or null if no trigger was found.
     */
    fun findCommand(text: String): Command? {
        val commands = getCommands()
        for (cmd in commands) {  // Already sorted by trigger length in getCommands()
            // Skip translate display entry — its literal "<lang>" can't match real text
            if (cmd.builtInKey == "translate") continue
            if (text.endsWith(cmd.trigger)) {
                return cmd
            }
        }
        // Translate trigger — parametric matching with overridable trigger name.
        // Intentionally accepts any 2-5 char alphanumeric language code
        // (e.g. "en", "fr", "zh", "ptBR"). The AI model handles invalid codes gracefully.
        val prefix = getTriggerPrefix()
        val translateName = overrides.getTranslateTriggerName(prefix)
        val translatePrefix = "${prefix}${translateName}:"
        val translateIdx = text.lastIndexOf(translatePrefix)
        if (translateIdx >= 0) {
            val langPart = text.substring(translateIdx + translatePrefix.length)
            if (langPart.length in 2..5 && langPart.all { it.isLetterOrDigit() }) {
                val promptTemplate = overrides.getTranslatePromptTemplate()
                val prompt = promptTemplate.replace(CommandConstants.LANG_PLACEHOLDER, langPart)
                return Command(
                    "${translatePrefix}$langPart", prompt, true,
                    CommandType.AI, builtInKey = "translate"
                )
            }
        }
        return null
    }
}
