package com.musheer360.swiftslate.ui.shared

import com.musheer360.swiftslate.model.Command

data class DashboardUiState(
    val isServiceEnabled: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val activeProviderName: String? = null,
    val keyCount: Int = 0,
    val currentPrefix: String = "?",
    val isBatteryOptimized: Boolean = false
)

data class CommandsUiState(
    val commands: List<Command> = emptyList(),
    val prefix: String = "?"
)

data class KeysUiState(
    val providerId: String? = null,
    val providerName: String? = null,
    val providerEndpoint: String = "",
    val keys: List<String> = emptyList(),
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val keystoreAvailable: Boolean = true
)

data class SettingsUiState(
    val providers: List<SettingsProviderItem> = emptyList(),
    val activeProviderId: String? = null,
    val activeProviderName: String? = null,
    val activeProviderEndpoint: String = "",
    val activeProviderModel: String = "",
    val temperature: Float = 0.7f,
    val timeout: Float = 10f,
    val prefix: String = "?"
)

data class SettingsProviderItem(
    val id: String,
    val name: String
)
