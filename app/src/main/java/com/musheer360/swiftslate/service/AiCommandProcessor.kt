package com.musheer360.swiftslate.service

import android.content.Context
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityNodeInfo
import com.musheer360.swiftslate.data.remote.OpenAiClient
import com.musheer360.swiftslate.data.repository.KeyRepository
import com.musheer360.swiftslate.data.repository.ProviderRepository
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.data.remote.ApiError
import com.musheer360.swiftslate.data.remote.ApiException
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
import timber.log.Timber

class AiCommandProcessor(
    private val context: Context,
    private val providerRepository: ProviderRepository,
    private val keyRepository: KeyRepository,
    private val openAiClient: OpenAiClient,
    private val textReplacer: TextReplacer,
    private val toastManager: OverlayToastManager,
    private val serviceScope: CoroutineScope,
    private val handler: Handler
) {

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.7
        val SPINNER_FRAMES = arrayOf("\u25D0", "\u25D3", "\u25D1", "\u25D2")
    }

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
            val provider = providerRepository.getActiveProvider()

            if (provider == null) {
                toastManager.showToast("No provider configured. Add one in Settings.")
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    try { source.recycle() } catch (_: Exception) {}
                }
                return@launch
            }

            if (provider.selectedModel.isBlank()) {
                toastManager.showToast("No model selected. Choose one in Settings.")
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    try { source.recycle() } catch (_: Exception) {}
                }
                return@launch
            }

            var spinnerJob: Job? = null
            val timeoutSecs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                .getFloat("timeout", 10f)

            try {
                withTimeout((timeoutSecs * 1000).toLong()) {
                    spinnerJob = executeWithKeyRotation(
                        source, text, command, provider,
                        temperature.toDouble(), callbacks, spinnerJob, thisJob
                    )
                }
            } catch (_: TimeoutCancellationException) {
                spinnerJob?.cancelAndJoin()
                try { textReplacer.replaceText(source, text) } catch (_: Exception) {}
                toastManager.showToast("Request timed out")
                Timber.w("AI request timed out after %ds", timeoutSecs.toInt())
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
                Timber.e(e, "AI command processing failed")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    callbacks.onProcessingComplete(thisJob)
                    spinnerJob?.cancel()
                    try { source.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun executeWithKeyRotation(
        source: AccessibilityNodeInfo,
        originalText: String,
        command: Command,
        provider: Provider,
        temperature: Double,
        callbacks: ProcessingCallbacks,
        initialSpinnerJob: Job?,
        parentJobToCancel: Job?
    ): Job? {
        var spinnerJob = initialSpinnerJob
        var lastErrorMsg: String? = null
        var succeeded = false

        val keyCount = keyRepository.getKeys(provider.id).size.coerceAtLeast(1)
        for (attempt in 0 until keyCount) {
            val key = keyRepository.getNextKey(provider.id) ?: break
            if (spinnerJob == null) spinnerJob = startInlineSpinner(source, originalText, parentJobToCancel)

            val result = openAiClient.generate(
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
                    keyRepository.reportRateLimit(key, secs)
                    Timber.d("Key rate-limited, rotating after %ds", secs)
                }
                is ApiError.InvalidKey -> {
                    keyRepository.markInvalid(key)
                    Timber.d("Key marked invalid, rotating")
                }
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

    private suspend fun showFailureToast(providerId: String, lastErrorMsg: String?) {
        if (lastErrorMsg != null) {
            toastManager.showToast(ErrorMessageMapper.map(lastErrorMsg))
        } else {
            val waitMs = keyRepository.getShortestWaitTimeMs(providerId)
            when {
                waitMs != null -> {
                    val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                    toastManager.showToast("API key rate limited. Try again in ${waitSec}s")
                }
                keyRepository.getKeys(providerId).isEmpty() ->
                    toastManager.showToast("No API keys configured for this provider")
                else ->
                    toastManager.showToast("All API keys are invalid. Please check your keys")
            }
        }
    }

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

    private fun sourceId(source: AccessibilityNodeInfo): String =
        "${source.windowId}:${source.viewIdResourceName ?: source.hashCode()}"
}
