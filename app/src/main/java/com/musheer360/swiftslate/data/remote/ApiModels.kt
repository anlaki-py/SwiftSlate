package com.musheer360.swiftslate.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String,
    @SerialName("json_schema")
    val jsonSchema: JsonSchema? = null
)

@Serializable
data class JsonSchema(
    val name: String,
    val strict: Boolean = true,
    val schema: Schema
)

@Serializable
data class Schema(
    val type: String = "object",
    val properties: Map<String, Property>,
    val required: List<String>,
    @SerialName("additionalProperties")
    val additionalProperties: Boolean = false
)

@Serializable
data class Property(
    val type: String
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>? = null,
    val error: ApiErrorResponse? = null
)

@Serializable
data class Choice(
    val message: MessageResponse? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class MessageResponse(
    val content: String? = null
)

@Serializable
data class ApiErrorResponse(
    val message: String? = null
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

@Serializable
data class ModelInfo(
    val id: String
)
