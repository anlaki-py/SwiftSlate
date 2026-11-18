package com.musheer360.swiftslate.feature.google.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.SwiftSlateApp
import com.musheer360.swiftslate.feature.google.data.GoogleLoginState
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Account-screen state contains only display identity, health, progress, and sanitized errors. */
data class GoogleAccountsUiState(
    val accounts: List<GoogleAccount> = emptyList(),
    val login: GoogleLoginState = GoogleLoginState(),
    val isWorking: Boolean = false,
    val error: GoogleException? = null
)

class GoogleAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val services = (application as SwiftSlateApp).aiServices
    private val operation = MutableStateFlow(GoogleAccountsUiState())
    val state = combine(services.accounts.accounts, services.login.state, operation) { accounts, login, current ->
        current.copy(accounts = accounts, login = login)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoogleAccountsUiState())
    val browser = services.login.browser

    init { load() }
    fun load() = perform { services.accounts.load() }
    fun setEnabled(id: String, enabled: Boolean) = perform { services.accounts.setEnabled(id, enabled) }
    fun remove(id: String) = perform { services.accounts.remove(id) }
    fun refresh(id: String) = perform { services.accounts.refreshAccount(id) }
    fun activate() = perform { services.activateGoogleProvider() }
    fun openBrowser() { services.login.openBrowser() }
    fun dismissError() { operation.value = operation.value.copy(error = null); services.login.dismissResult() }
    fun browserFailed() { operation.value = operation.value.copy(error = GoogleException(GoogleFailure.CONFIGURATION)) }

    private fun perform(block: suspend () -> Unit) {
        if (operation.value.isWorking) return
        operation.value = operation.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                operation.value = operation.value.copy(error = error as? GoogleException
                    ?: GoogleException(GoogleFailure.INVALID_RESPONSE))
            } finally {
                operation.value = operation.value.copy(isWorking = false)
            }
        }
    }
}
