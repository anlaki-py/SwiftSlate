package com.musheer360.swiftslate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.musheer360.swiftslate.MainActivity
import com.musheer360.swiftslate.R

/**
 * Builds and manages the persistent notification shown while [KeepAliveService]
 * is running as a foreground service.
 *
 * The channel is created once (idempotent on the system side) with
 * [NotificationManager.IMPORTANCE_LOW] so it never makes sound or vibration.
 */
object NotificationHelper {

    /**
     * Creates the keep-alive [NotificationChannel] on API 26+.
     *
     * Safe to call repeatedly — the system ignores duplicate channel
     * registrations with the same ID.
     *
     * @param context Application or service context used to access [NotificationManager].
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NotificationConstants.KEEP_ALIVE_CHANNEL_ID,
            context.getString(R.string.keep_alive_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            // Silent channel — no sound, no badge, no vibration
            description = context.getString(R.string.keep_alive_notification_text)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the persistent foreground notification.
     *
     * Uses [NotificationCompat] for backward compatibility down to API 23.
     * The notification is non-dismissible ([setOngoing]), taps open [MainActivity],
     * and on API 34+ attaches a [deleteIntent] to restart [KeepAliveService]
     * if the user somehow swipes it away.
     *
     * @param context Application or service context.
     * @return A fully built [Notification] ready for [android.app.Service.startForeground].
     */
    fun buildNotification(context: Context): Notification {
        // Tapping the notification opens the main activity
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // API 34+ lets users dismiss even ongoing notifications in some cases;
        // this intent restarts the service if that happens
        val deleteIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, KeepAliveService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, NotificationConstants.KEEP_ALIVE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.keep_alive_notification_title))
            .setContentText(context.getString(R.string.keep_alive_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }
}
