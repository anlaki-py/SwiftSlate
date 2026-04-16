package com.musheer360.swiftslate.ui.settingsscreen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.domain.usecase.ExportCommandsUseCase
import com.musheer360.swiftslate.domain.usecase.FetchModelsUseCase
import com.musheer360.swiftslate.domain.usecase.ImportCommandsUseCase
import com.musheer360.swiftslate.domain.usecase.SettingsPreferencesUseCase
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.model.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<AiProvider> = emptyList(),
    val activeProvider: AiProvider? = null,
    val models: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val fetchError: String? = null,
    val temperature: Float = 0.7f,
    val triggerPrefix: String = "",
    val backupMessage: String? = null,
    val backupSuccess: Boolean = false
)

class SettingsViewModel(
    private val commandManager: CommandManager,
    private val providerManager: ProviderManager,
    private val fetchModelsUseCase: FetchModelsUseCase,
    private val exportCommandsUseCase: ExportCommandsUseCase,
    private val importCommandsUseCase: ImportCommandsUseCase,
    private val settingsPreferencesUseCase: SettingsPreferencesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { 
            it.copy(temperature = settingsPreferencesUseCase.getTemperature()) 
        }
        refreshProviders()
        refreshPrefix()
    }

    private fun refreshProviders() {
        val all = providerManager.getProviders()
        var active = providerManager.getActiveProvider()
        if (active == null && all.isNotEmpty()) {
            providerManager.setActiveProvider(all.first().id)
            active = all.first()
        }
        _uiState.update { it.copy(providers = all, activeProvider = active, models = emptyList(), fetchError = null) }
    }

    private fun refreshPrefix() {
        _uiState.update { it.copy(triggerPrefix = commandManager.getTriggerPrefix()) }
    }

    fun setActiveProvider(id: String) {
        providerManager.setActiveProvider(id)
        refreshProviders()
    }

    fun updateProviderModel(id: String, model: String) {
        providerManager.updateProviderModel(id, model)
        refreshProviders()
    }

    fun saveProvider(provider: AiProvider) {
        providerManager.addOrUpdateProvider(provider)
        refreshProviders()
    }

    fun deleteProvider(id: String) {
        providerManager.deleteProvider(id)
        refreshProviders()
    }

    fun updateTemperature(temp: Float) {
        settingsPreferencesUseCase.setTemperature(temp)
        _uiState.update { it.copy(temperature = temp) }
    }

    fun updateTriggerPrefix(prefix: String) {
        commandManager.setTriggerPrefix(prefix)
        refreshPrefix()
    }

    fun fetchModels() {
        val state = _uiState.value
        val prov = state.activeProvider ?: return

        _uiState.update { it.copy(isFetchingModels = true, fetchError = null) }
        viewModelScope.launch {
            val result = fetchModelsUseCase(prov)
            if (result.isSuccess) {
                _uiState.update { it.copy(models = result.getOrThrow(), isFetchingModels = false) }
            } else {
                _uiState.update { 
                    it.copy(fetchError = result.exceptionOrNull()?.message ?: "Failed to fetch models", isFetchingModels = false) 
                }
            }
        }
    }

    fun exportCommands(uri: Uri?, successMsg: String, errorMsg: String) {
        if (uri == null) return
        viewModelScope.launch {
            val result = exportCommandsUseCase(uri)
            if (result.isSuccess) {
                _uiState.update { it.copy(backupMessage = successMsg, backupSuccess = true) }
            } else {
                _uiState.update { it.copy(backupMessage = errorMsg, backupSuccess = false) }
            }
        }
    }

    fun importCommands(uri: Uri?, successMsg: String, errorMsg: String) {
        if (uri == null) return
        viewModelScope.launch {
            val result = importCommandsUseCase(uri)
            if (result.isSuccess) {
                _uiState.update { it.copy(backupMessage = successMsg, backupSuccess = true) }
            } else {
                _uiState.update { it.copy(backupMessage = errorMsg, backupSuccess = false) }
            }
        }
    }
    
    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }
}
