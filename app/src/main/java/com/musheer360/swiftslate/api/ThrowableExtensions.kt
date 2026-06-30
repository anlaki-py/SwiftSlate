package com.musheer360.swiftslate.api

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Returns `true` for errors that are likely transient and worth retrying:
 * network-level failures (timeout, DNS, connection refused) and
 * server-side errors (502, 503, 504).
 */
internal fun Throwable?.isTransient(): Boolean = when (this) {
    is SocketTimeoutException, is UnknownHostException, is ConnectException -> true
    is ApiException -> apiError is ApiError.Network || apiError is ApiError.ServerError
    else -> false
}
