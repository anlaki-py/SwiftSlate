package com.musheer360.swiftslate.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Accessibility service that detects command triggers typed in any text field,
 * dispatches them to the appropriate handler (AI, text-replacer, or undo),
 * and manages the overall processing lifecycle.
 *
 * Delegates heavy work to [AiCommandProcessor], [TextReplacer],
 * [OverlayToastManager], and [HapticHelper].
 */
class AssistantService : AccessibilityService(), ProcessingCallbacks {

    private lateinit var keyManager: KeyManager
    private lateinit var commandManager: CommandManager
    private lateinit var providerManager: ProviderManager
    private lateinit var textReplacer: TextReplacer
    private lateinit var toastManager: OverlayToastManager
    private lateinit var aiCommandProcessor: AiCommandProcessor

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val isProcessing = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var processingStartedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var triggerLastChars = setOf<Char>()
    private var cachedPrefix = CommandManager.DEFAULT_PREFIX
    private var cachedTranslatePrefix = ""
    @Volatile private var currentJob: Job? = null
    private var processingResetRunnable: Runnable? = null
    // Intentionally single-level undo (toggle between current and previous text).
    @Volatile private var lastOriginalText: String? = null
    @Volatile private var lastUndoSourceId: String? = null
    private var lastTriggerRefresh = 0L
    private var watchdogRunnable: Runnable? = null

    private companion object {
        const val TRIGGER_REFRESH_INTERVAL_MS = 5_000L
        const val PROCESSING_WATCHDOG_MS = 120_000L
    }

    /** Unique identifier for a node: windowId + resource name (or hash). */
    private fun sourceId(source: AccessibilityNodeInfo): String =
        "${source.windowId}:${source.viewIdResourceName ?: source.hashCode()}"

    override fun onServiceConnected() {
        super.onServiceConnected()
        keyManager = KeyManager(applicationContext)
        commandManager = CommandManager(applicationContext)
        providerManager = ProviderManager(applicationContext)
        textReplacer = TextReplacer(applicationContext, handler)
        toastManager = OverlayToastManager(applicationContext, handler)
        aiCommandProcessor = AiCommandProcessor(
            applicationContext, providerManager, keyManager,
            OpenAICompatibleClient(), textReplacer, toastManager,
            serviceScope, handler
        )
        updateTriggers()

        // Ensure the foreground keep-alive is running so the process
        // has elevated priority even without the app being open
        KeepAliveService.start(applicationContext)
    }

    /**
     * Refreshes cached trigger data from [CommandManager].
     * Filters the translate entry to avoid false-positive matching on '<lang>'.
     */
    private fun updateTriggers() {
        cachedPrefix = commandManager.getTriggerPrefix()
        cachedTranslatePrefix = commandManager.getTranslatePrefix()
        triggerLastChars = commandManager.getCommands()
            .filter { it.builtInKey != "translate" }
            .mapNotNull { it.trigger.lastOrNull() }.toSet()
        lastTriggerRefresh = System.currentTimeMillis()
    }

    // -- Watchdog & processing-state helpers --

