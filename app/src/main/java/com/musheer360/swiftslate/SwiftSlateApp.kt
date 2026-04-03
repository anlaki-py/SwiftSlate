package com.musheer360.swiftslate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager

/**
 * SwiftSlateApp - Application class for global initialization.
 *
 * Responsibilities:
 * - Create notification channel for KeepAliveService
 * - Initialize singleton managers (CommandManager, KeyManager) once
 * - Pre-warm managers to avoid slow first keystroke
 */
class SwiftSlateApp : Application() {

    companion object {
        // Notification channel ID for KeepAliveService
        const val CHANNEL_ID = "swiftslate_keepalive"
        // Notification ID for the persistent foreground notification
        const val NOTIFICATION_ID = 1001
    }

    // Lazily initialized singleton managers - safe to access from anywhere
    val commandManager: CommandManager by lazy { CommandManager(this) }
    val keyManager: KeyManager by lazy { KeyManager(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d("SwiftSlateApp", "Application initialized")

        // Create notification channel before any notification can be shown
        createNotificationChannel()

        // Pre-warm command manager so first keystroke isn't slow
        commandManager.getTriggerPrefix()
    }

    /**
     * Creates the notification channel for KeepAliveService.
     * Must be called every app start - Android deduplicates safely.
     * Uses IMPORTANCE_LOW for silent, non-intrusive notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SwiftSlate Active",
                NotificationManager.IMPORTANCE_LOW // No sound, no heads-up
            ).apply {
                description = "Keeps SwiftSlate running in the background"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
