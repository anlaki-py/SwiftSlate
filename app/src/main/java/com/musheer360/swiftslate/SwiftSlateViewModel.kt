package com.musheer360.swiftslate

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager

/**
 * Application-level ViewModel providing shared managers to all screens.
 *
 * @param application The application instance for context-dependent managers.
 */
class SwiftSlateViewModel(application: Application) : AndroidViewModel(application) {
    val prefs: SharedPreferences = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val keyManager = KeyManager(application)
    val commandManager = CommandManager(application)
    val providerManager = ProviderManager(application)
}
