package com.musheer360.swiftslate.ui.shared

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.SwiftSlateApp
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.data.BackupResult
import com.musheer360.swiftslate.data.CommandManager
import com.musheer360.swiftslate.data.KeyManager
import com.musheer360.swiftslate.data.ProviderManager
import com.musheer360.swiftslate.domain.CommandValidation
import com.musheer360.swiftslate.domain.CommandValidationResult
import com.musheer360.swiftslate.domain.KeyValidation
import com.musheer360.swiftslate.domain.KeyValidationResult
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.model.ProviderProtocol
import com.musheer360.swiftslate.service.AccessibilityHelper
import com.musheer360.swiftslate.service.AssistantService
import com.musheer360.swiftslate.service.BatteryOptimizationHelper
import com.musheer360.swiftslate.service.ErrorMessageMapper
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

    private val services = (application as SwiftSlateApp).aiServices
    private val openAIClient = services.openAi
    private var modelJob: kotlinx.coroutines.Job? = null
    private val _modelState = MutableStateFlow(ModelPickerUiState())
    val modelState = _modelState.asStateFlow()

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
        val provider = providerManager.getActiveProvider()
        val app = getApplication<Application>()
        val isAccessibilityEnabled = AccessibilityHelper.isServiceEnabled(app)
        _dashboardState.value = DashboardUiState(
            isServiceEnabled = isAccessibilityEnabled && AssistantService.isProcessingEnabled(app),
            isAccessibilityEnabled = isAccessibilityEnabled,
            activeProviderName = providerManager.getActiveProvider()?.name,
            keyCount = if (provider?.protocol == ProviderProtocol.GOOGLE_ACCOUNT)
                services.accounts.accounts.value.size else provider?.let { keyManager.getKeys(it.id).size } ?: 0,
            usesGoogleAccounts = provider?.protocol == ProviderProtocol.GOOGLE_ACCOUNT,
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
            keystoreAvailable = keyManager.keystoreAvailable,
            usesGoogleAccounts = provider.protocol == ProviderProtocol.GOOGLE_ACCOUNT
        )
    }

    fun addKey(key: String) {
        val providerId = _keysState.value.providerId ?: return
        val trimmedKey = key.trim()
        val existingKeys = keyManager.getKeys(providerId)

        viewModelScope.launch {
            val native = providerManager.getProviders().firstOrNull { it.id == providerId }?.protocol ==
                ProviderProtocol.GEMINI_API_KEY
            val result = if (native) {
                if (trimmedKey in existingKeys) KeyValidationResult.Duplicate
                else services.gemini.models(trimmedKey, _keysState.value.providerEndpoint).fold(
                    { KeyValidationResult.Valid },
                    { KeyValidationResult.Invalid(
                        ErrorMessageMapper.describe(getApplication(), it)) })
            } else KeyValidation.validate(
                key = trimmedKey,
                endpoint = _keysState.value.providerEndpoint,
                existingKeys = existingKeys,
                client = openAIClient,
                fallbackErrorMessage = "Validation failed"
            )
            when (result) {
                is KeyValidationResult.Duplicate -> {
                    _keysState.value = _keysState.value.copy(
                        isTesting = false,
                        testResult = "Key already added",
                        testSuccess = false
                    )
                }
                is KeyValidationResult.Invalid -> {
                    _keysState.value = _keysState.value.copy(
                        isTesting = false,
                        testResult = result.cause?.let { ErrorMessageMapper.describe(getApplication(), it) } ?: result.message,
                        testSuccess = false
                    )
                }
                is KeyValidationResult.Valid -> {
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
            providers = providers.map { SettingsProviderItem(it.id, it.name, it.endpoint, it.selectedModel, it.protocol) },
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

    fun addProvider(name: String, endpoint: String, protocol: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE): Provider {
        val provider = providerManager.addProvider(name, endpoint, protocol)
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

    fun fetchProviderModels(providerId: String) {
        val provider = providerManager.getProviders().firstOrNull { it.id == providerId } ?: return
        modelJob?.cancel()
        _modelState.value = ModelPickerUiState(providerId, isLoading = true)
        modelJob = viewModelScope.launch {
            val response = services.requests.models(provider)
            _modelState.value = ModelPickerUiState(providerId, models = response.getOrDefault(emptyList()),
                error = response.exceptionOrNull()?.let { ErrorMessageMapper.describe(getApplication(), it) })
        }
    }

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
