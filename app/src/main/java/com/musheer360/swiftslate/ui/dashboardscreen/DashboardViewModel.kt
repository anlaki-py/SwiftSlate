package com.musheer360.swiftslate.ui.dashboardscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musheer360.swiftslate.domain.usecase.GetDashboardMetricsUseCase
import com.musheer360.swiftslate.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isServiceEnabled: Boolean = false,
    val currentPrefix: String = "",
    val isBatteryOptimized: Boolean = false
)

class DashboardViewModel(
    private val getDashboardMetricsUseCase: GetDashboardMetricsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        syncState()
        startPolling()
    }

    fun syncState() {
        viewModelScope.launch {
            val metrics = getDashboardMetricsUseCase()

            _uiState.update { currentState ->
                currentState.copy(
                    isServiceEnabled = metrics.isServiceEnabled,
                    currentPrefix = metrics.currentPrefix,
                    isBatteryOptimized = metrics.isBatteryOptimized
                )
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(3000)
                syncState()
            }
        }
    }
}
