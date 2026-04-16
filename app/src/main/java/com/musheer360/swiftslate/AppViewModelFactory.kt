package com.musheer360.swiftslate

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.domain.usecase.*
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.ui.dashboardscreen.DashboardViewModel
import com.musheer360.swiftslate.ui.keysscreen.KeysViewModel
import com.musheer360.swiftslate.ui.settingsscreen.SettingsViewModel

class AppViewModelFactory(
    private val application: Application,
    private val keyManager: KeyManager,
    private val commandManager: CommandManager,
    private val providerManager: ProviderManager
) : ViewModelProvider.Factory {

    private val openAIClient = OpenAICompatibleClient()

    // UseCase instantiations
    private val fetchModelsUseCase = FetchModelsUseCase(keyManager, openAIClient)
    private val exportCommandsUseCase = ExportCommandsUseCase(application, commandManager)
    private val importCommandsUseCase = ImportCommandsUseCase(application, commandManager)
    private val getDashboardMetricsUseCase = GetDashboardMetricsUseCase(application, keyManager, commandManager, providerManager)
    private val manageKeyUseCase = ManageKeyUseCase(application, keyManager, openAIClient)
    private val settingsPreferencesUseCase = SettingsPreferencesUseCase(application)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(getDashboardMetricsUseCase) as T
            }
            modelClass.isAssignableFrom(KeysViewModel::class.java) -> {
                KeysViewModel(keyManager, providerManager, manageKeyUseCase) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    commandManager = commandManager,
                    providerManager = providerManager,
                    fetchModelsUseCase = fetchModelsUseCase,
                    exportCommandsUseCase = exportCommandsUseCase,
                    importCommandsUseCase = importCommandsUseCase,
                    settingsPreferencesUseCase = settingsPreferencesUseCase
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
