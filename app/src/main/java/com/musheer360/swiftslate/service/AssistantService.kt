package com.musheer360.swiftslate.service

import android.accessibilityservice.AccessibilityService
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import com.musheer360.swiftslate.SwiftSlateApp
import com.musheer360.swiftslate.api.GeminiClient
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * AssistantService - Core accessibility service that monitors text input
 * and processes commands triggered by special prefixes (e.g., ?fix, ?improve).
 *
 * This service runs with privileged accessibility permissions and is designed
 * to survive OS kills through multiple defense layers:
 * - AccessibilityService gets auto-restarted by Android on crash
 * - Companion KeepAliveService holds foreground slot for process priority
 * - CoroutineExceptionHandler provides visibility into unhandled failures
 * - Proper onDestroy cleanup prevents memory leaks
 */
class AssistantService : AccessibilityService() {

    // Access singleton managers from Application - ensures consistent state across service restarts
    private val app: SwiftSlateApp get() = application as SwiftSlateApp
    private val keyManager get() = app.keyManager
    private val commandManager get() = app.commandManager

    // API clients for different providers
    private val client = GeminiClient()
    private val openAIClient = OpenAICompatibleClient()

    // Coroutine exception handler - provides visibility into unhandled coroutine failures
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AssistantService", "Unhandled coroutine exception", throwable)
        // Show overlay toast for unexpected errors
        handler.post {
            showOverlayToastDirect("Unexpected error: ${throwable.localizedMessage}")
        }
    }

    // SupervisorJob allows child coroutines to fail independently
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO + exceptionHandler)

    // Processing state - volatile for thread visibility
    @Volatile
    private var isProcessing = false

    private val handler = Handler(Looper.getMainLooper())

    // Trigger detection state
    private var triggerLastChars = setOf<Char>()
    private var cachedPrefix = CommandManager.DEFAULT_PREFIX
    private var lastTriggerRefresh = 0L

    // Current API request job - can be cancelled
    private var currentJob: Job? = null

    // Undo state - stores original text before last replacement
    @Volatile
    private var lastOriginalText: String? = null

    // Overlay toast state
    private var currentOverlayToast: View? = null
    private var dismissRunnable: Runnable? = null
    private var dismissAnimator: AnimatorSet? = null
    private var enterAnimator: AnimatorSet? = null

    /**
     * Converts dp to pixels based on screen density.
     */
    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    private companion object {
        const val TRIGGER_REFRESH_INTERVAL_MS = 5_000L
        const val DEFAULT_TEMPERATURE = 0.5
        val SPINNER_FRAMES = arrayOf("◐", "◓", "◑", "◒")
        const val TOAST_BACKGROUND_COLOR = 0xE6323232.toInt()
        const val TOAST_DURATION_MS = 3500L
        const val TOAST_BOTTOM_MARGIN_DP = 64
        const val TOAST_ANIM_DURATION_MS = 300L
        const val TOAST_SLIDE_DISTANCE_DP = 40
    }

    /**
     * Called when accessibility service is fully connected and ready.
     * Starts the KeepAliveService companion and initializes triggers.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AssistantService", "Service connected")

        // Start the foreground keep-alive companion service
        val keepAliveIntent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(keepAliveIntent)
        } else {
            startService(keepAliveIntent)
        }

        // Initialize triggers from singleton manager
        updateTriggers()
    }

    /**
     * Refreshes cached trigger prefix and last characters from CommandManager.
     * Called periodically to pick up setting changes.
     */
    private fun updateTriggers() {
        cachedPrefix = commandManager.getTriggerPrefix()
        val cmds = commandManager.getCommands()
        triggerLastChars = cmds.mapNotNull { it.trigger.lastOrNull() }.toSet()
        lastTriggerRefresh = System.currentTimeMillis()
    }

    /**
     * Main entry point for accessibility events.
     * Monitors text changes and triggers command processing.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || isProcessing) return
        val source = event.source ?: return
        val text = source.text?.toString() ?: return
        if (text.isEmpty()) return

        // Periodically refresh triggers to pick up settings changes
        if (System.currentTimeMillis() - lastTriggerRefresh > TRIGGER_REFRESH_INTERVAL_MS) {
            updateTriggers()
        }

        // Quick filter: check if last character could be a trigger
        val lastChar = text[text.length - 1]
        if (!triggerLastChars.contains(lastChar)) {
            // Special case: translate command uses language code suffix
            if (!lastChar.isLetterOrDigit() || !text.contains("${cachedPrefix}tr:")) {
                return
            }
        }

        // Find matching command
        val command = commandManager.findCommand(text) ?: return

        // Extract text before trigger
        val cleanText = text.substring(0, text.length - command.trigger.length).trim()

        // Handle undo command specially
        if (command.trigger.endsWith("undo") && command.isBuiltIn) {
            if (source.isPassword) { return }
            isProcessing = true
            currentJob?.cancel()
            handleUndo(source, cleanText)
            return
        }

        // Skip empty text or password fields
        if (cleanText.isEmpty() || source.isPassword) { return }

        isProcessing = true
        currentJob?.cancel()
        processCommand(source, cleanText, command)
    }

    /**
     * Processes a command by calling the appropriate API and replacing text.
     * Handles key rotation, rate limits, and error recovery.
     */
    private fun processCommand(source: AccessibilityNodeInfo, text: String, command: Command) {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val providerType = prefs.getString("provider_type", "gemini") ?: "gemini"
        val model: String
        val endpoint: String

        if (providerType == "custom") {
            model = prefs.getString("custom_model", "") ?: ""
            endpoint = prefs.getString("custom_endpoint", "") ?: ""
            if (model.isBlank() || endpoint.isBlank()) {
                serviceScope.launch {
                    showToast("Custom provider not configured. Set endpoint and model in Settings.")
                }
                isProcessing = false
                return
            }
        } else {
            model = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
            endpoint = ""
        }
        val temperature = DEFAULT_TEMPERATURE

        currentJob = serviceScope.launch {
            val originalText = text
            var spinnerJob: Job? = null
            try {
                withTimeout(90_000) {
                    val maxAttempts = keyManager.getKeys().size.coerceAtLeast(1)
                    var lastErrorMsg: String? = null
                    var succeeded = false

                    // Try each available key until one succeeds
                    for (attempt in 0 until maxAttempts) {
                        val key = keyManager.getNextKey()
                        if (key == null) break

                        // Start spinner animation on first attempt
                        if (spinnerJob == null) {
                            spinnerJob = startInlineSpinner(source, originalText)
                        }

                        // Call appropriate API based on provider type
                        val result = if (providerType == "custom") {
                            openAIClient.generate(command.prompt, text, key, model, temperature, endpoint)
                        } else {
                            client.generate(command.prompt, text, key, model, temperature)
                        }

                        if (result.isSuccess) {
                            spinnerJob?.cancel()
                            spinnerJob = null
                            lastOriginalText = originalText
                            replaceText(source, result.getOrThrow())
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            succeeded = true
                            break
                        }

                        // Handle failure - check if retryable
                        val msg = result.exceptionOrNull()?.message ?: ""
                        lastErrorMsg = msg
                        val isRateLimit = msg.contains("Rate limit") || msg.contains("rate limit")
                        val isInvalidKey = msg.contains("Invalid API key", ignoreCase = true) ||
                                           msg.contains("API key not valid", ignoreCase = true)

                        if (isRateLimit) {
                            val seconds = Regex("retry after (\\d+)s")
                                .find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 60
                            keyManager.reportRateLimit(key, seconds)
                        } else if (isInvalidKey) {
                            keyManager.markInvalid(key)
                        } else {
                            break // Non-retryable error, stop trying other keys
                        }
                    }

                    // Handle failure case
                    if (!succeeded) {
                        spinnerJob?.cancel()
                        spinnerJob = null
                        replaceText(source, originalText)
                        performHapticFeedback(HapticFeedbackConstants.REJECT)
                        if (lastErrorMsg != null) {
                            showToast("SwiftSlate Error: $lastErrorMsg")
                        } else {
                            val waitMs = keyManager.getShortestWaitTimeMs()
                            if (waitMs != null) {
                                val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                                showToast("API key rate limited. Try again in ${waitSec}s")
                            } else if (keyManager.getKeys().isEmpty()) {
                                showToast("No API keys configured")
                            } else {
                                showToast("All API keys are invalid. Please check your keys")
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                spinnerJob?.cancel()
                try { replaceText(source, originalText) } catch (_: Exception) {}
                showToast("Request timed out")
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation - let it propagate
            } catch (e: Exception) {
                spinnerJob?.cancel()
                try { replaceText(source, originalText) } catch (_: Exception) {
                    showToast("Could not restore original text")
                }
                showToast("SwiftSlate Error: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    spinnerJob?.cancel()
                    if (!handler.postDelayed({ isProcessing = false }, 500)) {
                        isProcessing = false
                    }
                }
            }
        }
    }

    /**
     * Handles the undo command to restore previous text.
     */
    private fun handleUndo(source: AccessibilityNodeInfo, currentText: String) {
        currentJob = serviceScope.launch {
            try {
                val previousText = lastOriginalText
                if (previousText == null) {
                    replaceText(source, currentText)
                    performHapticFeedback(HapticFeedbackConstants.REJECT)
                    showToast("Nothing to undo")
                } else {
                    lastOriginalText = currentText
                    replaceText(source, previousText)
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Could not undo")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (!handler.postDelayed({ isProcessing = false }, 500)) {
                        isProcessing = false
                    }
                }
            }
        }
    }

    /**
     * Replaces text in the source node using accessibility actions.
     * Falls back to clipboard paste if direct set text fails.
     */
    private suspend fun replaceText(source: AccessibilityNodeInfo, newText: String) =
        withContext(Dispatchers.Main) {
        source.refresh()
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)

        val success = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        // Fallback: use clipboard paste if direct set fails
        if (!success) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val oldClip = clipboard.primaryClip
            val newClip = ClipData.newPlainText("SwiftSlate Result", newText)
            clipboard.setPrimaryClip(newClip)

            val selectAllArgs = Bundle()
            selectAllArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            selectAllArgs.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                source.text?.length ?: 0
            )
            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllArgs)
            source.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            // Restore original clipboard after delay
            handler.postDelayed({
                if (oldClip != null) {
                    clipboard.setPrimaryClip(oldClip)
                }
            }, 500)
        }
    }

    /**
     * Sets text directly in a node using accessibility action.
     */
    private fun setFieldText(source: AccessibilityNodeInfo, text: String) {
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    /**
     * Starts an animated spinner in the text field to indicate processing.
     */
    private fun startInlineSpinner(source: AccessibilityNodeInfo, baseText: String): Job {
        return serviceScope.launch(Dispatchers.Main) {
            var frameIndex = 0
            while (isActive) {
                setFieldText(source, "$baseText ${SPINNER_FRAMES[frameIndex]}")
                frameIndex = (frameIndex + 1) % SPINNER_FRAMES.size
                delay(200)
            }
        }
    }

    /**
     * Shows an overlay toast message with animation.
     */
    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        dismissOverlayToast()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val textView = TextView(applicationContext).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(24), dp(12), dp(24), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            background = GradientDrawable().apply {
                setColor(TOAST_BACKGROUND_COLOR)
                cornerRadius = dp(24).toFloat()
            }
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(TOAST_SLIDE_DISTANCE_DP).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(TOAST_BOTTOM_MARGIN_DP)
            windowAnimations = 0
        }

        try {
            wm.addView(textView, params)
            currentOverlayToast = textView

            // Animate entry
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(textView, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(
                        textView, View.TRANSLATION_Y,
                        dp(TOAST_SLIDE_DISTANCE_DP).toFloat(), 0f
                    )
                )
                duration = TOAST_ANIM_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
                enterAnimator = this
            }

            // Schedule dismissal
            val runnable = Runnable { dismissOverlayToastAnimated() }
            dismissRunnable = runnable
            handler.postDelayed(runnable, TOAST_DURATION_MS)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows an overlay toast directly (for exception handler).
     * Must be called from main thread.
     */
    private fun showOverlayToastDirect(msg: String) {
        dismissOverlayToast()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val textView = TextView(applicationContext).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(24), dp(12), dp(24), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            background = GradientDrawable().apply {
                setColor(TOAST_BACKGROUND_COLOR)
                cornerRadius = dp(24).toFloat()
            }
            gravity = Gravity.CENTER
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(TOAST_BOTTOM_MARGIN_DP)
        }

        try {
            wm.addView(textView, params)
            currentOverlayToast = textView
            handler.postDelayed({ dismissOverlayToast() }, TOAST_DURATION_MS)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Immediately dismisses the overlay toast without animation.
     */
    private fun dismissOverlayToast() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        enterAnimator?.cancel()
        enterAnimator = null
        dismissAnimator?.cancel()
        dismissAnimator = null
        currentOverlayToast?.let { view ->
            try {
                view.visibility = View.GONE
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (_: Exception) {}
            currentOverlayToast = null
        }
    }

    /**
     * Dismisses the overlay toast with fade-out animation.
     */
    private fun dismissOverlayToastAnimated() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        enterAnimator?.cancel()
        enterAnimator = null
        dismissAnimator?.cancel()
        currentOverlayToast?.let { view ->
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                dismissAnimator = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
                        ObjectAnimator.ofFloat(
                            view, View.TRANSLATION_Y,
                            view.translationY, dp(TOAST_SLIDE_DISTANCE_DP).toFloat()
                        )
                    )
                    duration = TOAST_ANIM_DURATION_MS
                    interpolator = DecelerateInterpolator()
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            view.visibility = View.GONE
                            try { wm.removeView(view) } catch (_: Exception) {}
                            dismissAnimator = null
                        }
                    })
                    start()
                }
            } catch (_: Exception) {}
            currentOverlayToast = null
        }
    }

    /**
     * Performs haptic feedback for confirmation or rejection.
     */
    @Suppress("DEPRECATION")
    private fun performHapticFeedback(feedbackType: Int) {
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val vibrator = vibratorManager.defaultVibrator
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM ->
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        HapticFeedbackConstants.REJECT ->
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM ->
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        HapticFeedbackConstants.REJECT ->
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(50)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Called when the accessibility service is interrupted by the system.
     */
    override fun onInterrupt() {
        Log.w("AssistantService", "Service interrupted")
        isProcessing = false
        currentJob?.cancel()
    }

    /**
     * Cleans up all resources when service is destroyed.
     * This prevents memory leaks that could cause crashes.
     */
    override fun onDestroy() {
        Log.d("AssistantService", "Service destroying - cleaning up resources")
        super.onDestroy()

        // Cancel all coroutines
        serviceScope.cancel()

        // Cancel current API job
        currentJob?.cancel()
        currentJob = null

        // Remove all pending handler callbacks
        handler.removeCallbacksAndMessages(null)

        // Cancel and clean up animators
        dismissAnimator?.cancel()
        enterAnimator?.cancel()
        dismissAnimator = null
        enterAnimator = null

        // Remove overlay view from window if still attached
        try {
            currentOverlayToast?.let { view ->
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            Log.w("AssistantService", "Error removing overlay on destroy", e)
        }
        currentOverlayToast = null
    }
}
