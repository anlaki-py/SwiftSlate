package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Manages built-in command overrides and deletions.
 * Stores override data in a dedicated SharedPreferences file,
 * separate from custom command storage.
 */
class CommandOverrides(context: Context) {

    /**
     * Holds override data for a single built-in command.
     *
     * @param trigger The overridden trigger string.
     * @param prompt The overridden prompt string.
     * @param description The overridden description (may be empty).
     */
    data class OverrideData(val trigger: String, val prompt: String, val description: String)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("command_overrides", Context.MODE_PRIVATE)

    /**
     * Retrieves the override for a built-in command, if one exists.
     *
     * @param builtInKey The built-in command key (e.g. "fix", "translate").
     * @return A Pair of (trigger, prompt) if overridden, null otherwise.
     */
    fun getOverride(builtInKey: String): OverrideData? {
        val json = prefs.getString("override_$builtInKey", null) ?: return null
        return try {
            val obj = JSONObject(json)
            OverrideData(
                obj.getString("trigger"),
                obj.getString("prompt"),
                obj.optString("description", "")
            )
        } catch (_: Exception) { null }
    }

    /**
     * Checks whether a built-in command has been marked as deleted (hidden).
     *
     * @param builtInKey The built-in command key.
     * @return True if the command is deleted/hidden.
     */
    fun isDeleted(builtInKey: String): Boolean =
        prefs.getBoolean("deleted_$builtInKey", false)

    /**
     * Checks whether a built-in command has a user override.
     *
     * @param builtInKey The built-in command key.
     * @return True if the command has been modified from its default.
     */
    fun isOverridden(builtInKey: String): Boolean =
        prefs.getString("override_$builtInKey", null) != null

    /**
     * Checks whether a built-in command is protected from deletion.
     *
     * @param builtInKey The built-in command key.
     * @return True if the command cannot be deleted.
     */
    fun isUndeletable(builtInKey: String): Boolean =
        builtInKey in CommandConstants.UNDELETABLE_KEYS

    /**
     * Saves an override for a built-in command. Also un-deletes the command
     * if it was previously marked as deleted.
     *
     * @param builtInKey The built-in command key.
     * @param newTrigger The new trigger string (e.g. "?fixall"). For translate,
     *                   this is the base trigger without ":<lang>" (e.g. "?tr").
     * @param newPrompt The new prompt string.
     * @param newDescription The new description string (may be empty).
     */
    fun saveOverride(builtInKey: String, newTrigger: String, newPrompt: String, newDescription: String) {
        val obj = JSONObject().apply {
            put("trigger", newTrigger)
            put("prompt", newPrompt)
            put("description", newDescription)
        }
        prefs.edit()
            .putString("override_$builtInKey", obj.toString())
            .remove("deleted_$builtInKey")
            .apply()
    }

    /**
     * Marks a built-in command as deleted (hidden from the commands list
     * and disabled for matching).
     *
     * @param builtInKey The built-in command key.
     * @return True if the command was deleted, false if it is protected.
     */
    fun markDeleted(builtInKey: String): Boolean {
        if (isUndeletable(builtInKey)) return false
        prefs.edit().putBoolean("deleted_$builtInKey", true).apply()
        return true
    }

    /**
     * Removes the override and un-deletes a built-in command,
     * restoring it to its original default definition.
     *
     * @param builtInKey The built-in command key.
     */
    fun reset(builtInKey: String) {
        prefs.edit()
            .remove("override_$builtInKey")
            .remove("deleted_$builtInKey")
            .apply()
    }

    /**
     * Returns the current translate trigger name (the part after the prefix
     * and before the colon). Defaults to "translate" if no override exists.
     *
     * @param prefix The current trigger prefix character (e.g. "?").
     * @return The trigger name, e.g. "translate" or "tr".
     */
    fun getTranslateTriggerName(prefix: String): String {
        val override = getOverride("translate")
            ?: return CommandConstants.DEFAULT_TRANSLATE_TRIGGER_NAME
        val trigger = override.trigger
        // Strip the prefix to get just the name part
        return if (trigger.startsWith(prefix)) {
            trigger.substring(prefix.length)
        } else {
            CommandConstants.DEFAULT_TRANSLATE_TRIGGER_NAME
        }
    }

    /**
     * Returns the current translate prompt template with {lang} placeholder.
     *
     * @return The prompt template string.
     */
    fun getTranslatePromptTemplate(): String {
        val override = getOverride("translate")
            ?: return CommandConstants.DEFAULT_TRANSLATE_PROMPT
        return override.prompt
    }

    /**
     * Migrates all override triggers to use a new prefix character.
     * Called when the user changes the global trigger prefix.
     *
     * @param newPrefix The new trigger prefix character (e.g. "!").
     */
    fun migratePrefix(newPrefix: String) {
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("override_") || value !is String) continue
            try {
                val obj = JSONObject(value)
                val oldTrigger = obj.getString("trigger")
                // Strip any single-char non-alphanumeric prefix, then apply new one
                val stripped = if (oldTrigger.isNotEmpty() && !oldTrigger[0].isLetterOrDigit()) {
                    oldTrigger.substring(1)
                } else {
                    oldTrigger
                }
                obj.put("trigger", newPrefix + stripped)
                editor.putString(key, obj.toString())
            } catch (_: Exception) { /* Skip malformed entries */ }
        }
        editor.apply()
    }
}
