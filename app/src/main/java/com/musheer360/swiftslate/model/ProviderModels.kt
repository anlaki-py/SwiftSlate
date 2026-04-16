package com.musheer360.swiftslate.model

/**
 * Represents a user-defined AI provider with its connection details.
 *
 * @param id Unique identifier (UUID), generated on creation.
 * @param name User-given display name (e.g. "My Gemini", "Work API").
 * @param endpoint OpenAI-compatible base URL (e.g. "https://api.groq.com/openai/v1").
 * @param selectedModel Currently selected model ID for this provider.
 */
data class Provider(
    val id: String,
    val name: String,
    val endpoint: String,
    val selectedModel: String = ""
)
