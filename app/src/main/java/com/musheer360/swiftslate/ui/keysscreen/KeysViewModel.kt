package com.musheer360.swiftslate.ui.keysscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.domain.usecase.AddKeyStatus
import com.musheer360.swiftslate.domain.usecase.ManageKeyUseCase
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.model.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeysUiState(
    val providers: List<AiProvider> = emptyList(),
    val selectedProvider: AiProvider? = null,
    val keys: List<String> = emptyList(),
    val newKey: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val keyToDelete: String? = null,
    val keystoreAvailable: Boolean = true
)

class KeysViewModel(
    private val keyManager: KeyManager,
    private val providerManager: ProviderManager,
    private val manageKeyUseCase: ManageKeyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeysUiState(keystoreAvailable = keyManager.keystoreAvailable))
    val uiState: StateFlow<KeysUiState> = _uiState.asStateFlow()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        val providers = providerManager.getProviders()
        val activeProvider = providerManager.getActiveProvider() ?: providers.firstOrNull()
        val keys = activeProvider?.id?.let { keyManager.getKeys(it) } ?: emptyList()
        _uiState.update { 
            it.copy(providers = providers, selectedProvider = activeProvider, keys = keys) 
        }
    }

    fun selectProvider(provider: AiProvider) {
        val keys = keyManager.getKeys(provider.id)
        _uiState.update { 
            it.copy(
                selectedProvider = provider, 
                keys = keys,
                testResult = null,
                newKey = ""
            ) 
        }
    }

    fun setNewKey(key: String) {
        if (key.length <= 256) {
            _uiState.update { it.copy(newKey = key) }
        }
    }
    
    fun setKeyToDelete(key: String?) {
        _uiState.update { it.copy(keyToDelete = key) }
    }

    fun addKey(
        validAddedMsg: String,
        alreadyAddedMsg: String
    ) {
        val currentState = _uiState.value
        val provider = currentState.selectedProvider ?: return
        val key = currentState.newKey.trim()
        if (key.isBlank()) return

        _uiState.update { it.copy(isTesting = true, testResult = null) }

        viewModelScope.launch {
            val status = manageKeyUseCase.addKey(provider, key)

            when (status) {
                is AddKeyStatus.Duplicate -> {
                    _uiState.update { it.copy(testResult = alreadyAddedMsg, testSuccess = false, isTesting = false) }
                }
                is AddKeyStatus.Error -> {
                    _uiState.update { it.copy(testResult = status.message, testSuccess = false, isTesting = false) }
                }
                is AddKeyStatus.Success -> {
                    _uiState.update { 
                        it.copy(
                            keys = status.newKeysList,
                            newKey = "",
                            testResult = validAddedMsg,
                            testSuccess = true,
                            isTesting = false
                        ) 
                    }
                }
            }
        }
    }

    fun removeKey(key: String, keystoreErrorMsg: String) {
        val currentState = _uiState.value
        val provider = currentState.selectedProvider ?: return
        
        viewModelScope.launch {
            val result = manageKeyUseCase.removeKey(provider.id, key)
            if (result.isSuccess) {
                _uiState.update { it.copy(keys = result.getOrThrow(), keyToDelete = null) }
            } else {
                _uiState.update { it.copy(testResult = keystoreErrorMsg, testSuccess = false, keyToDelete = null) }
            }
        }
    }
}
