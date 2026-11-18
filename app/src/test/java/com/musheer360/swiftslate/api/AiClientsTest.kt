package com.musheer360.swiftslate.api

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AiClientsTest {
    private lateinit var server: MockWebServer
    private val completion = """{"choices":[{"finish_reason":"stop","message":{"content":"{\"text\":\"Corrected\"}"}}]}"""
    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.close() }
    private fun respond(code: Int, body: String) { server.enqueue(MockResponse.Builder().code(code).body(body).build()) }

    @Test fun negotiatesStructuredOutputOnlyAfterExplicitRejectionAndCachesCapability() = runBlocking {
        respond(400, """{"error":{"message":"response_format json_schema is not supported"}}""")
        respond(400, """{"error":{"param":"response_format","message":"unsupported"}}""")
        respond(200, completion)
        respond(200, completion)
        val client = OpenAICompatibleClient()
        val endpoint = server.url("/v1").toString().replace("localhost", "127.0.0.1")
        assertEquals("Corrected", client.generate("Fix", "input", "secret", "model", 0.7, endpoint).getOrThrow().text)
        assertEquals("Corrected", client.generate("Fix", "input", "secret", "model", 0.7, endpoint).getOrThrow().text)
        val requests = (0..3).map { JSONObject(server.takeRequest().body!!.utf8()) }
        assertEquals("json_schema", requests[0].getJSONObject("response_format").getString("type"))
        assertEquals("json_object", requests[1].getJSONObject("response_format").getString("type"))
        assertFalse(requests[2].has("response_format"))
        assertFalse(requests[3].has("response_format"))
    }

    @Test fun doesNotRetryRejectedOrAmbiguousGeneration() = runBlocking {
        respond(400, """{"error":{"message":"invalid temperature"}}""")
        val client = OpenAICompatibleClient()
        val endpoint = server.url("/v1").toString().replace("localhost", "127.0.0.1")
        assertTrue(client.generate("Fix", "input", "secret", "model", 0.7, endpoint).isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellingRequestCancelsSocketWhileReadingBody() = runBlocking {
        server.enqueue(MockResponse.Builder().body("delayed").bodyDelay(10, TimeUnit.SECONDS).build())
        val job = launch { AiHttpTransport().execute(Request.Builder().url(server.url("/slow")).build()) }
        val request = async(kotlinx.coroutines.Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) }
        assertNotNull(request.await())
        withTimeout(1500) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test fun rejectsOversizedResponses() = runBlocking {
        respond(200, "x".repeat(2 * 1024 * 1024 + 1))
        try {
            AiHttpTransport().execute(Request.Builder().url(server.url("/large")).build())
            fail("Oversized response accepted")
        } catch (error: java.io.IOException) { assertEquals("response_too_large", error.message) }
    }

    @Test fun refusalAndTruncationNeverReplaceInputWithPartialText() {
        for (reason in listOf("length", "content_filter")) {
            try {
                OpenAiProtocol.text("""{"choices":[{"finish_reason":"$reason","message":{"content":"partial"}}]}""")
                fail("Partial response accepted")
            } catch (_: ApiException) { }
        }
        assertEquals("Hello", OpenAiProtocol.text("""{"choices":[{"message":{"content":[{"type":"text","text":"Hello"}]}}]}"""))
    }

    @Test fun nativeGeminiUsesHeaderKeysAndPaginatesModelList() = runBlocking {
        respond(200, """{"models":[{"name":"models/gemini-flash","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next"}""")
        respond(200, """{"models":[{"name":"models/gemini-pro"},{"name":"models/gemini-embedding"}]}""")
        val endpoint = server.url("/v1beta").toString().replace("localhost", "127.0.0.1")
        assertEquals(listOf("gemini-flash", "gemini-pro"), GeminiApiKeyClient(AiHttpTransport()).models("secret", endpoint).getOrThrow())
        val first = server.takeRequest()
        assertEquals("secret", first.headers["x-goog-api-key"])
        assertNull(first.url.queryParameter("key"))
        assertEquals("next", server.takeRequest().url.queryParameter("pageToken"))
    }

    @Test fun followsNoRedirectsWithCredentials() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://localhost:${server.port}/other").build())
        val response = AiHttpTransport().execute(Request.Builder().url(server.url("/redirect"))
            .header("Authorization", "Bearer secret").build())
        assertEquals(302, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun reasoningModelsCanExplicitlyRejectCustomTemperature() = runBlocking {
        respond(400, """{"error":{"param":"temperature","message":"Only the default value is supported"}}""")
        respond(200, completion)
        val endpoint = server.url("/v1").toString().replace("localhost", "127.0.0.1")
        assertTrue(OpenAICompatibleClient().generate("Fix", "input", "secret", "reasoning-model", 0.7, endpoint).isSuccess)
        assertTrue(JSONObject(server.takeRequest().body!!.utf8()).has("temperature"))
        assertFalse(JSONObject(server.takeRequest().body!!.utf8()).has("temperature"))
    }
}
