package com.musheer360.swiftslate.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.musheer360.swiftslate.data.remote.OpenAiClient
import com.musheer360.swiftslate.data.repository.CommandRepository
import com.musheer360.swiftslate.data.repository.KeyRepository
import com.musheer360.swiftslate.data.repository.ProviderRepository
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AssistantService : AccessibilityService(), ProcessingCallbacks {

    @Inject lateinit var commandRepository: CommandRepository
    @Inject lateinit var keyRepository: KeyRepository
    @Inject lateinit var providerRepository: ProviderRepository
    @Inject lateinit var openAiClient: OpenAiClient

    private lateinit var textReplacer: TextReplacer
    private lateinit var toastManager: OverlayToastManager
    private lateinit var aiCommandProcessor: AiCommandProcessor

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val isProcessing = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var processingStartedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var triggerLastChars = setOf<Char>()
    private var cachedPrefix = CommandConstants.DEFAULT_PREFIX
    private var cachedTranslatePrefix = ""
    @Volatile private var currentJob: Job? = null
    private var processingResetRunnable: Runnable? = null
    @Volatile private var lastOriginalText: String? = null
    @Volatile private var lastUndoSourceId: String? = null
    private var lastTriggerRefresh = 0L
    private var watchdogRunnable: Runnable? = null

    private companion object {
        const val TRIGGER_REFRESH_INTERVAL_MS = 5_000L
        const val PROCESSING_WATCHDOG_MS = 120_000L
    }

    private fun sourceId(source: AccessibilityNodeInfo): String =
        "${source.windowId}:${source.viewIdResourceName ?: source.hashCode()}"

    override fun onServiceConnected() {
        super.onServiceConnected()
        textReplacer = TextReplacer(applicationContext, handler)
        toastManager = OverlayToastManager(applicationContext, handler)
        aiCommandProcessor = AiCommandProcessor(
            applicationContext, providerRepository, keyRepository,
            openAiClient, textReplacer, toastManager,
            serviceScope, handler
        )
        updateTriggers()
        KeepAliveService.start(applicationContext)
        Timber.i("AssistantService connected")
    }

    private fun updateTriggers() {
        cachedPrefix = commandRepository.getTriggerPrefix()
        cachedTranslatePrefix = commandRepository.getTranslatePrefix()
        triggerLastChars = kotlinx.coroutines.runBlocking {
            commandRepository.getCommands()
        }.filter { it.builtInKey != "translate" }
            .mapNotNull { it.trigger.lastOrNull() }.toSet()
        lastTriggerRefresh = System.currentTimeMillis()
    }

    private fun startWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (isProcessing.get()) {
                currentJob?.cancel()
                isProcessing.set(false)
                processingStartedAt = 0L
                Timber.w("Watchdog triggered — processing cancelled")
            }
        }
        watchdogRunnable = r
        handler.postDelayed(r, PROCESSING_WATCHDOG_MS)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    private fun cancelPendingProcessingReset() {
        processingResetRunnable?.let { handler.removeCallbacks(it) }
        processingResetRunnable = null
    }

    private fun scheduleProcessingReset() {
        cancelPendingProcessingReset()
        val r = Runnable { isProcessing.set(false) }
        processingResetRunnable = r
        if (!handler.postDelayed(r, 500)) isProcessing.set(false)
    }

    private fun beginProcessing(): Job? {
        if (!isProcessing.compareAndSet(false, true)) return null
        processingStartedAt = System.currentTimeMillis()
        startWatchdog()
        cancelPendingProcessingReset()
        val oldJob = currentJob
        oldJob?.cancel()
        return oldJob ?: Job().apply { complete() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (event.packageName?.toString() == packageName || isProcessing.get()) return

        val source = event.source ?: return
        if (source.isPassword) { source.recycle(); return }
        val text = source.text?.toString() ?: run { source.recycle(); return }

        if (text.isEmpty()) {
            textReplacer.handleEmptyField(source)
            source.recycle()
            return
        }

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

        val command = kotlinx.coroutines.runBlocking {
            commandRepository.findCommand(text)
        } ?: run { source.recycle(); return }

        val precedingText = text.substring(0, text.length - command.trigger.length)
        val cleanText = precedingText.trim()

        val oldJob = beginProcessing() ?: run { try { source.recycle() } catch (_: Exception) {}; return }

        when {
            command.builtInKey == "undo" -> handleUndo(source, cleanText, oldJob)
            command.type == CommandType.TEXT_REPLACER -> handleTextReplacer(source, precedingText, command, oldJob)
            command.type == CommandType.AI -> handleAiCommand(source, cleanText, command, oldJob)
        }
    }

    private fun handleTextReplacer(source: AccessibilityNodeInfo, precedingText: String, command: Command, oldJob: Job) {
        currentJob = serviceScope.launch {
            oldJob.join()
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
                    if (currentJob === coroutineContext[Job]) { cancelWatchdog(); processingStartedAt = 0L; scheduleProcessingReset() }
                    try { source.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun handleAiCommand(source: AccessibilityNodeInfo, cleanText: String, command: Command, oldJob: Job) {
        if (!keyRepository.keystoreAvailable) {
            handler.post {
                android.widget.Toast.makeText(
                    applicationContext,
                    getString(com.musheer360.swiftslate.R.string.keys_keystore_error),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            cancelWatchdog()
            processingStartedAt = 0L
            isProcessing.set(false)
            try { source.recycle() } catch (_: Exception) {}
            return
        }

        val temperature = applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getFloat("temperature", 0.7f)

        currentJob = aiCommandProcessor.processCommand(
            source, cleanText, command, this, temperature, oldJob
        )
    }

    private fun handleUndo(source: AccessibilityNodeInfo, currentText: String, oldJob: Job) {
        currentJob = serviceScope.launch {
            oldJob.join()
            try {
                val prev = lastOriginalText
                val undoId = lastUndoSourceId
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
                    if (currentJob === coroutineContext[Job]) { cancelWatchdog(); processingStartedAt = 0L; scheduleProcessingReset() }
                    try { source.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onProcessingComplete(job: Job) {
        if (currentJob === job) { cancelWatchdog(); processingStartedAt = 0L; scheduleProcessingReset() }
    }

    override fun onOriginalTextCaptured(text: String, sourceId: String) {
        lastOriginalText = text
        lastUndoSourceId = sourceId
    }

    override fun onInterrupt() {
        isProcessing.set(false)
        processingStartedAt = 0L
        currentJob?.cancel()
        serviceJob.cancelChildren()
        handler.removeCallbacksAndMessages(null)
        textReplacer.clearState()
        toastManager.dismissOverlayToast()
        Timber.i("AssistantService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isProcessing.set(false)
        handler.removeCallbacksAndMessages(null)
        textReplacer.clearState()
        toastManager.dismissOverlayToast()
        serviceScope.cancel()
        Timber.i("AssistantService destroyed")
    }
}
