package com.musheer360.swiftslate.domain

import com.musheer360.swiftslate.model.Command

/**
 * Result of validating a command trigger before saving.
 * Either [Valid] (proceed with save) or [Error] (display message to user).
 */
sealed class CommandValidationResult {
    /** Trigger passed all validation checks. */
    data object Valid : CommandValidationResult()

    /**
     * Trigger failed validation.
     *
     * @param messageKey Identifies which error message to show. One of:
     *   `"prefix"`, `"empty_trigger"`, `"duplicate"`, or `"conflict"`.
     * @param conflictTrigger The trigger that caused a conflict, if applicable.
     */
    data class Error(
        val messageKey: String,
        val conflictTrigger: String? = null
    ) : CommandValidationResult()
}

/**
 * Pure validation logic for command triggers, extracted from the UI layer.
 * Checks prefix conformance, emptiness, duplicates, and prefix-overlap conflicts.
 */
object CommandValidation {

    /**
     * Validates a trimmed trigger string against all business rules.
     *
     * @param trimmedTrigger The trigger text after trimming whitespace.
     * @param prefix The current trigger prefix (e.g. "?").
     * @param existingCommands All commands currently registered.
     * @param editingTrigger The original trigger of the command being edited,
     *   or null when adding a new command. Excluded from duplicate/conflict checks.
     * @return [CommandValidationResult.Valid] if the trigger is acceptable,
     *   or [CommandValidationResult.Error] with a message key describing the failure.
     */
    fun validate(
        trimmedTrigger: String,
        prefix: String,
        existingCommands: List<Command>,
        editingTrigger: String?
    ): CommandValidationResult {
        // Must start with the configured prefix character
        if (!trimmedTrigger.startsWith(prefix)) {
            return CommandValidationResult.Error("prefix")
        }

        // Must contain something after the prefix
        if (trimmedTrigger == prefix || trimmedTrigger.length <= prefix.length) {
            return CommandValidationResult.Error("empty_trigger")
        }

        // No two commands may share the same trigger
        val isDuplicate = existingCommands.any {
            it.trigger == trimmedTrigger && it.trigger != editingTrigger
        }
        if (isDuplicate) {
            return CommandValidationResult.Error("duplicate")
        }

        // Prefix-overlap conflict — prevents ambiguous matching at runtime
        val conflicting = existingCommands.firstOrNull {
            it.trigger != editingTrigger &&
                (it.trigger.startsWith(trimmedTrigger) || trimmedTrigger.startsWith(it.trigger))
        }
        if (conflicting != null) {
            return CommandValidationResult.Error("conflict", conflicting.trigger)
        }

        return CommandValidationResult.Valid
    }
}
