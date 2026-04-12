package com.musheer360.swiftslate.manager

/**
 * Static definitions for built-in commands.
 * Extracted from CommandManager to keep file sizes manageable.
 */
object CommandConstants {

    /** Placeholder token in translate prompt templates, replaced with actual language code. */
    const val LANG_PLACEHOLDER = "{lang}"

    /** Default trigger name for the translate command (without prefix or colon). */
    const val DEFAULT_TRANSLATE_TRIGGER_NAME = "translate"

    /** Default prompt template for the translate command. */
    const val DEFAULT_TRANSLATE_PROMPT = "Translate to language code '$LANG_PLACEHOLDER'."

    /** Built-in command keys that cannot be deleted by the user. */
    val UNDELETABLE_KEYS = setOf("translate", "undo")

    /**
     * Built-in command definitions as (key, prompt) pairs.
     * The key is the command name without prefix (e.g. "fix", not "?fix").
     * Translate is excluded — it has special parametric handling in CommandManager.
     */
    val BUILT_IN_DEFINITIONS = listOf(
        "fix" to "Fix all grammar, spelling, and punctuation errors.",
        "improve" to "Improve the clarity and readability.",
        "shorten" to "Shorten while preserving the core meaning.",
        "expand" to "Expand with more detail and context.",
        "formal" to "Rewrite in a formal, professional tone.",
        "casual" to "Rewrite in a casual, friendly tone.",
        "emoji" to "Add relevant emojis throughout.",
        "reply" to "Generate a contextual reply to this message.",
        "undo" to "Undo the last replacement and restore the original text."
    )
}
