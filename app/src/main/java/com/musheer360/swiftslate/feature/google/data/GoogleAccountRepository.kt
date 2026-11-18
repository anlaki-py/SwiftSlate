package com.musheer360.swiftslate.feature.google.data

import com.musheer360.swiftslate.api.GeminiProtocol
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import com.musheer360.swiftslate.feature.google.domain.SelectGoogleAccount
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Coordinates encrypted storage and Google's APIs. Global state is never locked during network
 * I/O; each account has its own refresh lock to prevent concurrent refresh-token races.
 */
class GoogleAccountRepository internal constructor(
    private val store: GoogleAccountStore,
    private val api: GoogleAccountApi
) {
    private val mutex = Mutex()
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()
    private var credentials: List<GoogleCredential>? = null
    private var cursor = 0
    private val _accounts = MutableStateFlow<List<GoogleAccount>>(emptyList())
    val accounts = _accounts.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { loadLocked() } }

    internal suspend fun saveLogin(token: JSONObject): GoogleAccount {
        val credential = api.credential(token)
        mutate { current ->
            val previous = current.firstOrNull { it.account.id == credential.account.id }
            current.filterNot { it.account.id == credential.account.id } +
                credential.copy(account = credential.account.copy(isEnabled = previous?.account?.isEnabled ?: true))
        }
        return credential.account
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = update(id) { it.copy(account = it.account.copy(isEnabled = enabled)) }
    suspend fun remove(id: String) { mutate { it.filterNot { credential -> credential.account.id == id } } }

    /** Forces token renewal and refreshes the account's visible model/quota state. */
    suspend fun refreshAccount(id: String) {
        val fresh = fresh(id, force = true)
        val models = api.models(fresh)
        update(id) { it.copy(account = it.account.copy(remainingFraction = GeminiProtocol.remainingFraction(models),
            cooldownUntil = 0, verificationUrl = null)) }
    }

    suspend fun models(): List<String> = withAccount { credential ->
        val models = api.models(credential)
        update(credential.account.id) { it.copy(account = it.account.copy(remainingFraction = GeminiProtocol.remainingFraction(models))) }
        GeminiProtocol.modelIds(models)
    }

    suspend fun generate(model: String, prompt: String, text: String, temperature: Double): String =
        withAccount { api.generate(it, model, prompt, text, temperature) }

    private suspend fun <T> withAccount(block: suspend (GoogleCredential) -> T): T {
        load()
        val attempted = mutableSetOf<String>()
        var lastFailure: GoogleException? = null
        val limit = accounts.value.size
        repeat(limit) {
            val candidate = withContext(Dispatchers.IO) { mutex.withLock {
                val public = credentials.orEmpty().map { it.account }
                SelectGoogleAccount(public, cursor, attempted, System.currentTimeMillis())?.also {
                    cursor = (public.indexOf(it) + 1).mod(public.size)
                }
            } } ?: return@repeat
            attempted.add(candidate.id)
            try {
                var credential = fresh(candidate.id)
                try {
                    return block(credential)
                } catch (error: GoogleException) {
                    if (error.status != 401) throw error
                    credential = fresh(candidate.id, force = true)
                    return block(credential)
                }
            } catch (error: GoogleException) {
                lastFailure = error
                when (error.failure) {
                    GoogleFailure.LOGIN_REQUIRED -> update(candidate.id) { it.copy(account = it.account.copy(needsLogin = true)) }
                    GoogleFailure.RATE_LIMITED -> update(candidate.id) { it.copy(account = it.account.copy(
                        cooldownUntil = System.currentTimeMillis() + error.retryAfterMillis)) }
                    GoogleFailure.VERIFY_ACCOUNT -> update(candidate.id) { it.copy(account = it.account.copy(verificationUrl = error.verificationUrl)) }
                    GoogleFailure.ACCESS_DENIED, GoogleFailure.MODEL_UNAVAILABLE -> Unit
                    else -> throw error
                }
            }
        }
        if (lastFailure != null) throw lastFailure!!
        val all = accounts.value
        throw GoogleException(when {
            all.isEmpty() -> GoogleFailure.NO_ACCOUNTS
            all.none { it.isEnabled } -> GoogleFailure.ALL_DISABLED
            all.any { it.isEnabled && it.verificationUrl != null } -> GoogleFailure.VERIFY_ACCOUNT
            all.any { it.isEnabled && it.needsLogin } -> GoogleFailure.LOGIN_REQUIRED
            else -> GoogleFailure.RATE_LIMITED
        })
    }

    private suspend fun fresh(id: String, force: Boolean = false): GoogleCredential =
        refreshLocks.getOrPut(id) { Mutex() }.withLock {
            val current = withContext(Dispatchers.IO) { mutex.withLock {
                loadLocked()
                credentials.orEmpty().firstOrNull { it.account.id == id }
            } } ?: throw GoogleException(GoogleFailure.NO_ACCOUNTS)
            if (!force && current.account.needsLogin) throw GoogleException(GoogleFailure.LOGIN_REQUIRED)
            if (!force && current.expiresAt > System.currentTimeMillis() + 60_000) return@withLock current
            try {
                val refreshed = api.refresh(current)
                update(id) { refreshed.copy(account = refreshed.account.copy(isEnabled = it.account.isEnabled)) }
                refreshed
            } catch (error: GoogleException) {
                if (error.failure == GoogleFailure.LOGIN_REQUIRED) update(id) { it.copy(account = it.account.copy(needsLogin = true)) }
                throw error
            }
        }

    private suspend fun update(id: String, change: (GoogleCredential) -> GoogleCredential) {
        mutate { current -> current.map { if (it.account.id == id) change(it) else it } }
    }

    private suspend fun mutate(change: (List<GoogleCredential>) -> List<GoogleCredential>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val next = change(credentials.orEmpty())
            store.write(next)
            credentials = next
            _accounts.value = next.map { it.account }
        }
    }

    private fun loadLocked() {
        if (credentials == null) {
            credentials = store.read()
            _accounts.value = credentials.orEmpty().map { it.account }
        }
    }
}
