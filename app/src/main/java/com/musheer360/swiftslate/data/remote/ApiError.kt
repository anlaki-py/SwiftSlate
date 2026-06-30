package com.musheer360.swiftslate.data.remote

sealed interface ApiError {
    data class RateLimit(val message: String, val retryAfterSeconds: Int? = null) : ApiError
    data class InvalidKey(val message: String) : ApiError
    data class Network(val message: String) : ApiError
    data class ServerError(val message: String) : ApiError
    data class Other(val message: String) : ApiError
}

class ApiException(val apiError: ApiError, message: String) : Exception(message)

data class GenerateResult(val text: String)
