package com.musheer360.swiftslate.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts [KeepAliveService] automatically after device boot or app update.
 *
 * Registered in the manifest for [Intent.ACTION_BOOT_COMPLETED] and
 * [Intent.ACTION_MY_PACKAGE_REPLACED] so the foreground service resumes
 * without the user having to open the app first.
 */
class BootReceiver : BroadcastReceiver() {

    /**
     * Handles the incoming boot/update broadcast.
     *
     * Only reacts to the two expected actions; ignores everything else
     * as a safety measure.
     *
     * @param context Receiver context provided by the system.
     * @param intent  The broadcast intent.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val isBootOrUpdate = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBootOrUpdate) return

        // Delegate to KeepAliveService.start() which uses
        // ContextCompat.startForegroundService() internally
        KeepAliveService.start(context)
    }
}
