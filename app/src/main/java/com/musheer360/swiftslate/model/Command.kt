package com.musheer360.swiftslate.model

import androidx.compose.runtime.Immutable

enum class CommandType {
    AI, TEXT_REPLACER
}

/**
 * Represents a text command that can be triggered by the user.
 *
 * @param trigger The full trigger string including prefix (e.g. "?fix").
 * @param prompt The AI prompt or replacement text for this command.
 * @param isBuiltIn Whether this command is a built-in (true) or custom (false).
 * @param type The type of command — AI-powered or literal text replacement.
 * @param builtInKey The original built-in name (e.g. "fix", "translate") for tracking
 *                   overrides. Null for custom commands.
 * @param isOverridden Whether this built-in command has been modified from its default.
 * @param description Optional brief description shown in the commands list.
 *                    Falls back to a prompt snippet when empty.
 */
@Immutable
data class Command(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false,
    val type: CommandType = CommandType.AI,
    val builtInKey: String? = null,
    val isOverridden: Boolean = false,
    val description: String = ""
)
