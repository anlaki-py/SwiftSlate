package com.musheer360.swiftslate.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi

/**
 * Provides cross-version haptic feedback using the system vibrator.
 * Abstracts away API-level branching so callers can simply specify
 * a feedback type without caring about the OS version.
 */
object HapticHelper {

    /**
     * Triggers a haptic vibration matching the given feedback type.
     * Posts the vibration onto the main thread via [handler] to ensure
     * it executes regardless of the caller's current dispatcher.
     *
     * @param context Application context used to obtain the vibrator service.
     * @param handler Main-thread handler to post the vibration call on.
     * @param feedbackType One of [HapticFeedbackConstants.CONFIRM] or
     *                     [HapticFeedbackConstants.REJECT].
     */
    @Suppress("DEPRECATION")
    fun performHapticFeedback(context: Context, handler: Handler, feedbackType: Int) {
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val vibrator = vibratorManager.defaultVibrator
                    vibrateByType(vibrator, feedbackType)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrateByType(vibrator, feedbackType)
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(50)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Maps a [HapticFeedbackConstants] type to a predefined vibration effect.
     *
     * @param vibrator The vibrator instance to trigger.
     * @param feedbackType The feedback constant to map.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibrateByType(vibrator: Vibrator, feedbackType: Int) {
        when (feedbackType) {
            HapticFeedbackConstants.CONFIRM ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            HapticFeedbackConstants.REJECT ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        }
    }
}
