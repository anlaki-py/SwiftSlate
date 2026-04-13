package com.musheer360.swiftslate.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * A minimal foreground service whose only purpose is to keep the app process
 * alive at elevated priority so the [AssistantService] (accessibility) is not
 * killed by Doze, App Standby, or aggressive OEM battery optimisers.
 *
 * The service posts a persistent, non-dismissible notification via
 * [NotificationHelper] for its entire lifetime.
 *
 * Lifecycle:
 * - Started from [com.musheer360.swiftslate.SwiftSlateApp.onCreate],
 *   [AssistantService.onServiceConnected], or [BootReceiver].
 * - Returns [START_STICKY] so the system restarts it after an OOM kill.
 * - Re-starts itself in [onTaskRemoved] to survive recent-app swipe.
 */
class KeepAliveService : Service() {

    /**
     * Initialises the notification channel and immediately promotes
     * this service to foreground with a persistent notification.
     *
     * On API 34+ the runtime foreground-service-type is set explicitly
     * to [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE].
     */
    override fun onCreate() {
        super.onCreate()

        // Channel must exist before startForeground() on API 26+
        NotificationHelper.createChannel(this)

        val notification = NotificationHelper.buildNotification(this)

        // API 34+ requires an explicit foreground service type at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationConstants.KEEP_ALIVE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NotificationConstants.KEEP_ALIVE_NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Returns [START_STICKY] so the OS re-creates this service after
     * low-memory kills — the strongest built-in restart guarantee.
     *
     * @param intent  The intent supplied to [Context.startService].
     * @param flags   Additional data about this start request.
     * @param startId A unique integer representing this start request.
     * @return [START_STICKY] to request automatic restart.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Called when the user swipes the app from Recents.
     *
     * Re-starts the service immediately so the process stays alive even
     * after the task is removed.
     *
     * @param rootIntent The original intent that launched the task being removed.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Re-post to survive recent-app swipe
        start(applicationContext)
    }

    /** Not a bound service — always returns `null`. */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Attempts to restart itself when destroyed.
     *
     * This is a last-resort measure — if the system or user force-stops
     * the service, this callback tries to re-launch it. Only effective
     * when battery optimization is disabled for the app.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Attempt self-restart — effective when battery optimization is disabled
        start(applicationContext)
    }

    companion object {

        /**
         * Starts the keep-alive foreground service.
         *
         * Uses [ContextCompat.startForegroundService] which internally calls
         * [Context.startForegroundService] on API 26+ and plain
         * [Context.startService] on older versions.
         *
         * Wrapped in try-catch because Android 12+ throws
         * [android.app.ForegroundServiceStartNotAllowedException] if the
         * app is not in an allowed state (e.g. boot receiver on some OEMs).
         *
         * @param context Any context (application, activity, or service).
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Silently ignore — the service will be started from the
                // next allowed context (Activity, AccessibilityService, etc.)
            }
        }

        /**
         * Stops the keep-alive foreground service.
         *
         * @param context Any context.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
