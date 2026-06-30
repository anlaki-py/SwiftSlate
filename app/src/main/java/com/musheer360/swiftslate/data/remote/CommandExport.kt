package com.musheer360.swiftslate.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CommandExport(
    val trigger: String,
    val prompt: String,
    val type: String = "AI",
    val description: String = ""
)
