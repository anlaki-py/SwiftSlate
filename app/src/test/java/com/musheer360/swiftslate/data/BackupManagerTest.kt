package com.musheer360.swiftslate.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var backupManager: BackupManager
    private lateinit var commandPrefs: android.content.SharedPreferences
    private lateinit var overridePrefs: android.content.SharedPreferences
    private lateinit var settingsPrefs: android.content.SharedPreferences
    private lateinit var providerPrefs: android.content.SharedPreferences
    private lateinit var assistantPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        commandPrefs = context.getSharedPreferences("commands", 0)
        overridePrefs = context.getSharedPreferences("command_overrides", 0)
        settingsPrefs = context.getSharedPreferences("settings", 0)
        providerPrefs = context.getSharedPreferences("providers_prefs", 0)
        assistantPrefs = context.getSharedPreferences("assistant_service_prefs", 0)

        commandPrefs.edit().clear().commit()
        overridePrefs.edit().clear().commit()
        settingsPrefs.edit().clear().commit()
        providerPrefs.edit().clear().commit()
        assistantPrefs.edit().clear().commit()

        backupManager = BackupManager(context)
    }

    private fun addCustomCommand(trigger: String, prompt: String, type: String = "AI") {
        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        val obj = JSONObject()
        obj.put("trigger", trigger)
        obj.put("prompt", prompt)
        obj.put("type", type)
        arr.put(obj)
        commandPrefs.edit().putString("custom_commands", arr.toString()).commit()
    }

    private fun addOverride(builtInKey: String, trigger: String, prompt: String, description: String = "") {
        val obj = JSONObject()
        obj.put("trigger", trigger)
        obj.put("prompt", prompt)
        obj.put("description", description)
        overridePrefs.edit().putString("override_$builtInKey", obj.toString()).commit()
    }

    private fun markDeleted(builtInKey: String) {
        overridePrefs.edit().putBoolean("deleted_$builtInKey", true).commit()
    }

    // --- Export ---

    @Test
    fun exportBackup_includesVersionAndSignature() {
        val json = backupManager.exportBackup()
        val root = JSONObject(json)
        assertEquals(3, root.getInt("version"))
        assertEquals("SWIFTSATE_BACKUP", root.getString("signature"))
    }

    @Test
    fun exportBackup_includesCurrentPrefix() {
        settingsPrefs.edit().putString("trigger_prefix", "!").commit()
        val json = backupManager.exportBackup()
        val root = JSONObject(json)
        assertEquals("!", root.getString("prefix"))
    }

    @Test
    fun exportBackup_includesCustomCommands() {
        addCustomCommand("?greet", "Say hello")
        val json = backupManager.exportBackup()
        val root = JSONObject(json)
        val arr = root.getJSONArray("custom_commands")
        assertEquals(1, arr.length())
        assertEquals("?greet", arr.getJSONObject(0).getString("trigger"))
    }

    @Test
    fun exportBackup_includesOverrides() {
        addOverride("fix", "?fixall", "Fix everything", "Fix all errors")
        val json = backupManager.exportBackup()
        val root = JSONObject(json)
        val overrides = root.getJSONObject("overrides")
        assertTrue(overrides.has("fix"))
        assertEquals("?fixall", overrides.getJSONObject("fix").getString("trigger"))
    }

    @Test
    fun exportBackup_includesDeletions() {
        markDeleted("casual")
        val json = backupManager.exportBackup()
        val root = JSONObject(json)
        val deletions = root.getJSONArray("deletions")
        assertEquals(1, deletions.length())
        assertEquals("casual", deletions.getString(0))
    }

    @Test
    fun exportBackup_includesChecksum() {
        val json = backupManager.exportBackup()
        val root = JSONObject(json)
        assertTrue(root.getString("checksum").isNotEmpty())
        assertEquals(64, root.getString("checksum").length)
    }

    @Test
    fun exportBackup_includesProvidersAndSettings() {
        providerPrefs.edit()
            .putString(
                "providers_json",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "provider-1")
                        put("name", "Provider")
                        put("endpoint", "https://example.com")
                        put("selectedModel", "model-1")
                    })
                }.toString()
            )
            .putString("active_provider_id", "provider-1")
            .commit()
        settingsPrefs.edit().putFloat("temperature", 0.5f).putFloat("timeout", 20f).commit()
        assistantPrefs.edit().putBoolean("processing_enabled", false).commit()

        val root = JSONObject(backupManager.exportBackup())

        assertEquals("provider-1", root.getString("active_provider_id"))
        assertEquals(1, root.getJSONArray("providers").length())
        assertTrue(root.getJSONObject("api_keys").has("provider-1"))
        val settings = root.getJSONObject("settings")
        assertEquals(0.5, settings.getDouble("temperature"), 0.001)
        assertEquals(20.0, settings.getDouble("timeout"), 0.001)
        assertFalse(settings.getBoolean("processing_enabled"))
    }

    // --- Import v2 ---

    @Test
    fun importBackup_v2_restoresCustomCommands() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray().apply {
                put(JSONObject().apply { put("trigger", "?hello"); put("prompt", "Say hi"); put("type", "AI") })
            })
            put("overrides", JSONObject())
            put("deletions", JSONArray())
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals(1, arr.length())
        assertEquals("?hello", arr.getJSONObject(0).getString("trigger"))
    }

    @Test
    fun importBackup_v2_restoresOverrides() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject().apply {
                put("fix", JSONObject().apply {
                    put("trigger", "?fixall")
                    put("prompt", "Fix everything")
                    put("description", "Fix all")
                })
            })
            put("deletions", JSONArray())
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)

        val overrideJson = overridePrefs.getString("override_fix", null)
        assertNotNull(overrideJson)
        val obj = JSONObject(overrideJson!!)
        assertEquals("?fixall", obj.getString("trigger"))
    }

    @Test
    fun importBackup_v2_restoresDeletions() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject())
            put("deletions", JSONArray().apply { put("casual") })
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)
        assertTrue(overridePrefs.getBoolean("deleted_casual", false))
    }

    @Test
    fun importBackup_v2_rejectsInvalidSignature() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "FAKE")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject())
            put("deletions", JSONArray())
            put("checksum", "abc")
        }
        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_format", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v2_rejectsUnsupportedVersion() {
        val backup = JSONObject().apply {
            put("version", 99)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject())
            put("deletions", JSONArray())
            put("checksum", "abc")
        }
        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("version_unsupported", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v2_rejectsCorruptedChecksum() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject())
            put("deletions", JSONArray())
            put("checksum", "0000000000000000000000000000000000000000000000000000000000000000")
        }
        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("checksum_mismatch", (result as BackupResult.Error).messageKey)
    }

    // --- Import v1 (backward compat) ---

    @Test
    fun importBackup_v1_restoresCustomCommands() {
        val v1 = JSONArray().apply {
            put(JSONObject().apply { put("trigger", "?hello"); put("prompt", "Say hi"); put("type", "AI") })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals(1, arr.length())
        assertEquals("?hello", arr.getJSONObject(0).getString("trigger"))
    }

    @Test
    fun importBackup_v1_rejectsTooManyCommands() {
        val v1 = JSONArray()
        for (i in 1..101) {
            v1.put(JSONObject().apply { put("trigger", "?cmd$i"); put("prompt", "Prompt $i"); put("type", "AI") })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("too_many_commands", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v1_rejectsBlankTrigger() {
        val v1 = JSONArray().apply {
            put(JSONObject().apply { put("trigger", ""); put("prompt", "Valid prompt"); put("type", "AI") })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_trigger", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v1_rejectsBlankPrompt() {
        val v1 = JSONArray().apply {
            put(JSONObject().apply { put("trigger", "?valid"); put("prompt", ""); put("type", "AI") })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_prompt", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v1_rejectsInvalidType() {
        val v1 = JSONArray().apply {
            put(JSONObject().apply { put("trigger", "?valid"); put("prompt", "Hi"); put("type", "INVALID") })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_type", (result as BackupResult.Error).messageKey)
    }

    // --- Prefix migration ---

    @Test
    fun importBackup_v1_migratesTriggersToCurrentPrefix() {
        settingsPrefs.edit().putString("trigger_prefix", "!").commit()
        val v1 = JSONArray().apply {
            put(JSONObject().apply { put("trigger", "?hello"); put("prompt", "Say hi"); put("type", "AI") })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals("!hello", arr.getJSONObject(0).getString("trigger"))
    }

    @Test
    fun importBackup_v2_migratesTriggersWhenPrefixDiffers() {
        settingsPrefs.edit().putString("trigger_prefix", "!").commit()
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray().apply {
                put(JSONObject().apply { put("trigger", "?hello"); put("prompt", "Say hi"); put("type", "AI") })
            })
            put("overrides", JSONObject().apply {
                put("fix", JSONObject().apply {
                    put("trigger", "?fixall")
                    put("prompt", "Fix everything")
                    put("description", "")
                })
            })
            put("deletions", JSONArray())
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals("!hello", arr.getJSONObject(0).getString("trigger"))

        val overrideJson = overridePrefs.getString("override_fix", null)
        assertNotNull(overrideJson)
        val obj = JSONObject(overrideJson!!)
        assertEquals("!fixall", obj.getString("trigger"))
    }

    @Test
    fun importBackup_v2_doesNotMigrateWhenSamePrefix() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray().apply {
                put(JSONObject().apply { put("trigger", "?hello"); put("prompt", "Say hi"); put("type", "AI") })
            })
            put("overrides", JSONObject())
            put("deletions", JSONArray())
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals("?hello", arr.getJSONObject(0).getString("trigger"))
    }

    // --- Round-trip ---

    @Test
    fun exportThenImport_preservesCustomCommands() {
        addCustomCommand("?greet", "Say hello")
        addCustomCommand("?code", "Write code", "TEXT_REPLACER")

        val exported = backupManager.exportBackup()
        commandPrefs.edit().clear().commit()

        val result = backupManager.importBackup(exported)
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals(2, arr.length())
    }

    @Test
    fun exportThenImport_preservesOverrides() {
        addOverride("fix", "?fixall", "Fix everything", "My fix")

        val exported = backupManager.exportBackup()
        overridePrefs.edit().clear().commit()

        val result = backupManager.importBackup(exported)
        assertTrue(result is BackupResult.Success)

        val overrideJson = overridePrefs.getString("override_fix", null)
        assertNotNull(overrideJson)
    }

    @Test
    fun exportThenImport_preservesDeletions() {
        markDeleted("casual")

        val exported = backupManager.exportBackup()
        overridePrefs.edit().clear().commit()

        val result = backupManager.importBackup(exported)
        assertTrue(result is BackupResult.Success)

        assertTrue(overridePrefs.getBoolean("deleted_casual", false))
    }

    @Test
    fun exportThenImport_afterPrefixChange_restoresCorrectly() {
        addCustomCommand("?greet", "Say hello")
        addOverride("fix", "?fixall", "Fix everything", "")

        val exported = backupManager.exportBackup()

        commandPrefs.edit().clear().commit()
        overridePrefs.edit().clear().commit()
        settingsPrefs.edit().putString("trigger_prefix", "!").commit()

        // Re-create backupManager to pick up new prefix
        val context = ApplicationProvider.getApplicationContext<Application>()
        backupManager = BackupManager(context)

        val result = backupManager.importBackup(exported)
        assertTrue(result is BackupResult.Success)

        val arr = JSONArray(commandPrefs.getString("custom_commands", "[]"))
        assertEquals("!greet", arr.getJSONObject(0).getString("trigger"))

        val overrideJson = overridePrefs.getString("override_fix", null)
        assertNotNull(overrideJson)
        assertEquals("!fixall", JSONObject(overrideJson!!).getString("trigger"))
    }

    // --- Error cases ---

    @Test
    fun importBackup_emptyString_returnsInvalidFormat() {
        val result = backupManager.importBackup("")
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_format", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_garbage_returnsParseError() {
        val result = backupManager.importBackup("not json at all {{{}}")
        assertTrue(result is BackupResult.Error)
        assertEquals("parse_error", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v1_rejectsTriggerTooLong() {
        val v1 = JSONArray().apply {
            put(JSONObject().apply {
                put("trigger", "?${"a".repeat(50)}")
                put("prompt", "Valid")
                put("type", "AI")
            })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_trigger", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v1_rejectsPromptTooLong() {
        val v1 = JSONArray().apply {
            put(JSONObject().apply {
                put("trigger", "?ok")
                put("prompt", "x".repeat(5001))
                put("type", "AI")
            })
        }
        val result = backupManager.importBackup(v1.toString())
        assertTrue(result is BackupResult.Error)
        assertEquals("invalid_prompt", (result as BackupResult.Error).messageKey)
    }

    @Test
    fun importBackup_v2_ignoresUnknownOverrideKeys() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject().apply {
                put("nonexistent_key", JSONObject().apply {
                    put("trigger", "?something")
                    put("prompt", "Prompt")
                    put("description", "")
                })
            })
            put("deletions", JSONArray())
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)
        assertNull(overridePrefs.getString("override_nonexistent_key", null))
    }

    @Test
    fun importBackup_v2_doesNotDeleteUndeletableKeys() {
        val backup = JSONObject().apply {
            put("version", 2)
            put("signature", "SWIFTSATE_BACKUP")
            put("prefix", "?")
            put("custom_commands", JSONArray())
            put("overrides", JSONObject())
            put("deletions", JSONArray().apply { put("translate") })
        }
        val checksum = computeTestChecksum(backup)
        backup.put("checksum", checksum)

        val result = backupManager.importBackup(backup.toString())
        assertTrue(result is BackupResult.Success)
        assertFalse(overridePrefs.getBoolean("deleted_translate", false))
    }

    private fun computeTestChecksum(root: JSONObject): String {
        val payload = JSONObject()
        payload.put("version", root.optInt("version"))
        payload.put("signature", root.optString("signature"))
        payload.put("prefix", root.optString("prefix"))
        payload.put("custom_commands", root.optJSONArray("custom_commands"))
        payload.put("overrides", root.optJSONObject("overrides"))
        payload.put("deletions", root.optJSONArray("deletions"))
        if (root.optInt("version") >= 3) {
            payload.put("providers", root.optJSONArray("providers"))
            payload.put("active_provider_id", root.opt("active_provider_id"))
            payload.put("api_keys", root.optJSONObject("api_keys"))
            payload.put("settings", root.optJSONObject("settings"))
        }
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
