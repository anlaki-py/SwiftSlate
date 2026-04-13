package com.musheer360.swiftslate.service

/**
 * Centralised constants for the keep-alive foreground notification.
 *
 * Keeps channel IDs and notification IDs in one place so they stay
 * consistent across [NotificationHelper], [KeepAliveService], and
 * [BootReceiver].
 */
object NotificationConstants {

    /** Unique channel ID registered with the system notification manager. */
    const val KEEP_ALIVE_CHANNEL_ID = "swiftslate_keep_alive"

    /**
     * Stable notification ID used for [android.app.Service.startForeground].
     * Must be > 0 and should not collide with any other notification ID.
     */
    const val KEEP_ALIVE_NOTIFICATION_ID = 1001
}
