package com.musheer360.swiftslate.feature.google

import com.musheer360.swiftslate.api.AiHttpResponse
import com.musheer360.swiftslate.api.GeminiProtocol
import com.musheer360.swiftslate.domain.EndpointValidation
import com.musheer360.swiftslate.domain.EndpointValidationResult
import com.musheer360.swiftslate.feature.google.data.GoogleAccountApi
import com.musheer360.swiftslate.feature.google.data.GoogleOAuthChallenge
import com.musheer360.swiftslate.feature.google.data.GoogleOAuthLoopback
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import com.musheer360.swiftslate.feature.google.domain.SelectGoogleAccount
import okhttp3.Headers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class GoogleProtocolTest {
    @Test fun ignoresThinkingAndCombinesIncrementalSseEvents() {
        val events = """data: {"response":{"candidates":[{"content":{"parts":[{"text":"private","thought":true},{"text":"Hello "}]}}]}}

            |data: {"response":{"candidates":[{"content":{"parts":[{"text":"world"}]},"finishReason":"STOP"}]}}
            |
            |data: [DONE]
            |
        """.trimMargin()
        assertEquals("Hello world", GeminiProtocol.text(events))
    }

    @Test fun rejectsTruncatedAndBlockedGeminiResponses() {
        for (reason in listOf("MAX_TOKENS", "SAFETY", "RECITATION")) {
            try {
                GeminiProtocol.text("""{"candidates":[{"content":{"parts":[{"text":"partial"}]},"finishReason":"$reason"}]}""")
                fail("Partial response accepted")
            } catch (_: GoogleException) { }
        }
    }

    @Test fun handlesModelAndQuotaShapesWithoutPromisingUnavailableModels() {
        val root = JSONObject("""{"models":{"gemini-flash":{"quotaInfo":{"remainingFraction":0.5}},"gemini-pro":{"dailyQuotaInfo":{"remainingFraction":0.2}},"gemini-image":{},"claude-test":{}}}""")
        assertEquals(listOf("gemini-flash", "gemini-pro"), GeminiProtocol.modelIds(root))
        assertEquals(0.2, GeminiProtocol.remainingFraction(root)!!, 0.001)
        assertNull(GeminiProtocol.remainingFraction(JSONObject("""{"models":{"gemini-test":{}}}""")))
        assertEquals("project", GoogleAccountApi.projectId(JSONObject("""{"id":"project"}""")))
    }

    @Test fun accountSelectionRespectsAllUnavailableStatesAndRoundRobin() {
        val ready = GoogleAccount("a", "a@example.com", "A")
        val accounts = listOf(ready.copy(id="disabled", isEnabled=false), ready.copy(id="invalid", needsLogin=true),
            ready.copy(id="limited", cooldownUntil=200), ready, ready.copy(id="b"))
        assertEquals("a", SelectGoogleAccount(accounts, 0, emptySet(), 100)?.id)
        assertEquals("b", SelectGoogleAccount(accounts, 4, emptySet(), 100)?.id)
        assertNull(SelectGoogleAccount(accounts, 0, setOf("a", "b"), 100))
        assertNull(SelectGoogleAccount(emptyList(), 0, emptySet(), 100))
    }

    @Test fun callbackRequiresExactPathStateAndUnambiguousParameters() {
        val valid = "/oauth-callback?state=expected&code=one"
        assertEquals("one", GoogleOAuthLoopback.callbackParameters(valid, "expected")?.first)
        for (target in listOf(valid.replace("expected", "wrong"), valid + "&state=expected", valid + "&error=access_denied",
            valid.replace("oauth-callback", "oauth-callback-extra"), "//evil.test/oauth-callback?state=expected&code=one")) {
            assertNull(GoogleOAuthLoopback.callbackParameters(target, "expected"))
        }
        assertEquals("access_denied", GoogleOAuthLoopback.callbackParameters("/oauth-callback?state=expected&error=access_denied", "expected")?.second)
    }

    @Test fun pkceAndStateAreIndependentRandomSecrets() {
        val first = GoogleOAuthChallenge.create()
        val second = GoogleOAuthChallenge.create()
        assertEquals(43, first.verifier.length)
        assertEquals(43, first.challenge.length)
        assertNotEquals(first.state, first.verifier)
        assertNotEquals(first.state, second.state)
        assertFalse(first.toString().contains(first.verifier))
    }

    @Test fun verificationLinksAreRestrictedToGoogleAndRetryDelayIsBounded() {
        val error = GoogleAccountApi.failure(AiHttpResponse(403,
            """{"error":{"details":[{"metadata":{"validation_url":"https://accounts.google.com/verify"}}]}}""", Headers.headersOf()))
        assertEquals(GoogleFailure.VERIFY_ACCOUNT, error.failure)
        val evil = GoogleAccountApi.failure(AiHttpResponse(403,
            """{"error":{"details":[{"metadata":{"validation_url":"https://google.com.evil.test/verify"}}]}}""", Headers.headersOf()))
        assertNull(evil.verificationUrl)
        val limited = GoogleAccountApi.failure(AiHttpResponse(429, "{}", Headers.headersOf("Retry-After", "9999999")))
        assertEquals(86_400_000L, limited.retryAfterMillis)
    }

    @Test fun endpointsRejectEmbeddedCredentialsAndMalformedHttps() {
        for (url in listOf("https://", "https://user:password@example.com/v1", "https://example.com/v1?key=secret", "https://example.com/#fragment", "http://example.com")) {
            assertTrue(EndpointValidation.validate(url) is EndpointValidationResult.Error)
        }
        assertTrue(EndpointValidation.validate("https://example.com/v1") is EndpointValidationResult.Valid)
        assertTrue(EndpointValidation.validate("http://127.0.0.1:1234/v1") is EndpointValidationResult.Valid)
    }
}
