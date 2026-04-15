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
import com.musheer360.swiftslate.model.AiProvider
import com.musheer360.swiftslate.model.Command
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

class AiCommandProcessor(
    private val context: Context,
    private val keyManager: KeyManager,
    private val providerManager: ProviderManager,
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

    private fun sourceId(source: AccessibilityNodeInfo): String =
        "${source.windowId}:${source.viewIdResourceName ?: source.hashCode()}"

    fun processCommand(
        source: AccessibilityNodeInfo,
        text: String,
        command: Command,
        callbacks: ProcessingCallbacks
    ): Job {
        return serviceScope.launch {
            val thisJob = coroutineContext[Job]!!
            val providerConfig = providerManager.getActiveProvider()

            if (providerConfig == null || providerConfig.selectedModel.isBlank() || providerConfig.endpoint.isBlank()) {
                toastManager.showToast(
                    "Provider not fully configured. Set endpoint and model in Settings."
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

    private suspend fun executeWithKeyRotation(
        source: AccessibilityNodeInfo,
        originalText: String,
        command: Command,
        config: AiProvider,
        callbacks: ProcessingCallbacks,
        initialSpinnerJob: Job?
    ): Job? {
        var spinnerJob = initialSpinnerJob
        var lastErrorMsg: String? = null
        var succeeded = false

        for (attempt in 0 until keyManager.getKeys(config.id).size.coerceAtLeast(1)) {
            val key = keyManager.getNextKey(config.id) ?: break
            if (spinnerJob == null) spinnerJob = startInlineSpinner(source, originalText)

            val result = openAIClient.generate(
                prompt = command.prompt,
                text = originalText,
                apiKey = key,
                model = config.selectedModel,
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
            showFailureToast(config.id, lastErrorMsg)
        }
        return spinnerJob
    }

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
