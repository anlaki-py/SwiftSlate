package com.musheer360.swiftslate.api

import android.content.Context
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.data.KeyManager
import com.musheer360.swiftslate.data.ProviderManager
import com.musheer360.swiftslate.feature.google.data.GoogleAccountApi
import com.musheer360.swiftslate.feature.google.data.GoogleAccountRepository
import com.musheer360.swiftslate.feature.google.data.GoogleCredentialStore
import com.musheer360.swiftslate.feature.google.data.GoogleLoginSession
import com.musheer360.swiftslate.feature.google.data.GoogleOAuthSettings
import com.musheer360.swiftslate.model.ProviderProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Application-scoped networking and accounts, shared by Settings and the accessibility service. */
class AiServices(private val applicationContext: Context) {
    private val http = AiHttpTransport()
    val openAi = OpenAICompatibleClient(http)
    val gemini = GeminiApiKeyClient(http)
    private val settings = GoogleOAuthSettings()
    private val api = GoogleAccountApi(http, settings)
    val accounts = GoogleAccountRepository(GoogleCredentialStore(applicationContext), api)
    internal val login = GoogleLoginSession(settings, api, accounts)
    val requests = AiRequestRouter({ KeyManager(applicationContext) }, openAi, gemini, accounts)

    suspend fun activateGoogleProvider() = withContext(Dispatchers.IO) {
        val providers = ProviderManager(applicationContext)
        val google = providers.getProviders().firstOrNull { it.protocol == ProviderProtocol.GOOGLE_ACCOUNT }
            ?: providers.addProvider(applicationContext.getString(R.string.google_provider_name),
                GoogleOAuthSettings.ENDPOINT, ProviderProtocol.GOOGLE_ACCOUNT)
        providers.setActiveProvider(google.id)
        if (google.selectedModel.isBlank()) {
            val models = accounts.models()
            val selected = models.firstOrNull { "flash" in it && "preview" !in it }
                ?: models.firstOrNull { "flash" in it } ?: models.firstOrNull()
            if (selected != null) providers.updateProvider(google.id, selectedModel = selected)
        }
    }
}