    /** Arms a watchdog that force-cancels after [PROCESSING_WATCHDOG_MS]. */
    private fun startWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (isProcessing.get()) {
                currentJob?.cancel(); isProcessing.set(false); processingStartedAt = 0L
            }
        }
        watchdogRunnable = r
        handler.postDelayed(r, PROCESSING_WATCHDOG_MS)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }; watchdogRunnable = null
    }

    private fun cancelPendingProcessingReset() {
        processingResetRunnable?.let { handler.removeCallbacks(it) }; processingResetRunnable = null
    }

    /** Delays clearing [isProcessing] to debounce IME re-commit events. */
    private fun scheduleProcessingReset() {
        cancelPendingProcessingReset()
        val r = Runnable { isProcessing.set(false) }
        processingResetRunnable = r
        if (!handler.postDelayed(r, 500)) isProcessing.set(false)
    }

    /** Common setup before any command handler: acquire lock, arm watchdog, cancel prior job. */
    private fun beginProcessing(): Boolean {
        if (!isProcessing.compareAndSet(false, true)) return false
        processingStartedAt = System.currentTimeMillis()
        startWatchdog(); cancelPendingProcessingReset(); currentJob?.cancel()
        return true
    }

    // -- Event handling --

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (event.packageName?.toString() == packageName || isProcessing.get()) return

        val source = event.source ?: return
        if (source.isPassword) { source.recycle(); return }
        val text = source.text?.toString() ?: run { source.recycle(); return }

        if (text.isEmpty()) {
            textReplacer.handleEmptyField(source); source.recycle(); return
        }
        // Debounce: skip events matching our most recent replacement
        val replaced = textReplacer.lastReplacedText
        if (replaced != null && text == replaced &&
            System.currentTimeMillis() - textReplacer.lastReplacedAt < 1000
        ) { source.recycle(); return }

        if (System.currentTimeMillis() - lastTriggerRefresh > TRIGGER_REFRESH_INTERVAL_MS) updateTriggers()

        val lastChar = text.last()
        if (!triggerLastChars.contains(lastChar)) {
            if (!lastChar.isLetterOrDigit() || !text.contains(cachedTranslatePrefix)) {
                source.recycle(); return
            }
        }

        val command = commandManager.findCommand(text) ?: run { source.recycle(); return }
        val precedingText = text.substring(0, text.length - command.trigger.length)
        val cleanText = precedingText.trim()

        when {
            command.builtInKey == "undo" -> {
                if (!beginProcessing()) { source.recycle(); return }
                handleUndo(source, cleanText)
            }
            command.type == CommandType.TEXT_REPLACER -> handleTextReplacer(source, precedingText, command)
            command.type == CommandType.AI -> handleAiCommand(source, cleanText, command)
        }
    }

    // -- Command handlers --

    /** Replaces trigger text with the command's literal prompt. */
    private fun handleTextReplacer(source: AccessibilityNodeInfo, precedingText: String, command: Command) {
        if (!beginProcessing()) { source.recycle(); return }
        currentJob = serviceScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                withContext(Dispatchers.Main) {
                    lastOriginalText = precedingText
                    lastUndoSourceId = sourceId(source)
                    textReplacer.replaceText(source, precedingText + command.prompt)
                    HapticHelper.performHapticFeedback(applicationContext, handler, HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { toastManager.showToast("Could not replace text")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (currentJob === thisJob) { cancelWatchdog(); processingStartedAt = 0L; scheduleProcessingReset() }
                    textReplacer.recycleIfUnowned(source)
                }
            }
        }
    }

    /** Validates preconditions then delegates to [AiCommandProcessor]. */
    private fun handleAiCommand(source: AccessibilityNodeInfo, cleanText: String, command: Command) {
        if (cleanText.isEmpty()) { source.recycle(); return }
        if (!beginProcessing()) { source.recycle(); return }

        if (!keyManager.keystoreAvailable) {
            handler.post {
                Toast.makeText(applicationContext, "Secure key storage unavailable. Please reinstall the app.", Toast.LENGTH_LONG).show()
            }
            cancelWatchdog(); processingStartedAt = 0L; isProcessing.set(false)
            textReplacer.recycleIfUnowned(source); return
        }

        val temperature = applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getFloat("temperature", 0.7f)

        currentJob = aiCommandProcessor.processCommand(
            source, cleanText, command, this, temperature
        )
    }

    /** Swaps current text with the previously captured original (single-level toggle). */
    private fun handleUndo(source: AccessibilityNodeInfo, currentText: String) {
        currentJob = serviceScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                val prev = lastOriginalText; val undoId = lastUndoSourceId
                if (prev == null || undoId != sourceId(source)) {
                    HapticHelper.performHapticFeedback(applicationContext, handler, HapticFeedbackConstants.REJECT)
                    toastManager.showToast("Nothing to undo")
                } else {
                    lastOriginalText = currentText
                    textReplacer.replaceText(source, prev)
                    HapticHelper.performHapticFeedback(applicationContext, handler, HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { toastManager.showToast("Could not undo")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (currentJob === thisJob) { cancelWatchdog(); processingStartedAt = 0L; scheduleProcessingReset() }
                    textReplacer.recycleIfUnowned(source)
                }
            }
        }
    }

    // -- ProcessingCallbacks --

    override fun onProcessingComplete(job: Job) {
        if (currentJob === job) { cancelWatchdog(); processingStartedAt = 0L; scheduleProcessingReset() }
    }

    override fun onOriginalTextCaptured(text: String, sourceId: String) {
        lastOriginalText = text; lastUndoSourceId = sourceId
    }

    // -- Lifecycle --

    override fun onInterrupt() {
        isProcessing.set(false); processingStartedAt = 0L
        currentJob?.cancel(); serviceJob.cancelChildren()
        handler.removeCallbacksAndMessages(null)
        textReplacer.clearState(); toastManager.dismissOverlayToast()
    }

    override fun onDestroy() {
        super.onDestroy(); isProcessing.set(false)
        handler.removeCallbacksAndMessages(null)
        textReplacer.clearState(); toastManager.dismissOverlayToast()
        serviceScope.cancel()
    }
}
