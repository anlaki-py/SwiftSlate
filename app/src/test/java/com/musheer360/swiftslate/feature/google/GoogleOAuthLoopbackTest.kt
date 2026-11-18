package com.musheer360.swiftslate.feature.google

import com.musheer360.swiftslate.feature.google.data.GoogleOAuthLoopback
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class GoogleOAuthLoopbackTest {
    @Test fun unrelatedRequestsDoNotConsumeTheValidLogin() = runBlocking {
        GoogleOAuthLoopback(0).use { callback ->
            callback.open()
            val port = callback.localPort
            val result = async { callback.awaitCode("expected", "received", "rejected") }
            suspend fun request(target: String): String = withContext(Dispatchers.IO) {
                Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 3000
                    socket.getOutputStream().write("GET $target HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                    socket.getInputStream().bufferedReader().readText()
                }
            }
            withTimeout(5000) {
                assertTrue(request("/oauth-callback?state=wrong&code=bad").startsWith("HTTP/1.1 400"))
                assertTrue(request("/oauth-callback?state=expected&code=valid").startsWith("HTTP/1.1 200"))
                assertEquals("valid", result.await())
            }
        }
    }

    @Test fun occupiedCallbackPortFailsClearlyWithoutTakingOverTheOtherListener() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { occupied ->
            GoogleOAuthLoopback(occupied.localPort).use { callback ->
                try { callback.open(); fail("Occupied port accepted") }
                catch (error: GoogleException) { assertEquals(GoogleFailure.LOGIN_PORT_BUSY, error.failure) }
                assertFalse(occupied.isClosed)
            }
        }
    }
}
