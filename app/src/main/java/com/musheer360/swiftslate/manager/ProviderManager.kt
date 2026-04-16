package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import com.musheer360.swiftslate.model.Provider
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * CRUD manager for user-defined AI providers.
 *
 * Providers are persisted as a JSON array in SharedPreferences.
 * Each provider has a unique UUID, a user-given name, an endpoint,
 * and an optional selected model.
 *
 * @param context Application context for SharedPreferences access.
 */
class ProviderManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "providers_prefs"
        const val KEY_PROVIDERS = "providers_json"
        const val KEY_ACTIVE_ID = "active_provider_id"
    }

    /**
     * Returns all user-defined providers.
     *
     * @return List of [Provider] objects, or empty if none defined.
     */
    fun getProviders(): List<Provider> {
        val json = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { parseProvider(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the currently active provider, or null if none set.
     *
     * @return The active [Provider], or null.
     */
    fun getActiveProvider(): Provider? {
        val activeId = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        return getProviders().find { it.id == activeId }
    }

    /**
     * Sets the active provider by ID.
     *
     * @param id The provider UUID to make active.
     */
    fun setActiveProvider(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    /**
     * Creates a new provider with a generated UUID and persists it.
     *
     * @param name User-given display name.
     * @param endpoint OpenAI-compatible base URL.
     * @return The newly created [Provider].
     */
    fun addProvider(name: String, endpoint: String): Provider {
        val provider = Provider(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            endpoint = endpoint.trim().trimEnd('/')
        )
        val providers = getProviders().toMutableList()
        providers.add(provider)
        saveProviders(providers)

        // Auto-activate if this is the first provider
        if (providers.size == 1) {
            setActiveProvider(provider.id)
        }
        return provider
    }

    /**
     * Updates an existing provider's fields. Only non-null params are applied.
     *
     * @param id The provider UUID to update.
     * @param name New display name, or null to keep existing.
     * @param endpoint New endpoint URL, or null to keep existing.
     * @param selectedModel New selected model, or null to keep existing.
     * @return True if the provider was found and updated.
     */
    fun updateProvider(
        id: String,
        name: String? = null,
        endpoint: String? = null,
        selectedModel: String? = null
    ): Boolean {
        val providers = getProviders().toMutableList()
        val index = providers.indexOfFirst { it.id == id }
        if (index == -1) return false

        val current = providers[index]
        providers[index] = current.copy(
            name = name?.trim() ?: current.name,
            endpoint = endpoint?.trim()?.trimEnd('/') ?: current.endpoint,
            selectedModel = selectedModel ?: current.selectedModel
        )
        saveProviders(providers)
        return true
    }

    /**
     * Removes a provider and clears active selection if it was active.
     *
     * @param id The provider UUID to remove.
     * @return True if the provider was found and removed.
     */
    fun removeProvider(id: String): Boolean {
        val providers = getProviders().toMutableList()
        val removed = providers.removeAll { it.id == id }
        if (!removed) return false

        saveProviders(providers)

        // Clear active if deleted, auto-select next if available
        if (prefs.getString(KEY_ACTIVE_ID, null) == id) {
            val nextId = providers.firstOrNull()?.id
            prefs.edit().putString(KEY_ACTIVE_ID, nextId).apply()
        }
        return true
    }

    /**
     * Serialises the provider list and writes it to SharedPreferences.
     *
     * @param providers The full list of providers to persist.
     */
    private fun saveProviders(providers: List<Provider>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("endpoint", p.endpoint)
                put("selectedModel", p.selectedModel)
            })
        }
        prefs.edit().putString(KEY_PROVIDERS, arr.toString()).apply()
    }

    /**
     * Parses a single [Provider] from a [JSONObject].
     *
     * @param obj The JSON object containing provider fields.
     * @return The parsed [Provider].
     */
    private fun parseProvider(obj: JSONObject): Provider {
        return Provider(
            id = obj.getString("id"),
            name = obj.getString("name"),
            endpoint = obj.getString("endpoint"),
            selectedModel = obj.optString("selectedModel", "")
        )
    }
}
