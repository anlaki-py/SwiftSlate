package com.musheer360.swiftslate.feature.google.data

import android.util.Base64
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class GoogleOAuthChallenge(val state: String, val verifier: String, val challenge: String) {
    override fun toString(): String = "GoogleOAuthChallenge(redacted)"

    companion object {
        fun create(): GoogleOAuthChallenge {
            fun random(): String = encode(ByteArray(32).also { SecureRandom().nextBytes(it) })
            val verifier = random()
            return GoogleOAuthChallenge(random(), verifier, encode(MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))))
        }
        private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

/** Fixed-port callback, bound only to loopback. Unrelated requests cannot consume a login session. */
internal class GoogleOAuthLoopback(private val port: Int = GoogleOAuthSettings.CALLBACK_PORT) : AutoCloseable {
    private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null

    fun open() {
        try {
            server = ServerSocket()
            server!!.apply {
                reuseAddress = false
                bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                soTimeout = 500
            }
        } catch (error: Exception) {
            close()
            throw GoogleException(GoogleFailure.LOGIN_PORT_BUSY)
        }
    }

    val localPort: Int get() = server?.localPort ?: error("callback_not_open")

    suspend fun awaitCode(expectedState: String, successText: String, failureText: String): String = withContext(Dispatchers.IO) {
        val listener = checkNotNull(server)
        var authorizationCode: String? = null
        while (authorizationCode == null) {
            currentCoroutineContext().ensureActive()
            val accepted = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
            socket = accepted
            accepted.use { client ->
                client.soTimeout = 2000
                val target = try {
                    val header = StringBuilder()
                    val input = client.getInputStream()
                    while (!header.endsWith("\r\n\r\n") && !header.endsWith("\n\n")) {
                        val byte = input.read()
                        if (byte < 0 || header.length >= 8192) throw IllegalArgumentException("invalid_http_request")
                        header.append(byte.toChar())
                    }
                    header.lineSequence().first().split(' ').takeIf { it.size == 3 && it[0] == "GET" }?.get(1)
                } catch (_: Exception) { null }
                val parameters = callbackParameters(target, expectedState)
                if (parameters == null) {
                    reply(client, 400, failureText)
                } else {
                    val code = parameters.first
                    val error = parameters.second
                    reply(client, if (code != null) 200 else 400, if (code != null) successText else failureText)
                    if (error != null) throw GoogleException(if (error == "access_denied") GoogleFailure.LOGIN_CANCELLED else GoogleFailure.ACCESS_DENIED)
                    if (code != null) authorizationCode = code
                }
            }
            socket = null
        }
        checkNotNull(authorizationCode)
    }

    override fun close() {
        try { socket?.close() } finally { server?.close() }
        socket = null
        server = null
    }

    private fun reply(socket: Socket, status: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status ${if (status == 200) "OK" else "Bad Request"}\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n"
        // A browser disconnect after sending a valid callback must not discard the authorization.
        try {
            socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII) + bytes)
            socket.getOutputStream().flush()
        } catch (_: java.io.IOException) {
            android.util.Log.d("GoogleOAuth", "Callback browser disconnected before acknowledgement")
        }
    }

    companion object {
        fun callbackParameters(target: String?, expectedState: String): Pair<String?, String?>? {
            if (target == null || !target.startsWith('/') || target.startsWith("//")) return null
            val url = runCatching { ("http://localhost$target").toHttpUrl() }.getOrNull() ?: return null
            if (url.encodedPath != GoogleOAuthSettings.CALLBACK_PATH || url.fragment != null) return null
            if (listOf("state", "code", "error").any { url.queryParameterValues(it).size > 1 }) return null
            val state = url.queryParameter("state") ?: return null
            if (!MessageDigest.isEqual(state.toByteArray(), expectedState.toByteArray())) return null
            val code = url.queryParameter("code")?.takeIf { it.isNotBlank() && it.length <= 4096 }
            val error = url.queryParameter("error")?.takeIf { it.isNotBlank() }
            if ((code == null) == (error == null)) return null
            return code to error
        }
    }
}
