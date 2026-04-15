package com.musheer360.swiftslate.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Utility for checking the accessibility service state.
 * Follows the same `object`-helper pattern as [BatteryOptimizationHelper].
 */
object AccessibilityHelper {

    /**
     * Checks whether this app's accessibility service is currently enabled.
     *
     * @param context Any Android context (used to access [AccessibilityManager]).
     * @return True if the service is listed among enabled accessibility services.
     */
    fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }
}
