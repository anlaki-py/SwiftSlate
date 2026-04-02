package com.musheer360.swiftslate.model

data class Command(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false,
    val isSystem: Boolean = false,  // Cannot be edited or deleted (e.g., undo)
    val isDynamic: Boolean = false  // Dynamic command like translate
)
