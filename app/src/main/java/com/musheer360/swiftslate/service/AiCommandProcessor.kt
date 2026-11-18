package com.musheer360.swiftslate.service

import com.musheer360.swiftslate.SwiftSlateApp

import android.content.Context
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityNodeInfo
import com.musheer360.swiftslate.data.ProviderManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Handles AI-powered text transformation commands. Manages provider resolution,
 * multi-key rotation with automatic fallback on rate-limit or invalid-key errors,
 * and an inline progress spinner.
 *
 * Requests use the selected provider protocol, shared cancellable networking, and credential rotation.
 *
 * @param context Application context for reading SharedPreferences.
 * @param providerManager Provides the active user-defined provider.
 * @param textReplacer Handles text replacement in accessibility nodes.
 * @param toastManager Displays overlay toast notifications.
 * @param serviceScope Coroutine scope tied to the service lifecycle.
 * @param handler Main-thread handler.
 */
class AiCommandProcessor(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val textReplacer: TextReplacer,
    private val toastManager: OverlayToastManager,
    private val serviceScope: CoroutineScope,
    private val handler: Handler
) {

    private val requests = (context.applicationContext as SwiftSlateApp).aiServices.requests

    private companion object {
        val SPINNER_FRAMES = arrayOf("◐", "◓", "◑", "◒")
    }

    /**
     * Generates a unique string identifier for an [AccessibilityNodeInfo].
     *
     * @param source The node to identify.
     * @return A string combining window ID and resource name or hash code.
     */
    private fun sourceId(source: AccessibilityNodeInfo): String =
        "${source.windowId}:${source.viewIdResourceName ?: source.hashCode()}"

    /**
     * Launches a coroutine to process an AI command: reads provider settings,
     * rotates through available API keys on retryable errors, and replaces the
     * source text with the AI-generated result.
     *
     * @param source The accessibility node containing the user's text.
     * @param text The cleaned user input (trigger stripped).
     * @param command The matched [Command] containing the AI prompt.
     * @param callbacks Notified on completion and original-text capture.
     * @param temperature The sampling temperature from settings.
     * @return The launched [Job]; caller should assign to `currentJob`.
     */
    fun processCommand(
        source: AccessibilityNodeInfo,
        text: String,
        command: Command,
        callbacks: ProcessingCallbacks,
        temperature: Float,
        previousJob: Job? = null
    ): Job {
        return serviceScope.launch {
            val thisJob = coroutineContext[Job]!!
            previousJob?.join()
            val provider = providerManager.getActiveProvider()

            // Early exit if no provider configured
            if (provider == null) {
                toastManager.showToast("No provider configured. Add one in Settings.")
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    try { source.recycle() } catch (_: Exception) {}
                }
                return@launch
            }

            // Early exit if no model selected
            if (provider.selectedModel.isBlank()) {
                toastManager.showToast("No model selected. Choose one in Settings.")
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    try { source.recycle() } catch (_: Exception) {}
                }
                return@launch
            }

            val timeoutSecs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getFloat("timeout", 10f)
            
            try {
                withTimeout((timeoutSecs * 1000).toLong()) {
                    executeTransformation(
                        source, text, command, provider,
                        temperature.toDouble(), callbacks, thisJob
                    )
                }
            } catch (_: TimeoutCancellationException) {
                try { textReplacer.replaceText(source, text) } catch (_: Exception) {}
                toastManager.showToast("Request timed out")
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.Main) {
                    try { textReplacer.replaceText(source, text) } catch (_: Exception) {}
                }
                throw e
            } catch (e: Exception) {
                try { textReplacer.replaceText(source, text) } catch (_: Exception) {
                    toastManager.showToast("Could not restore original text")
                }
                toastManager.showToast(ErrorMessageMapper.describe(context, e))
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    try { source.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    /** Applies one transformation; the request router owns credentials and protocol selection. */
    private suspend fun executeTransformation(
        source: AccessibilityNodeInfo,
        originalText: String,
        command: Command,
        provider: Provider,
        temperature: Double,
        callbacks: ProcessingCallbacks,
        parentJobToCancel: Job?
    ) {
        val spinnerJob = startInlineSpinner(source, originalText, parentJobToCancel)
        try {
            val generated = requests.generate(provider, command.prompt, originalText, temperature).getOrThrow()
            spinnerJob.cancelAndJoin()
            callbacks.onOriginalTextCaptured(originalText, sourceId(source))
            textReplacer.replaceText(source, generated.text)
            HapticHelper.performHapticFeedback(context, handler, HapticFeedbackConstants.CONFIRM)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            spinnerJob.cancelAndJoin()
            textReplacer.replaceText(source, originalText)
            toastManager.showToast(ErrorMessageMapper.describe(context, error))
        } finally {
            spinnerJob.cancel()
        }
    }

    /**
     * Animates an inline spinner in the text field during AI processing.
     *
     * @param source The accessibility node to animate.
     * @param baseText The user's original text shown before the spinner.
     * @return A [Job] that runs the animation loop.
     */
    private fun startInlineSpinner(source: AccessibilityNodeInfo, baseText: String, parentJobToCancel: Job?): Job {
        return serviceScope.launch(Dispatchers.Main) {
            var frameIndex = 0
            while (isActive) {
                if (!textReplacer.setFieldText(source, "$baseText ${SPINNER_FRAMES[frameIndex]}")) {
                    parentJobToCancel?.cancel()
                    break
                }
                frameIndex = (frameIndex + 1) % SPINNER_FRAMES.size
                delay(200)
            }
        }
    }
}
