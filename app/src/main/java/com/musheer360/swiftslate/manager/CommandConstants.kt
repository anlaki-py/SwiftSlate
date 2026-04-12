package com.musheer360.swiftslate.manager

/**
 * Static definitions for built-in commands.
 * Extracted from CommandManager to keep file sizes manageable.
 */
object CommandConstants {

    /**
     * Holds the default definition for a built-in command.
     *
     * @param key The command name without prefix (e.g. "fix").
     * @param prompt The AI prompt sent to the model.
     * @param description Brief user-facing explanation shown in the list.
     */
    data class BuiltInDef(val key: String, val prompt: String, val description: String)

    /** Placeholder token in translate prompt templates, replaced with actual language code. */
    const val LANG_PLACEHOLDER = "{lang}"

    /** Default trigger name for the translate command (without prefix or colon). */
    const val DEFAULT_TRANSLATE_TRIGGER_NAME = "tr"

    /** Default prompt template for the translate command. */
    const val DEFAULT_TRANSLATE_PROMPT = "Translate to language code '$LANG_PLACEHOLDER'."

    /** Default description for the translate command. */
    const val DEFAULT_TRANSLATE_DESCRIPTION = "Translate to specified language"

    /** Built-in command keys that cannot be deleted by the user. */
    val UNDELETABLE_KEYS = setOf("translate", "undo")

    /**
     * Built-in command definitions.
     * The key is the command name without prefix (e.g. "fix", not "?fix").
     * Translate is excluded — it has special parametric handling in CommandManager.
     */
    val BUILT_IN_DEFINITIONS = listOf(
        BuiltInDef("fix", "Fix all grammar, spelling, and punctuation errors.", "Fix grammar, spelling & punctuation"),
        BuiltInDef("improve", "Improve the clarity and readability.", "Improve clarity and readability"),
        BuiltInDef("shorten", "Shorten while preserving the core meaning.", "Shorten while keeping meaning"),
        BuiltInDef("expand", "Expand with more detail and context.", "Expand with more detail"),
        BuiltInDef("formal", "Rewrite in a formal, professional tone.", "Rewrite in formal tone"),
        BuiltInDef("casual", "Rewrite in a casual, friendly tone.", "Rewrite in casual tone"),
        BuiltInDef("emoji", "Add relevant emojis throughout.", "Add relevant emojis"),
        BuiltInDef("reply", "Generate a contextual reply to this message.", "Generate a contextual reply"),
        BuiltInDef("undo", "Undo the last replacement and restore the original text.", "Undo last replacement")
    )
}
