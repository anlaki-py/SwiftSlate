package com.musheer360.swiftslate.service

import android.content.Context
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityNodeInfo
import com.musheer360.swiftslate.api.ApiError
import com.musheer360.swiftslate.api.ApiException
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Handles AI-powered text transformation commands. Manages provider selection,
 * multi-key rotation with automatic fallback on rate-limit or invalid-key errors,
 * and an inline progress spinner.
 *
 * All providers (Gemini, Groq, custom) are routed through [OpenAICompatibleClient]
 * using the standardised `/chat/completions` endpoint with strict structured output.
 *
 * @param context Application context for reading SharedPreferences.
 * @param keyManager Provides API keys and tracks rate-limit/invalid state.
 * @param openAIClient Unified client for all OpenAI-compatible APIs.
 * @param textReplacer Handles text replacement in accessibility nodes.
 * @param toastManager Displays overlay toast notifications.
 * @param serviceScope Coroutine scope tied to the service lifecycle.
 * @param handler Main-thread handler.
 */
class AiCommandProcessor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val openAIClient: OpenAICompatibleClient,
    private val textReplacer: TextReplacer,
    private val toastManager: OverlayToastManager,
    private val serviceScope: CoroutineScope,
    private val handler: Handler
) {

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.5

        /** Gemini's official OpenAI-compatible base URL. */
        const val GEMINI_OPENAI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/openai"

        /** Groq's OpenAI-compatible base URL. */
        const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1"

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
     * @return The launched [Job]; caller should assign to `currentJob`.
     */
    fun processCommand(
        source: AccessibilityNodeInfo,
        text: String,
        command: Command,
        callbacks: ProcessingCallbacks
    ): Job {
        return serviceScope.launch {
            val thisJob = coroutineContext[Job]!!
            val providerConfig = resolveProvider()

            // Early exit if custom provider isn't configured
            if (providerConfig == null) {
                toastManager.showToast(
                    "Custom provider not configured. Set endpoint and model in Settings."
                )
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
                        source, text, command, providerConfig,
                        callbacks, spinnerJob
                    )
                }
            } catch (e: TimeoutCancellationException) {
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
     * Reads SharedPreferences to determine the active provider, model, and endpoint.
     *
     * @return A [ProviderConfig] with resolved settings, or null if the custom
     *         provider is selected but not configured.
     */
    private fun resolveProvider(): ProviderConfig? {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val type = prefs.getString("provider_type", ProviderType.GEMINI)
            ?: ProviderType.GEMINI

        return when (type) {
            ProviderType.GEMINI -> ProviderConfig(
                type = ProviderType.GEMINI,
                model = prefs.getString("model", "gemini-2.5-flash-lite")
                    ?: "gemini-2.5-flash-lite",
                endpoint = GEMINI_OPENAI_ENDPOINT
            )
            ProviderType.GROQ -> ProviderConfig(
                type = ProviderType.GROQ,
                model = prefs.getString("groq_model", "llama-3.3-70b-versatile")
                    ?: "llama-3.3-70b-versatile",
                endpoint = GROQ_ENDPOINT
            )
            ProviderType.CUSTOM -> {
                val model = prefs.getString("custom_model", "") ?: ""
                val endpoint = prefs.getString("custom_endpoint", "") ?: ""
                if (model.isBlank() || endpoint.isBlank()) null
                else ProviderConfig(type, model, endpoint.trimEnd('/'))
            }
            else -> null
        }
    }

    /**
     * Tries each available API key in turn, stopping on success or
     * a non-retryable error. All providers use [openAIClient].
     */
    private suspend fun executeWithKeyRotation(
        source: AccessibilityNodeInfo,
        originalText: String,
        command: Command,
        config: ProviderConfig,
        callbacks: ProcessingCallbacks,
        initialSpinnerJob: Job?
    ): Job? {
        var spinnerJob = initialSpinnerJob
        var lastErrorMsg: String? = null
        var succeeded = false

        for (attempt in 0 until keyManager.getKeys().size.coerceAtLeast(1)) {
            val key = keyManager.getNextKey() ?: break
            if (spinnerJob == null) spinnerJob = startInlineSpinner(source, originalText)

            val result = openAIClient.generate(
                prompt = command.prompt,
                text = originalText,
                apiKey = key,
                model = config.model,
                temperature = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getFloat("temperature", DEFAULT_TEMPERATURE.toFloat()).toDouble(),
                endpoint = config.endpoint
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
            HapticHelper.performHapticFeedback(
                context, handler, HapticFeedbackConstants.REJECT
            )
            showFailureToast(lastErrorMsg)
        }
        return spinnerJob
    }

    /**
     * Shows an error toast using key-state context when no explicit
     * error message exists.
     */
    private suspend fun showFailureToast(lastErrorMsg: String?) {
        if (lastErrorMsg != null) {
            toastManager.showToast(ErrorMessageMapper.map(lastErrorMsg))
        } else {
            val waitMs = keyManager.getShortestWaitTimeMs()
            when {
                waitMs != null -> {
                    val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                    toastManager.showToast("API key rate limited. Try again in ${waitSec}s")
                }
                keyManager.getKeys().isEmpty() ->
                    toastManager.showToast("No API keys configured")
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

    /**
     * Holds resolved provider settings.
     *
     * @param type The provider identifier constant from [ProviderType].
     * @param model The model name to send in the request.
     * @param endpoint The base URL for the OpenAI-compatible API.
     */
    private data class ProviderConfig(
        val type: String,
        val model: String,
        val endpoint: String
    )
}
