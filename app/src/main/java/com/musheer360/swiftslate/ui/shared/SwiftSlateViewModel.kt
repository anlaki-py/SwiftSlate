package com.musheer360.swiftslate.ui.shared

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.domain.CommandValidation
import com.musheer360.swiftslate.domain.CommandValidationResult
import com.musheer360.swiftslate.domain.KeyValidation
import com.musheer360.swiftslate.data.BackupResult
import com.musheer360.swiftslate.data.CommandManager
import com.musheer360.swiftslate.data.KeyManager
import com.musheer360.swiftslate.data.ProviderManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.service.AccessibilityHelper
import com.musheer360.swiftslate.service.AssistantService
import com.musheer360.swiftslate.service.BatteryOptimizationHelper
import com.musheer360.swiftslate.service.KeepAliveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwiftSlateViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keyManager = KeyManager(application)
    private val commandManager = CommandManager(application)
    private val providerManager = ProviderManager(application)

    private val openAIClient = OpenAICompatibleClient()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _commandsState = MutableStateFlow(CommandsUiState())
    val commandsState: StateFlow<CommandsUiState> = _commandsState.asStateFlow()

    private val _keysState = MutableStateFlow(KeysUiState())
    val keysState: StateFlow<KeysUiState> = _keysState.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    init {
        refreshDashboard()
        refreshCommands()
        refreshKeys()
        refreshSettings()
    }

    // -- Dashboard --

    fun refreshDashboard() {
        val app = getApplication<Application>()
        val isAccessibilityEnabled = AccessibilityHelper.isServiceEnabled(app)
        _dashboardState.value = DashboardUiState(
            isServiceEnabled = isAccessibilityEnabled && AssistantService.isProcessingEnabled(app),
            isAccessibilityEnabled = isAccessibilityEnabled,
            activeProviderName = providerManager.getActiveProvider()?.name,
            keyCount = providerManager.getActiveProvider()?.let { keyManager.getKeys(it.id).size } ?: 0,
            currentPrefix = commandManager.getTriggerPrefix(),
            isBatteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(app)
        )
    }

    fun setServiceEnabled(isEnabled: Boolean) {
        val app = getApplication<Application>()
        AssistantService.setProcessingEnabled(app, isEnabled)
        if (isEnabled) {
            KeepAliveService.allowStart(app)
            KeepAliveService.start(app)
        } else {
            KeepAliveService.stop(app)
        }
        refreshDashboard()
    }

    // -- Commands --

    fun refreshCommands() {
        _commandsState.value = CommandsUiState(
            commands = commandManager.getCommands(),
            prefix = commandManager.getTriggerPrefix()
        )
    }

    fun saveCommand(
        trigger: String,
        prompt: String,
        description: String,
        type: CommandType,
        editingTrigger: String?,
        editingBuiltInKey: String?
    ): String? {
        val trimmedTrigger = trigger.trim()
        val prefix = commandManager.getTriggerPrefix()
        val currentCommands = commandManager.getCommands()

        val result = CommandValidation.validate(trimmedTrigger, prefix, currentCommands, editingTrigger)
        if (result is CommandValidationResult.Error) return result.messageKey

        if (editingBuiltInKey != null) {
            val saveTrigger = if (editingBuiltInKey == "translate") {
                trimmedTrigger.trimEnd(':')
            } else trimmedTrigger
            commandManager.overrideBuiltInCommand(editingBuiltInKey, saveTrigger, prompt.trim(), description.trim())
        } else {
            if (editingTrigger != null) {
                commandManager.removeCustomCommand(editingTrigger)
            }
            commandManager.addCustomCommand(
                Command(trimmedTrigger, prompt.trim(), false, type, description = description.trim())
            )
        }
        refreshCommands()
        return null
    }

    fun deleteCommand(command: Command) {
        if (command.builtInKey != null) {
            commandManager.deleteBuiltInCommand(command.builtInKey)
        } else {
            commandManager.removeCustomCommand(command.trigger)
        }
        refreshCommands()
    }

    fun resetBuiltInCommand(builtInKey: String) {
        commandManager.resetBuiltInCommand(builtInKey)
        refreshCommands()
    }

    fun isUndeletable(builtInKey: String): Boolean = commandManager.isUndeletable(builtInKey)
    fun isBuiltInOverridden(builtInKey: String): Boolean = commandManager.isBuiltInOverridden(builtInKey)
    fun getTriggerPrefix(): String = commandManager.getTriggerPrefix()

    // -- Keys --

    fun refreshKeys() {
        val provider = providerManager.getActiveProvider()
        if (provider == null) {
            _keysState.value = KeysUiState(keystoreAvailable = keyManager.keystoreAvailable)
            return
        }
        _keysState.value = KeysUiState(
            providerId = provider.id,
            providerName = provider.name,
            providerEndpoint = provider.endpoint,
            keys = keyManager.getKeys(provider.id),
            keystoreAvailable = keyManager.keystoreAvailable
        )
    }

    fun addKey(key: String) {
        val providerId = _keysState.value.providerId ?: return
        val trimmedKey = key.trim()
        val existingKeys = keyManager.getKeys(providerId)

        viewModelScope.launch {
            val result = KeyValidation.validate(
                key = trimmedKey,
                endpoint = _keysState.value.providerEndpoint,
                existingKeys = existingKeys,
                client = openAIClient,
                fallbackErrorMessage = "Validation failed"
            )
            when (result) {
                is com.musheer360.swiftslate.domain.KeyValidationResult.Duplicate -> {
                    _keysState.value = _keysState.value.copy(
                        isTesting = false,
                        testResult = "Key already added",
                        testSuccess = false
                    )
                }
                is com.musheer360.swiftslate.domain.KeyValidationResult.Invalid -> {
                    _keysState.value = _keysState.value.copy(
                        isTesting = false,
                        testResult = result.message,
                        testSuccess = false
                    )
                }
                is com.musheer360.swiftslate.domain.KeyValidationResult.Valid -> {
                    if (!keyManager.addKey(providerId, trimmedKey)) {
                        _keysState.value = _keysState.value.copy(
                            isTesting = false,
                            testResult = "Keystore error",
                            testSuccess = false
                        )
                        return@launch
                    }
                    _keysState.value = _keysState.value.copy(
                        isTesting = false,
                        testResult = "Key added successfully",
                        testSuccess = true,
                        keys = keyManager.getKeys(providerId)
                    )
                }
            }
        }
    }

    fun setKeyTesting(isTesting: Boolean) {
        _keysState.value = _keysState.value.copy(isTesting = isTesting)
    }

    fun clearKeyTestResult() {
        _keysState.value = _keysState.value.copy(testResult = null, testSuccess = false)
    }

    fun removeKey(key: String) {
        val providerId = _keysState.value.providerId ?: return
        if (keyManager.removeKey(providerId, key)) {
            _keysState.value = _keysState.value.copy(keys = keyManager.getKeys(providerId))
        }
    }

    // -- Settings --

    fun refreshSettings() {
        val providers = providerManager.getProviders()
        val active = providerManager.getActiveProvider()
        _settingsState.value = SettingsUiState(
            providers = providers.map { SettingsProviderItem(it.id, it.name) },
            activeProviderId = active?.id,
            activeProviderName = active?.name,
            activeProviderEndpoint = active?.endpoint ?: "",
            activeProviderModel = active?.selectedModel ?: "",
            temperature = prefs.getFloat("temperature", 0.7f),
            timeout = prefs.getFloat("timeout", 10f),
            prefix = commandManager.getTriggerPrefix()
        )
    }

    fun updateTemperature(value: Float) {
        prefs.edit().putFloat("temperature", value).apply()
        _settingsState.value = _settingsState.value.copy(temperature = value)
    }

    fun updateTimeout(value: Float) {
        prefs.edit().putFloat("timeout", value).apply()
        _settingsState.value = _settingsState.value.copy(timeout = value)
    }

    fun addProvider(name: String, endpoint: String): Provider {
        val provider = providerManager.addProvider(name, endpoint)
        refreshSettings()
        refreshKeys()
        return provider
    }

    fun updateProvider(id: String, name: String? = null, endpoint: String? = null, selectedModel: String? = null): Boolean {
        val result = providerManager.updateProvider(id, name, endpoint, selectedModel)
        refreshSettings()
        refreshKeys()
        return result
    }

    fun removeProvider(id: String): Boolean {
        keyManager.removeKeysForProvider(id)
        val result = providerManager.removeProvider(id)
        refreshSettings()
        refreshKeys()
        return result
    }

    fun setActiveProvider(id: String) {
        providerManager.setActiveProvider(id)
        refreshSettings()
        refreshKeys()
    }

    fun getFetchedModels(apiKey: String, endpoint: String, onResult: (Result<List<String>>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                com.musheer360.swiftslate.api.ModelFetcher.fetchModels(apiKey, endpoint)
            }
            onResult(result)
        }
    }

    fun getNextKey(providerId: String): String? = keyManager.getNextKey(providerId)
    fun getKeys(providerId: String): List<String> = keyManager.getKeys(providerId)

    fun setTriggerPrefix(newPrefix: String): Boolean {
        val result = commandManager.setTriggerPrefix(newPrefix)
        refreshCommands()
        refreshSettings()
        return result
    }

    fun exportCommands(): String = commandManager.exportCommands()

    fun importCommands(json: String): BackupResult {
        val result = commandManager.importCommands(json)
        if (result is BackupResult.Success) {
            refreshCommands()
            refreshDashboard()
            refreshKeys()
            refreshSettings()
        }
        return result
    }
}
