package com.musheer360.swiftslate.service

import android.content.Context
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityNodeInfo
import com.musheer360.swiftslate.api.ApiError
import com.musheer360.swiftslate.api.ApiException
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
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
 * All providers are user-defined and routed through [OpenAICompatibleClient]
 * using the standardised `/chat/completions` endpoint with strict structured output.
 *
 * @param context Application context for reading SharedPreferences.
 * @param providerManager Provides the active user-defined provider.
 * @param keyManager Provides API keys and tracks rate-limit/invalid state.
 * @param openAIClient Unified client for all OpenAI-compatible APIs.
 * @param textReplacer Handles text replacement in accessibility nodes.
 * @param toastManager Displays overlay toast notifications.
 * @param serviceScope Coroutine scope tied to the service lifecycle.
 * @param handler Main-thread handler.
 */
class AiCommandProcessor(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val keyManager: KeyManager,
    private val openAIClient: OpenAICompatibleClient,
    private val textReplacer: TextReplacer,
    private val toastManager: OverlayToastManager,
    private val serviceScope: CoroutineScope,
    private val handler: Handler
) {

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.7
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
        temperature: Float
    ): Job {
        return serviceScope.launch {
            val thisJob = coroutineContext[Job]!!
            val provider = providerManager.getActiveProvider()

            // Early exit if no provider configured
            if (provider == null) {
                toastManager.showToast("No provider configured. Add one in Settings.")
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    textReplacer.recycleIfUnowned(source)
                }
                return@launch
            }

            // Early exit if no model selected
            if (provider.selectedModel.isBlank()) {
                toastManager.showToast("No model selected. Choose one in Settings.")
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    textReplacer.recycleIfUnowned(source)
                }
                return@launch
            }

            var spinnerJob: Job? = null
            try {
                withTimeout(90_000) {
                    spinnerJob = executeWithKeyRotation(
                        source, text, command, provider,
                        temperature.toDouble(), callbacks, spinnerJob
                    )
                }
            } catch (_: TimeoutCancellationException) {
                spinnerJob?.cancelAndJoin()
                try { textReplacer.replaceText(source, text) } catch (_: Exception) {}
                toastManager.showToast("Request timed out")
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.Main) {
                    spinnerJob?.cancel()
                    try { textReplacer.replaceText(source, text) } catch (_: Exception) {}
                }
                throw e
            } catch (e: Exception) {
                spinnerJob?.cancelAndJoin()
                try { textReplacer.replaceText(source, text) } catch (_: Exception) {
                    toastManager.showToast("Could not restore original text")
                }
                toastManager.showToast(ErrorMessageMapper.map(e.message ?: "Unknown error"))
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    spinnerJob?.cancel()
                    textReplacer.recycleIfUnowned(source)
                }
            }
        }
    }

    /**
     * Tries each available API key in turn, stopping on success or
     * a non-retryable error.
     */
    private suspend fun executeWithKeyRotation(
        source: AccessibilityNodeInfo,
        originalText: String,
        command: Command,
        provider: Provider,
        temperature: Double,
        callbacks: ProcessingCallbacks,
        initialSpinnerJob: Job?
    ): Job? {
        var spinnerJob = initialSpinnerJob
        var lastErrorMsg: String? = null
        var succeeded = false

        val keyCount = keyManager.getKeys(provider.id).size.coerceAtLeast(1)
        for (attempt in 0 until keyCount) {
            val key = keyManager.getNextKey(provider.id) ?: break
            if (spinnerJob == null) spinnerJob = startInlineSpinner(source, originalText)

            val result = openAIClient.generate(
                prompt = command.prompt,
                text = originalText,
                apiKey = key,
                model = provider.selectedModel,
                temperature = temperature,
                endpoint = provider.endpoint
            )

            if (result.isSuccess) {
                spinnerJob?.cancelAndJoin(); spinnerJob = null
                callbacks.onOriginalTextCaptured(originalText, sourceId(source))
                val gen = result.getOrThrow()
                textReplacer.replaceText(source, gen.text)
                HapticHelper.performHapticFeedback(
                    context, handler, HapticFeedbackConstants.CONFIRM
                )
                succeeded = true; break
            }

            lastErrorMsg = result.exceptionOrNull()?.message ?: ""
            when ((result.exceptionOrNull() as? ApiException)?.apiError) {
                is ApiError.RateLimit -> {
                    val secs = (result.exceptionOrNull() as ApiException).apiError
                        .let { (it as ApiError.RateLimit).retryAfterSeconds?.toLong() ?: 60 }
                    keyManager.reportRateLimit(key, secs)
                }
                is ApiError.InvalidKey -> keyManager.markInvalid(key)
                else -> break
            }
        }

        if (!succeeded) {
            spinnerJob?.cancelAndJoin(); spinnerJob = null
            textReplacer.replaceText(source, originalText)
            showFailureToast(provider.id, lastErrorMsg)
        }
        return spinnerJob
    }

    /**
     * Shows an error toast using key-state context when no explicit
     * error message exists.
     *
     * @param providerId The active provider's ID for key-state lookups.
     * @param lastErrorMsg The last error message, or null.
     */
    private suspend fun showFailureToast(providerId: String, lastErrorMsg: String?) {
        if (lastErrorMsg != null) {
            toastManager.showToast(ErrorMessageMapper.map(lastErrorMsg))
        } else {
            val waitMs = keyManager.getShortestWaitTimeMs(providerId)
            when {
                waitMs != null -> {
                    val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                    toastManager.showToast("API key rate limited. Try again in ${waitSec}s")
                }
                keyManager.getKeys(providerId).isEmpty() ->
                    toastManager.showToast("No API keys configured for this provider")
                else ->
                    toastManager.showToast("All API keys are invalid. Please check your keys")
            }
        }
    }

    /**
     * Animates an inline spinner in the text field during AI processing.
     *
     * @param source The accessibility node to animate.
     * @param baseText The user's original text shown before the spinner.
     * @return A [Job] that runs the animation loop.
     */
    private fun startInlineSpinner(source: AccessibilityNodeInfo, baseText: String): Job {
        return serviceScope.launch(Dispatchers.Main) {
            var frameIndex = 0
            while (isActive) {
                if (!textReplacer.setFieldText(source, "$baseText ${SPINNER_FRAMES[frameIndex]}")) break
                frameIndex = (frameIndex + 1) % SPINNER_FRAMES.size
                delay(200)
            }
        }
    }
}
