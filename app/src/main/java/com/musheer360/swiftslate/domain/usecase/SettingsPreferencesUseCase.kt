package com.musheer360.swiftslate.domain.usecase

import android.content.Context

class SettingsPreferencesUseCase(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getTemperature(): Float = prefs.getFloat("temperature", 0.7f)
    
    fun setTemperature(temp: Float) {
        prefs.edit().putFloat("temperature", temp).apply()
    }
}
