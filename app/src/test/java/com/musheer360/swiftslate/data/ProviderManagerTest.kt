package com.musheer360.swiftslate.data

import kotlinx.coroutines.async
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.musheer360.swiftslate.model.ProviderProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProviderManagerTest {
    private lateinit var context: Application
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("providers_prefs", 0).edit().clear().commit()
    }
    @Test fun legacyProvidersRemainOpenAiCompatibleAndKeepTheirModel() {
        val json = JSONArray().put(JSONObject().put("id", "legacy").put("name", "Existing")
            .put("endpoint", "https://example.com/v1").put("selectedModel", "chosen"))
        context.getSharedPreferences("providers_prefs", 0).edit().putString("providers_json", json.toString()).commit()
        val provider = ProviderManager(context).getProviders().single()
        assertEquals(ProviderProtocol.OPENAI_COMPATIBLE, provider.protocol)
        assertEquals("chosen", provider.selectedModel)
    }
    @Test fun newProtocolsSurvivePersistenceAndModelUpdates() {
        val manager = ProviderManager(context)
        val provider = manager.addProvider("Gemini", "https://generativelanguage.googleapis.com/v1beta", ProviderProtocol.GEMINI_API_KEY)
        manager.updateProvider(provider.id, selectedModel = "gemini-flash")
        val restored = ProviderManager(context).getProviders().single()
        assertEquals(ProviderProtocol.GEMINI_API_KEY, restored.protocol)
        assertEquals("gemini-flash", restored.selectedModel)
    }

    @Test fun concurrentProviderWritesDoNotLoseOtherProviderSettings() = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.coroutineScope {
            (1..20).map { index ->
                async(kotlinx.coroutines.Dispatchers.Default) {
                    ProviderManager(context).addProvider("Provider $index", "https://example.com/v1")
                }
            }.forEach { it.await() }
        }
        assertEquals(20, ProviderManager(context).getProviders().size)
    }
}
