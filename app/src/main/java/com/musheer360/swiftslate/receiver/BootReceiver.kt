package com.musheer360.swiftslate.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.musheer360.swiftslate.service.KeepAliveService

/**
 * BootReceiver - Handles BOOT_COMPLETED to start KeepAliveService on device boot.
 *
 * Accessibility services survive reboots if enabled, but the app process doesn't
 * start until triggered. This receiver primes the process with a foreground slot
 * before AssistantService resumes, preventing cold-start gaps on aggressive OEMs.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Handle standard boot and quick boot (Huawei devices)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("SwiftSlate", "Boot completed — starting KeepAliveService to prime process")

            // Start foreground service immediately on boot
            val keepAliveIntent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(keepAliveIntent)
            } else {
                context.startService(keepAliveIntent)
            }
        }
    }
}
