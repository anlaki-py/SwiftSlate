package com.musheer360.swiftslate.domain.usecase

import android.content.Context
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.model.AiProvider
import com.musheer360.swiftslate.service.AccessibilityHelper
import com.musheer360.swiftslate.service.BatteryOptimizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DashboardMetrics(
    val isServiceEnabled: Boolean,
    val currentPrefix: String,
    val isBatteryOptimized: Boolean
)

class GetDashboardMetricsUseCase(
    private val context: Context,
    private val keyManager: KeyManager,
    private val commandManager: CommandManager,
    private val providerManager: ProviderManager
) {
    suspend operator fun invoke(): DashboardMetrics = withContext(Dispatchers.IO) {
        val isServiceEnabled = AccessibilityHelper.isServiceEnabled(context)
        val currentPrefix = commandManager.getTriggerPrefix()
        val isBatteryOptimized = !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        
        DashboardMetrics(
            isServiceEnabled = isServiceEnabled,
            currentPrefix = currentPrefix,
            isBatteryOptimized = isBatteryOptimized
        )
    }
}
