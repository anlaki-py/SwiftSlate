package com.musheer360.swiftslate

import android.app.Application
import android.content.Context
import com.musheer360.swiftslate.service.KeepAliveService
import com.musheer360.swiftslate.service.NotificationHelper

/**
 * Application-level initialisation for SwiftSlate.
 *
 * Responsibilities:
 * - Pre-warms [SharedPreferences] so disk I/O completes before the ViewModel reads them.
 * - Creates the keep-alive notification channel (idempotent, safe to call every launch).
 * - Starts [KeepAliveService] so the process is promoted to foreground priority immediately.
 */
class SwiftSlateApp : Application() {

    /**
     * Initialises SharedPreferences, the notification channel, and the
     * keep-alive foreground service.
     *
     * Called once when the application process is created — before any
     * Activity, Service, or BroadcastReceiver is instantiated.
     */
    override fun onCreate() {
        super.onCreate()

        // Pre-warm SharedPreferences — triggers async disk load so they're
        // in memory by the time the ViewModel creates managers
        getSharedPreferences("settings", Context.MODE_PRIVATE)
        getSharedPreferences("commands", Context.MODE_PRIVATE)
        getSharedPreferences("secure_keys_prefs", Context.MODE_PRIVATE)

        // Create notification channel before any service tries to post
        NotificationHelper.createChannel(this)

        // Start the foreground keep-alive service
        KeepAliveService.start(this)
    }
}

