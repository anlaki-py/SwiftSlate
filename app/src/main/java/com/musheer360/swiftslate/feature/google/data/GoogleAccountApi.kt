package com.musheer360.swiftslate.feature.google.data

import com.musheer360.swiftslate.api.AiHttpResponse
import com.musheer360.swiftslate.api.AiHttpTransport
import com.musheer360.swiftslate.api.GeminiProtocol
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** All account requests stay on the same backend so onboarding and generation share entitlements. */
internal class GoogleAccountApi(
    private val http: AiHttpTransport,
    private val settings: GoogleOAuthSettings
) {
    suspend fun exchange(code: String, verifier: String): JSONObject = token(FormBody.Builder()
        .add("code", code).add("code_verifier", verifier).add("grant_type", "authorization_code")
        .add("redirect_uri", GoogleOAuthSettings.REDIRECT_URI))

    suspend fun refresh(credential: GoogleCredential): GoogleCredential {
        val token = token(FormBody.Builder().add("refresh_token", credential.refreshToken).add("grant_type", "refresh_token"))
        if (token.optString("access_token").isBlank()) throw GoogleException(GoogleFailure.INVALID_RESPONSE)
        return credential.copy(accessToken = token.getString("access_token"),
            refreshToken = token.optString("refresh_token").ifBlank { credential.refreshToken },
            expiresAt = expiry(token), account = credential.account.copy(needsLogin = false))
    }

    suspend fun credential(token: JSONObject): GoogleCredential {
        val access = token.getString("access_token").also { require(it.isNotBlank()) }
        val refresh = token.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: throw GoogleException(GoogleFailure.LOGIN_REQUIRED)
        val identity = request(Request.Builder().url("https://www.googleapis.com/oauth2/v2/userinfo")
            .header("Authorization", "Bearer $access").get().build())
        val id = identity.getString("id").also { require(it.isNotBlank()) }
        val email = identity.getString("email").also { require(it.isNotBlank()) }
        val project = discoverProject(access)
        return GoogleCredential(GoogleAccount(id, email, identity.optString("name").ifBlank { email }),
            project, access, refresh, expiry(token))
    }

    suspend fun models(credential: GoogleCredential): JSONObject = post("fetchAvailableModels", credential.accessToken,
        JSONObject().put("project", credential.projectId))

    suspend fun generate(credential: GoogleCredential, model: String, prompt: String, text: String, temperature: Double): String {
        val body = JSONObject().put("project", credential.projectId).put("model", model)
            .put("requestType", "agent").put("userAgent", "antigravity")
            .put("requestId", "agent-${UUID.randomUUID()}")
            .put("request", GeminiProtocol.request(prompt, text, temperature).put("sessionId", UUID.randomUUID().toString()))
        val response = execute(builder("streamGenerateContent?alt=sse", credential.accessToken)
            .header("Accept", "text/event-stream").post(body.toString().toRequestBody(JSON)).build())
        if (!response.isSuccessful) throw failure(response)
        return GeminiProtocol.text(response.body)
    }

    private suspend fun discoverProject(access: String): String {
        val metadata = JSONObject().put("ideType", "ANTIGRAVITY")
        val load = post("loadCodeAssist", access, JSONObject().put("metadata", metadata))
        projectId(load.opt("cloudaicompanionProject"))?.let { return it }
        val tiers = load.optJSONArray("allowedTiers")
        val tier = load.optJSONObject("currentTier")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: (0 until (tiers?.length() ?: 0)).mapNotNull { tiers?.optJSONObject(it) }
                .firstOrNull { it.optBoolean("isDefault") }?.optString("id")?.takeIf { it.isNotBlank() }
            ?: "legacy-tier"
        repeat(5) { attempt ->
            if (attempt > 0) delay(2000)
            val operation = post("onboardUser", access, JSONObject().put("metadata", metadata).put("tierId", tier), onboard = true)
            if (operation.has("error")) throw GoogleException(GoogleFailure.ACCESS_DENIED)
            if (operation.optBoolean("done")) {
                projectId(operation.optJSONObject("response")?.opt("cloudaicompanionProject"))?.let { return it }
                throw GoogleException(GoogleFailure.ACCESS_DENIED)
            }
        }
        throw GoogleException(GoogleFailure.SERVER)
    }

    private suspend fun token(form: FormBody.Builder): JSONObject {
        if (!settings.isConfigured) throw GoogleException(GoogleFailure.CONFIGURATION)
        val response = execute(Request.Builder().url(GoogleOAuthSettings.TOKEN_URL)
            .post(form.add("client_id", settings.clientId).add("client_secret", settings.clientSecret).build()).build())
        if (!response.isSuccessful) {
            val error = runCatching { JSONObject(response.body).optString("error") }.getOrDefault("")
            if (error in setOf("invalid_grant", "invalid_token")) throw GoogleException(GoogleFailure.LOGIN_REQUIRED, response.code)
            if (error == "invalid_client") throw GoogleException(GoogleFailure.CONFIGURATION, response.code)
            throw failure(response)
        }
        return JSONObject(response.body)
    }

    private suspend fun post(method: String, token: String, body: JSONObject, onboard: Boolean = false): JSONObject {
        val request = builder(method, token)
        if (onboard) request.header("User-Agent", GoogleOAuthSettings.USER_AGENT + " google-api-nodejs-client/10.3.0")
            .header("X-Goog-Api-Client", "gl-node/22.21.1")
        return request(request.post(body.toString().toRequestBody(JSON)).build())
    }

    private fun builder(method: String, token: String): Request.Builder = Request.Builder()
        .url("${GoogleOAuthSettings.ENDPOINT}/v1internal:$method")
        .header("Authorization", "Bearer $token").header("User-Agent", GoogleOAuthSettings.USER_AGENT)
        .header("Accept", "application/json")

    private suspend fun request(request: Request): JSONObject {
        val response = execute(request)
        if (!response.isSuccessful) throw failure(response)
        return JSONObject(response.body)
    }

    private suspend fun execute(request: Request): AiHttpResponse = try {
        http.execute(request)
    } catch (error: IOException) {
        throw GoogleException(GoogleFailure.NETWORK)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun projectId(value: Any?): String? = when (value) {
            is String -> value.takeIf { it.isNotBlank() }
            is JSONObject -> value.optString("id").takeIf { it.isNotBlank() }
            else -> null
        }

        fun expiry(token: JSONObject, now: Long = System.currentTimeMillis()): Long {
            val seconds = token.optLong("expires_in", 3600).coerceIn(1, 86400)
            return now + seconds * 1000
        }

        fun failure(response: AiHttpResponse): GoogleException {
            val root = runCatching { JSONObject(response.body).optJSONObject("error") }.getOrNull()
            val message = root?.optString("message").orEmpty().lowercase()
            val details = root?.optJSONArray("details")
            var retry = response.headers["Retry-After"]?.toLongOrNull()?.coerceIn(1, 86400)?.times(1000) ?: 60_000L
            var verification: String? = null
            for (index in 0 until (details?.length() ?: 0)) {
                val detail = details?.optJSONObject(index) ?: continue
                detail.optString("retryDelay").removeSuffix("s").toDoubleOrNull()?.let {
                    if (it.isFinite()) retry = (it.coerceIn(1.0, 86400.0) * 1000).toLong()
                }
                val metadata = detail.optJSONObject("metadata")
                val candidate = metadata?.optString("validation_url")?.ifBlank { null }
                    ?: metadata?.optString("validationUrl")?.ifBlank { null }
                val url = candidate?.let { runCatching { it.toHttpUrl() }.getOrNull() }
                if (url?.isHttps == true && (url.host == "google.com" || url.host.endsWith(".google.com")) &&
                    url.username.isEmpty() && url.password.isEmpty()) verification = url.toString()
            }
            val reason = when {
                verification != null -> GoogleFailure.VERIFY_ACCOUNT
                "version" in message && ("not supported" in message || "update" in message) -> GoogleFailure.CLIENT_OUTDATED
                response.code == 401 -> GoogleFailure.LOGIN_REQUIRED
                response.code == 403 -> GoogleFailure.ACCESS_DENIED
                response.code == 429 -> GoogleFailure.RATE_LIMITED
                response.code == 404 -> GoogleFailure.MODEL_UNAVAILABLE
                response.code >= 500 -> GoogleFailure.SERVER
                else -> GoogleFailure.INVALID_RESPONSE
            }
            return GoogleException(reason, response.code, retry, verification)
        }
    }
}
