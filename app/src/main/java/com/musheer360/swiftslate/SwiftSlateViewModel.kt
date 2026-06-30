package com.musheer360.swiftslate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.data.remote.OpenAiClient
import com.musheer360.swiftslate.data.repository.CommandRepository
import com.musheer360.swiftslate.data.repository.KeyRepository
import com.musheer360.swiftslate.data.repository.ProviderRepository
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.Provider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isServiceEnabled: Boolean = false,
    val activeProvider: Provider? = null,
    val keyCount: Int = 0,
    val currentPrefix: String = "?",
    val isBatteryOptimized: Boolean = true
)

@HiltViewModel
class SwiftSlateViewModel @Inject constructor(
    val commandRepository: CommandRepository,
    val keyRepository: KeyRepository,
    val providerRepository: ProviderRepository,
    val openAiClient: OpenAiClient
) : ViewModel() {

    val commands: StateFlow<List<Command>> = commandRepository.observeCommands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providers = providerRepository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProvider = providerRepository.observeActiveProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    fun refreshDashboard(isServiceEnabled: Boolean, isBatteryOptimized: Boolean) {
        viewModelScope.launch {
            val provider = providerRepository.getActiveProvider()
            val keyCount = if (provider != null) keyRepository.getKeys(provider.id).size else 0
            _dashboardState.value = DashboardUiState(
                isServiceEnabled = isServiceEnabled,
                activeProvider = provider,
                keyCount = keyCount,
                currentPrefix = commandRepository.getTriggerPrefix(),
                isBatteryOptimized = isBatteryOptimized
            )
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        _dashboardState.value = _dashboardState.value.copy(isServiceEnabled = enabled)
    }

    fun setBatteryOptimized(optimized: Boolean) {
        _dashboardState.value = _dashboardState.value.copy(isBatteryOptimized = optimized)
    }
}
