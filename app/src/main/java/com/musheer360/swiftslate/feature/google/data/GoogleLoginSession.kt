package com.musheer360.swiftslate.feature.google.data

import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl

/** UI progress contains no token or PKCE verifier. Browser navigation is a one-time event. */
data class GoogleLoginState(val phase: GoogleLoginPhase = GoogleLoginPhase.IDLE, val failure: GoogleException? = null)
enum class GoogleLoginPhase { IDLE, WAITING, FINISHING, COMPLETE }

internal class GoogleLoginSession(
    private val settings: GoogleOAuthSettings,
    private val api: GoogleAccountApi,
    private val accounts: GoogleAccountRepository
) {
    private val _state = MutableStateFlow(GoogleLoginState())
    val state = _state.asStateFlow()
    private val _browser = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val browser = _browser.asSharedFlow()
    @Volatile private var authorizationUrl: String? = null

    suspend fun run(successText: String, failureText: String, beforeExchange: suspend () -> Unit, onConnected: suspend () -> Unit) {
        val callback = GoogleOAuthLoopback()
        try {
            if (!settings.isConfigured) throw GoogleException(GoogleFailure.CONFIGURATION)
            withTimeout(5 * 60_000L) {
                val challenge = GoogleOAuthChallenge.create()
                withContext(Dispatchers.IO) { callback.open() }
                authorizationUrl = GoogleOAuthSettings.AUTHORIZE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("client_id", settings.clientId).addQueryParameter("response_type", "code")
                    .addQueryParameter("redirect_uri", GoogleOAuthSettings.REDIRECT_URI)
                    .addQueryParameter("scope", GoogleOAuthSettings.SCOPES)
                    .addQueryParameter("code_challenge", challenge.challenge).addQueryParameter("code_challenge_method", "S256")
                    .addQueryParameter("state", challenge.state).addQueryParameter("access_type", "offline")
                    .addQueryParameter("prompt", "consent select_account").build().toString()
                _state.value = GoogleLoginState(GoogleLoginPhase.WAITING)
                openBrowser()
                val code = callback.awaitCode(challenge.state, successText, failureText)
                withContext(Dispatchers.IO) { callback.close() }
                _state.value = GoogleLoginState(GoogleLoginPhase.FINISHING)
                beforeExchange()
                accounts.saveLogin(api.exchange(code, challenge.verifier))
                onConnected()
                _state.value = GoogleLoginState(GoogleLoginPhase.COMPLETE)
            }
        } catch (error: TimeoutCancellationException) {
            fail(GoogleException(GoogleFailure.LOGIN_TIMEOUT))
        } catch (error: CancellationException) {
            _state.value = GoogleLoginState()
            throw error
        } catch (error: GoogleException) {
            fail(error)
        } catch (error: Exception) {
            android.util.Log.e("GoogleOAuth", "Login failed: ${error.javaClass.simpleName}")
            fail(GoogleException(GoogleFailure.INVALID_RESPONSE))
        } finally {
            authorizationUrl = null
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { callback.close() }
        }
    }

    fun openBrowser() { authorizationUrl?.let { _browser.tryEmit(it) } }
    fun fail(error: GoogleException) { _state.value = GoogleLoginState(failure = error) }
    fun dismissResult() {
        if (_state.value.phase !in setOf(GoogleLoginPhase.WAITING, GoogleLoginPhase.FINISHING)) _state.value = GoogleLoginState()
    }
}
