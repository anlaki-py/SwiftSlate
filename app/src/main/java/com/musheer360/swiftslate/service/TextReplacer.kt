package com.musheer360.swiftslate.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Handles text replacement in accessibility nodes using [AccessibilityNodeInfo.ACTION_SET_TEXT]
 * with a clipboard-paste fallback for apps that silently ignore direct text setting
 * (e.g. Firefox, Google Keep). Also manages post-replacement verification to
 * guard against IME clobbering.
 *
 * @param context Application context for clipboard access.
 * @param handler Main-thread handler for scheduling verification callbacks.
 */
class TextReplacer(
    private val context: Context,
    private val handler: Handler
) {

    /**
     * The text most recently written to a field.
     * Read by the service to debounce IME re-commit events.
     */
    @Volatile
    var lastReplacedText: String? = null
        private set

    /**
     * Timestamp of the last replacement.
     * Used alongside [lastReplacedText] for debounce window checks.
     */
    @Volatile
    var lastReplacedAt = 0L
        private set

    /**
     * The [AccessibilityNodeInfo] last written to.
     * Ownership is managed by [scheduleTextVerification]; callers must use
     * [recycleIfUnowned] to avoid double-recycle.
     */
    @Volatile
    var lastReplacedSource: AccessibilityNodeInfo? = null
        private set

    private var verifyRunnable: Runnable? = null

    /**
     * Replaces all text in the given accessibility node with [newText].
     * First attempts [AccessibilityNodeInfo.ACTION_SET_TEXT]; if the text
     * doesn't persist, falls back to select-all + clipboard paste.
     * Schedules a post-replace verification to re-apply the text if the
     * IME clobbers it.
     *
     * @param source The accessibility node whose text will be replaced.
     * @param newText The replacement text.
     */
    suspend fun replaceText(source: AccessibilityNodeInfo, newText: String) = withContext(Dispatchers.Main) {
        if (!source.refresh()) return@withContext
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)

        val success = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        if (success) {
            // Verify the text actually persisted — some apps (Firefox, Google Keep)
            // return true but don't update their internal text state
            delay(100)
            source.refresh()
            val currentText = source.text?.toString()
            if (currentText == newText) {
                scheduleTextVerification(source, newText)
                return@withContext // Text persisted
            }
            // Text didn't persist, fall through to clipboard fallback
        }

        // Clipboard fallback: select all + paste (goes through app's input pipeline)
        pasteViaClipboard(source, newText)
    }

    /**
     * Sets the text of the given accessibility node directly via
     * [AccessibilityNodeInfo.ACTION_SET_TEXT]. Used by the inline spinner
     * to update the field without clipboard side-effects.
     *
     * @param source The target node.
     * @param text The text to set.
     * @return True if the action was performed successfully.
     */
    fun setFieldText(source: AccessibilityNodeInfo, text: String): Boolean {
        if (!source.refresh()) return false
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    /**
     * Recycles [source] only if it was NOT captured by [scheduleTextVerification].
     * Prevents double-recycle when verification has taken ownership of the node.
     *
     * @param source The node to conditionally recycle.
     */
    fun recycleIfUnowned(source: AccessibilityNodeInfo) {
        if (lastReplacedSource !== source) {
            try { source.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Clears all tracked replacement state and recycles any held node.
     * Called during service interrupt or destruction.
     */
    fun clearState() {
        verifyRunnable?.let { handler.removeCallbacks(it) }
        verifyRunnable = null
        lastReplacedText = null
        lastReplacedAt = 0L
        try { lastReplacedSource?.recycle() } catch (_: Exception) {}
        lastReplacedSource = null
    }

    /**
     * Clears debounce state when a text field is emptied, preventing
     * stale replaced-text matches on subsequent events.
     * Does NOT recycle [source] — the caller is responsible for that.
     *
     * @param source The current event source node.
     */
    fun handleEmptyField(source: AccessibilityNodeInfo) {
        verifyRunnable?.let { handler.removeCallbacks(it) }
        lastReplacedText = null
        val prev = lastReplacedSource
        lastReplacedSource = null
        if (prev != null && prev !== source) {
            try { prev.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Clipboard fallback: copies [newText] to the clipboard, selects all text
     * in the field, and pastes. Restores the original clipboard contents after
     * a short delay if the paste succeeded.
     *
     * @param source The accessibility node to paste into.
     * @param newText The text to paste.
     */
    private fun pasteViaClipboard(source: AccessibilityNodeInfo, newText: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val oldClip = clipboard.primaryClip
        val newClip = ClipData.newPlainText("SwiftSlate Result", newText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            newClip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(newClip)

        source.refresh()
        if (source.text == null) return
        val selectAllArgs = Bundle()
        selectAllArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
        selectAllArgs.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
            source.text?.length ?: 0
        )
        source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllArgs)
        source.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        scheduleTextVerification(source, newText)

        // Restore the original clipboard after a short delay if our text is still in it
        handler.postDelayed({
            try {
                source.refresh()
                val fieldText = source.text?.toString()
                if (fieldText == newText) {
                    val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    if (current == newText) {
                        if (oldClip != null) {
                            clipboard.setPrimaryClip(oldClip)
                        } else {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            } catch (_: Exception) {}
        }, 500)
    }

    /**
     * Schedules a delayed check to guard against IME clobbering after text replacement.
     * If the field text was truncated (IME re-commit race), re-applies the expected text.
     * Takes ownership of [source]; the node will be recycled when the verification completes.
     *
     * @param source The accessibility node to verify.
     * @param expectedText The text that should be present in the field.
     */
    private fun scheduleTextVerification(source: AccessibilityNodeInfo, expectedText: String) {
        lastReplacedText = expectedText
        lastReplacedAt = System.currentTimeMillis()
        // Recycle the previous source if it's a different node
        val prev = lastReplacedSource
        if (prev != null && prev !== source) {
            try { prev.recycle() } catch (_: Exception) {}
        }
        lastReplacedSource = source
        verifyRunnable?.let { handler.removeCallbacks(it) }
        val capturedSource = source
        val runnable = Runnable {
            try {
                if (!capturedSource.refresh()) return@Runnable
                val currentText = capturedSource.text?.toString()
                // Detect IME clobber: field text is a prefix of expected text but shorter
                val isImeClobber = currentText != null &&
                    currentText.isNotEmpty() &&
                    expectedText.startsWith(currentText)
                if (isImeClobber && currentText != expectedText &&
                    currentText!!.length < expectedText.length
                ) {
                    val bundle = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            expectedText
                        )
                    }
                    capturedSource.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                }
            } catch (_: Exception) {
            } finally {
                // Only recycle if this source is still the current one (not replaced by a newer command)
                if (lastReplacedSource === capturedSource) {
                    lastReplacedText = null
                    try { capturedSource.recycle() } catch (_: Exception) {}
                    lastReplacedSource = null
                }
            }
        }
        verifyRunnable = runnable
        if (!handler.postDelayed(runnable, 300)) {
            lastReplacedText = null
            lastReplacedAt = 0L
            lastReplacedSource = null
        }
    }
}
