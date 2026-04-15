package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import com.musheer360.swiftslate.model.AiProvider
import org.json.JSONArray
import java.util.UUID

class ProviderManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("providers", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Volatile
    private var cachedProviders: List<AiProvider>? = null

    companion object {
        private const val PREF_PROVIDERS = "providers_array"
        private const val PREF_ACTIVE_PROVIDER_ID = "active_provider_id"
    }

    init {
        // Migration and seeding
        if (!prefs.contains(PREF_PROVIDERS)) {
            val legacyActiveType = settingsPrefs.getString("provider_type", "gemini") ?: "gemini"
            
            val initial = listOf(
                AiProvider(
                    id = "gemini",
                    name = "Google Gemini",
                    endpoint = "https://generativelanguage.googleapis.com/v1beta/openai",
                    selectedModel = settingsPrefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
                ),
                AiProvider(
                    id = "groq",
                    name = "Groq",
                    endpoint = "https://api.groq.com/openai/v1",
                    selectedModel = settingsPrefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
                ),
                AiProvider(
                    id = "custom",
                    name = "Custom Default",
                    endpoint = settingsPrefs.getString("custom_endpoint", "https://api.openai.com/v1")?.takeIf { it.isNotBlank() } ?: "https://api.openai.com/v1",
                    selectedModel = settingsPrefs.getString("custom_model", "") ?: ""
                )
            )
            val arr = JSONArray()
            initial.forEach { arr.put(it.toJson()) }
            prefs.edit()
                .putString(PREF_PROVIDERS, arr.toString())
                .putString(PREF_ACTIVE_PROVIDER_ID, legacyActiveType)
                .apply()
        }
    }

    @Synchronized
    fun getProviders(): List<AiProvider> {
        cachedProviders?.let { return it }
        val jsonStr = prefs.getString(PREF_PROVIDERS, "[]") ?: "[]"
        val list = mutableListOf<AiProvider>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(AiProvider.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {}
        cachedProviders = list
        return list
    }

    @Synchronized
    fun getProvider(id: String): AiProvider? {
        return getProviders().find { it.id == id }
    }

    @Synchronized
    fun getActiveProviderId(): String {
        return prefs.getString(PREF_ACTIVE_PROVIDER_ID, "gemini") ?: "gemini"
    }

    @Synchronized
    fun getActiveProvider(): AiProvider? {
        val activeId = getActiveProviderId()
        return getProvider(activeId) ?: getProviders().firstOrNull()
    }

    @Synchronized
    fun setActiveProvider(id: String) {
        if (getProviders().any { it.id == id }) {
            prefs.edit().putString(PREF_ACTIVE_PROVIDER_ID, id).apply()
        }
    }

    @Synchronized
    fun addOrUpdateProvider(provider: AiProvider) {
        val providers = getProviders().toMutableList()
        val index = providers.indexOfFirst { it.id == provider.id }
        if (index != -1) {
            providers[index] = provider
        } else {
            providers.add(provider)
        }
        
        val arr = JSONArray()
        providers.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(PREF_PROVIDERS, arr.toString()).apply()
        cachedProviders = providers
    }

    @Synchronized
    fun updateProviderModel(id: String, model: String) {
        val provider = getProvider(id) ?: return
        addOrUpdateProvider(provider.copy(selectedModel = model))
    }

    @Synchronized
    fun deleteProvider(id: String) {
        val providers = getProviders().toMutableList()
        providers.removeAll { it.id == id }
        
        // If we deleted the active provider, select another one
        if (getActiveProviderId() == id) {
            val newActive = providers.firstOrNull()?.id ?: ""
            prefs.edit().putString(PREF_ACTIVE_PROVIDER_ID, newActive).apply()
        }

        val arr = JSONArray()
        providers.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(PREF_PROVIDERS, arr.toString()).apply()
        cachedProviders = providers
    }
}
