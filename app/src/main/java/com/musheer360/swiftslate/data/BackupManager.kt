package com.musheer360.swiftslate.data

import android.content.Context
import android.content.SharedPreferences
import com.musheer360.swiftslate.model.CommandType
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Manages backup export and import for the full app state:
 * custom commands, built-in overrides/deletions, and the trigger prefix.
 *
 * Backup format (v2):
 * ```json
 * {
 *   "version": 2,
 *   "signature": "SWIFTSATE_BACKUP",
 *   "prefix": "?",
 *   "custom_commands": [...],
 *   "overrides": { "fix": {...}, "translate": {...} },
 *   "deletions": ["casual"],
 *   "checksum": "<sha256>"
 * }
 * ```
 */
class BackupManager(context: Context) {

    private val commandPrefs: SharedPreferences =
        context.getSharedPreferences("commands", Context.MODE_PRIVATE)
    private val overridePrefs: SharedPreferences =
        context.getSharedPreferences("command_overrides", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val CURRENT_VERSION = 2
        const val SIGNATURE = "SWIFTSATE_BACKUP"
        const val MIN_SUPPORTED_VERSION = 1
        const val MAX_CUSTOM_COMMANDS = 100
        const val MAX_TRIGGER_LENGTH = 50
        const val MAX_PROMPT_LENGTH = 5000
    }

    /**
     * Exports the full app state as a JSON string.
     * Includes custom commands, built-in overrides/deletions, prefix, version, signature, and checksum.
     */
    @Synchronized fun exportBackup(): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("signature", SIGNATURE)

        val prefix = settingsPrefs.getString(CommandManager.PREF_TRIGGER_PREFIX, CommandManager.DEFAULT_PREFIX)
            ?: CommandManager.DEFAULT_PREFIX
        root.put("prefix", prefix)

        root.put("custom_commands", exportCustomCommands())

        root.put("overrides", exportOverrides())
        root.put("deletions", exportDeletions())

        val checksum = computeChecksum(root)
        root.put("checksum", checksum)

