package com.musheer360.swiftslate.data

/**
 * Result of a backup import operation.
 * Carries specific error information instead of a generic boolean.
 */
sealed class BackupResult {

    /** Import succeeded. */
    data object Success : BackupResult()

    /**
     * Import failed with a specific reason.
     *
     * @param messageKey Identifies which error message to show. One of:
     *   `"invalid_format"`, `"version_unsupported"`, `"checksum_mismatch"`,
     *   `"too_many_commands"`, `"invalid_trigger"`, `"invalid_prompt"`,
     *   `"invalid_type"`, `"parse_error"`.
     */
    data class Error(val messageKey: String) : BackupResult()
}
