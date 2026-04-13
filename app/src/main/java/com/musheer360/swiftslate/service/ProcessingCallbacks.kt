package com.musheer360.swiftslate.service

import kotlinx.coroutines.Job

/**
 * Callback interface for [AiCommandProcessor] to communicate processing
 * state changes back to the orchestrating [AssistantService].
 */
interface ProcessingCallbacks {

    /**
     * Called when an AI request finishes (success or failure).
     * The implementation should verify job identity before cleaning up
     * processing state (watchdog, flags, etc.).
     *
     * @param job The job that completed, for identity comparison with the current job.
     */
    fun onProcessingComplete(job: Job)

    /**
     * Called when original text is captured before replacement, enabling undo.
     *
     * @param text The original text before AI transformation.
     * @param sourceId Unique identifier for the source accessibility node.
     */
    fun onOriginalTextCaptured(text: String, sourceId: String)
}