        return root.toString(2)
    }

    /**
     * Imports app state from a JSON string.
     * Handles both v1 (raw custom commands array) and v2 (structured backup) formats.
     * Migrates triggers to the current prefix if the backup was created with a different one.
     *
     * @param json The JSON string to import.
     * @return [BackupResult.Success] or [BackupResult.Error] with a specific message key.
     */
    @Synchronized fun importBackup(json: String): BackupResult {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return BackupResult.Error("invalid_format")

        return try {
            if (trimmed.startsWith("[")) {
                return importV1(trimmed)
            }
            importV2(trimmed)
        } catch (_: Exception) {
            BackupResult.Error("parse_error")
        }
    }

    /**
     * Imports a v1 backup (raw JSONArray of custom commands, the old format).
     * Migrates triggers from the stored prefix to the current prefix.
     */
    private fun importV1(json: String): BackupResult {
        val arr = JSONArray(json)
        val currentPrefix = getCurrentPrefix()

        val migrated = migrateTriggerPrefix(arr, currentPrefix)

        val validationResult = validateCustomCommands(migrated, currentPrefix)
        if (validationResult != null) return validationResult

        commandPrefs.edit().putString("custom_commands", migrated.toString()).apply()
        return BackupResult.Success
    }

    /**
     * Imports a v2 structured backup.
     * Validates signature, version, checksum, then applies all sections.
     */
    private fun importV2(json: String): BackupResult {
        val root = JSONObject(json)

        if (root.optString("signature") != SIGNATURE) {
            return BackupResult.Error("invalid_format")
        }

        val version = root.optInt("version", 0)
        if (version < MIN_SUPPORTED_VERSION || version > CURRENT_VERSION) {
            return BackupResult.Error("version_unsupported")
        }

        if (!verifyChecksum(root)) {
            return BackupResult.Error("checksum_mismatch")
        }

        val backupPrefix = root.optString("prefix", CommandManager.DEFAULT_PREFIX)
        val currentPrefix = getCurrentPrefix()

        val customArr = root.optJSONArray("custom_commands") ?: JSONArray()
        val migrated = migrateTriggerPrefix(customArr, currentPrefix)

        val validationResult = validateCustomCommands(migrated, currentPrefix)
        if (validationResult != null) return validationResult

        val overrideErrors = importOverrides(root.optJSONObject("overrides"), backupPrefix, currentPrefix)
        if (overrideErrors != null) return overrideErrors

        importDeletions(root.optJSONArray("deletions"))

        commandPrefs.edit().putString("custom_commands", migrated.toString()).apply()

        return BackupResult.Success
    }

    private fun getCurrentPrefix(): String {
        return settingsPrefs.getString(CommandManager.PREF_TRIGGER_PREFIX, CommandManager.DEFAULT_PREFIX)
            ?: CommandManager.DEFAULT_PREFIX
    }

    private fun exportCustomCommands(): JSONArray {
        val raw = commandPrefs.getString("custom_commands", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    private fun exportOverrides(): JSONObject {
        val result = JSONObject()
        for ((key, value) in overridePrefs.all) {
            if (!key.startsWith("override_") || value !is String) continue
            val builtInKey = key.removePrefix("override_")
            result.put(builtInKey, JSONObject(value))
        }
        return result
    }

    private fun exportDeletions(): JSONArray {
        val result = JSONArray()
        for ((key, value) in overridePrefs.all) {
            if (!key.startsWith("deleted_") || value !is Boolean || !value) continue
            result.put(key.removePrefix("deleted_"))
        }
        return result
    }

    /**
     * Migrates all trigger prefixes in a custom commands JSONArray
     * from whatever prefix they currently use to [targetPrefix].
     */
    private fun migrateTriggerPrefix(arr: JSONArray, targetPrefix: String): JSONArray {
        val migrated = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val oldTrigger = obj.getString("trigger")
            val newTrigger = if (!oldTrigger.startsWith(targetPrefix)) {
                val stripped = if (oldTrigger.isNotEmpty() && !oldTrigger[0].isLetterOrDigit()) {
                    oldTrigger.substring(1)
                } else {
                    oldTrigger
                }
                targetPrefix + stripped
            } else {
                oldTrigger
            }
            val newObj = JSONObject()
            newObj.put("trigger", newTrigger)
            newObj.put("prompt", obj.getString("prompt"))
            newObj.put("type", obj.optString("type", CommandType.AI.name))
            if (obj.has("description")) {
                newObj.put("description", obj.getString("description"))
            }
            migrated.put(newObj)
        }
        return migrated
    }

    /**
     * Validates each entry in the custom commands JSONArray.
     * @return A [BackupResult.Error] if any entry is invalid, or null if all pass.
     */
    private fun validateCustomCommands(arr: JSONArray, prefix: String): BackupResult.Error? {
        if (arr.length() > MAX_CUSTOM_COMMANDS) {
            return BackupResult.Error("too_many_commands")
        }
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val trigger = obj.optString("trigger", "")
            val prompt = obj.optString("prompt", "")
            if (trigger.isBlank()) return BackupResult.Error("invalid_trigger")
            if (trigger.length <= prefix.length) return BackupResult.Error("invalid_trigger")
            if (prompt.isBlank()) return BackupResult.Error("invalid_prompt")
            if (trigger.length > MAX_TRIGGER_LENGTH) return BackupResult.Error("invalid_trigger")
            if (prompt.length > MAX_PROMPT_LENGTH) return BackupResult.Error("invalid_prompt")
            if (!trigger.startsWith(prefix)) return BackupResult.Error("invalid_trigger")
            val type = obj.optString("type", CommandType.AI.name)
            if (type != CommandType.AI.name && type != CommandType.TEXT_REPLACER.name) {
                return BackupResult.Error("invalid_type")
            }
        }
        return null
    }

    /**
     * Imports overrides from the backup, migrating the trigger prefix
     * from [backupPrefix] to [currentPrefix].
     */
    private fun importOverrides(
        overrides: JSONObject?,
        backupPrefix: String,
        currentPrefix: String
    ): BackupResult.Error? {
        if (overrides == null) return null

        val knownKeys = CommandConstants.BUILT_IN_DEFINITIONS.map { it.key }.toSet() + setOf("translate")

        val editor = overridePrefs.edit()
        val iter = overrides.keys()
        while (iter.hasNext()) {
            val builtInKey = iter.next()
            if (builtInKey !in knownKeys) continue

            val obj = overrides.getJSONObject(builtInKey)
            val trigger = obj.optString("trigger", "")
            val prompt = obj.optString("prompt", "")
            if (trigger.isBlank() || prompt.isBlank()) continue

            val migratedTrigger = if (!trigger.startsWith(currentPrefix)) {
                val stripped = if (trigger.isNotEmpty() && !trigger[0].isLetterOrDigit()) {
                    trigger.substring(1)
                } else {
                    trigger
                }
                currentPrefix + stripped
            } else {
                trigger
            }

            val overrideObj = JSONObject()
            overrideObj.put("trigger", migratedTrigger)
            overrideObj.put("prompt", prompt)
            overrideObj.put("description", obj.optString("description", ""))

            editor.putString("override_$builtInKey", overrideObj.toString())
        }
        if (backupPrefix != currentPrefix) {
            editor.apply()
            migrateRemainingOverrideTriggers(currentPrefix)
            return null
        }
        editor.apply()
        return null
    }

    /**
     * Migrates prefix on any overrides that were not part of the imported backup
     * (in case the user changed prefix after some overrides were set).
     */
    private fun migrateRemainingOverrideTriggers(newPrefix: String) {
        val editor = overridePrefs.edit()
        for ((key, value) in overridePrefs.all) {
            if (!key.startsWith("override_") || value !is String) continue
            try {
                val obj = JSONObject(value)
                val oldTrigger = obj.getString("trigger")
                val stripped = if (oldTrigger.isNotEmpty() && !oldTrigger[0].isLetterOrDigit()) {
                    oldTrigger.substring(1)
                } else {
                    oldTrigger
                }
                obj.put("trigger", newPrefix + stripped)
                editor.putString(key, obj.toString())
            } catch (_: Exception) { }
        }
        editor.apply()
    }

    private fun importDeletions(deletions: JSONArray?) {
        if (deletions == null) return
        val editor = overridePrefs.edit()
        for (i in 0 until deletions.length()) {
            val key = deletions.optString(i, "")
            if (key.isBlank()) continue
            if (key in CommandConstants.UNDELETABLE_KEYS) continue
            editor.putBoolean("deleted_$key", true)
        }
        editor.apply()
    }

    private fun computeChecksum(root: JSONObject): String {
        val payload = JSONObject()
        payload.put("version", root.optInt("version"))
        payload.put("signature", root.optString("signature"))
        payload.put("prefix", root.optString("prefix"))
        payload.put("custom_commands", root.optJSONArray("custom_commands"))
        payload.put("overrides", root.optJSONObject("overrides"))
        payload.put("deletions", root.optJSONArray("deletions"))

        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun verifyChecksum(root: JSONObject): Boolean {
        val stored = root.optString("checksum", "")
        if (stored.isEmpty()) return false
        val computed = computeChecksum(root)
        return stored == computed
    }
}
