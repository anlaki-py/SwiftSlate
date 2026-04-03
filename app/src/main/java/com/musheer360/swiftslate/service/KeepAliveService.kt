package com.musheer360.swiftslate.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.musheer360.swiftslate.MainActivity
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.SwiftSlateApp

/**
 * KeepAliveService - Companion foreground service for AssistantService.
 *
 * This service provides a layered defense against process death:
 * - Holds a foreground slot, keeping process priority elevated
 * - Shows a persistent notification to signal ongoing work to OEM battery managers
 * - Uses START_STICKY for automatic restart after OS kills
 * - Implements onTaskRemoved with AlarmManager fallback for swipe-from-recents edge case
 */
class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately with persistent notification
        startForeground(SwiftSlateApp.NOTIFICATION_ID, buildNotification())

        // START_STICKY: if OS kills this service, it will be restarted
        // automatically with a null intent - no data loss since we hold no state
        return START_STICKY
    }

    // Not a bound service - return null
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when user swipes the app from recents.
     * Schedules a self-restart via AlarmManager to cover the gap window
     * before START_STICKY kicks in on older or heavily modified ROMs.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        // Schedule restart after 1 second
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )

        super.onTaskRemoved(rootIntent)
    }

    /**
     * Builds the persistent notification for the foreground service.
     * Uses low priority to avoid intrusive heads-up notifications.
     */
    private fun buildNotification(): Notification {
        // Tapping notification opens MainActivity
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SwiftSlateApp.CHANNEL_ID)
            .setContentTitle("SwiftSlate is active")
            .setContentText("Type a command like ?fix or ?improve in any app")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true) // Cannot be dismissed by user
            .setSilent(true) // No sound even if device is loud
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
