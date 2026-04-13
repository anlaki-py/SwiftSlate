package com.musheer360.swiftslate.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Utility helpers for checking and requesting exemption from Android's
 * battery optimisation (Doze / App Standby).
 *
 * Disabling battery optimisation is the single strongest cross-version
 * tool (API 23–35) for keeping the app process alive. It:
 * - Grants partial Doze/Standby exemption (network + partial wake locks).
 * - Satisfies one of the key background-FGS-start exemptions on API 31+.
 */
object BatteryOptimizationHelper {

    /**
     * Checks whether the app is exempt from battery optimisation.
     *
     * On API < 23  (no Doze exists) this always returns `true`.
     *
     * @param context Any context.
     * @return `true` if the app is whitelisted / unrestricted.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches the system dialog asking the user to exempt this app from
     * battery optimisation.
     *
     * Uses [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] which
     * shows a one-tap confirmation dialog rather than navigating to the
     * full battery settings page.
     *
     * No-ops silently on API < 23 where Doze does not exist.
     *
     * @param context Activity or application context. If called from a
     *                non-Activity context the intent is launched with
     *                [Intent.FLAG_ACTIVITY_NEW_TASK].
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            // Needed when starting from a non-Activity context (e.g. BroadcastReceiver)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
