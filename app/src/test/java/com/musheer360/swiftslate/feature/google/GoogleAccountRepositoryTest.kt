package com.musheer360.swiftslate.feature.google

import com.musheer360.swiftslate.api.AiHttpTransport
import com.musheer360.swiftslate.feature.google.data.GoogleAccountApi
import com.musheer360.swiftslate.feature.google.data.GoogleAccountRepository
import com.musheer360.swiftslate.feature.google.data.GoogleAccountStore
import com.musheer360.swiftslate.feature.google.data.GoogleCredential
import com.musheer360.swiftslate.feature.google.data.GoogleOAuthSettings
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class GoogleAccountRepositoryTest {
    private lateinit var server: MockWebServer
    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun cleanup() { server.close() }
    private fun credential(id: String, expires: Long = Long.MAX_VALUE) =
        GoogleCredential(GoogleAccount(id, "$id@example.com", id), "project", "access-$id", "refresh-$id", expires)
    private class Store(var saved: List<GoogleCredential>) : GoogleAccountStore {
        var failWrite = false
        override fun read() = saved
        override fun write(accounts: List<GoogleCredential>) {
            if (failWrite) throw GoogleException(GoogleFailure.STORAGE)
            saved = accounts
        }
    }
    private fun repository(store: Store): GoogleAccountRepository {
        val http = AiHttpTransport(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val original = chain.request()
            chain.proceed(original.newBuilder().url(server.url(original.url.encodedPath +
                (original.url.encodedQuery?.let { "?$it" } ?: ""))).build())
        }.build())
        return GoogleAccountRepository(store, GoogleAccountApi(http, GoogleOAuthSettings("test-client", "test-secret")))
    }
    private fun respond(code: Int, body: String) { server.enqueue(MockResponse.Builder().code(code).body(body).build()) }
    private val generated = """{"response":{"candidates":[{"content":{"parts":[{"text":"Fixed"}]},"finishReason":"STOP"}]}}"""

    @Test fun concurrentRequestsRefreshOneAccountOnlyOnce() = runBlocking {
        val store = Store(listOf(credential("one", 0)))
        val repository = repository(store)
        respond(200, """{"access_token":"new-access","expires_in":3600}""")
        respond(200, generated); respond(200, generated)
        val first = async { repository.generate("gemini-flash", "Fix", "input", 0.7) }
        val second = async { repository.generate("gemini-flash", "Fix", "input", 0.7) }
        assertEquals("Fixed", first.await()); assertEquals("Fixed", second.await())
        val paths = (0 until 3).map { server.takeRequest().url.encodedPath }
        assertEquals(1, paths.count { it == "/token" })
        assertEquals("refresh-one", store.saved.single().refreshToken)
        assertEquals("new-access", store.saved.single().accessToken)
    }

    @Test fun invalidGrantMarksOnlyTheRevokedAccountAndUsesAnotherOwnedAccount() = runBlocking {
        val store = Store(listOf(credential("revoked", 0), credential("ready")))
        val repository = repository(store)
        respond(400, """{"error":"invalid_grant"}"""); respond(200, generated)
        assertEquals("Fixed", repository.generate("gemini-flash", "Fix", "input", 0.7))
        assertTrue(repository.accounts.value.first { it.id == "revoked" }.needsLogin)
        assertFalse(repository.accounts.value.first { it.id == "ready" }.needsLogin)
    }

    @Test fun rateLimitsPutAnAccountOnCooldownWithoutInvalidatingIt() = runBlocking {
        val repository = repository(Store(listOf(credential("limited"), credential("ready"))))
        respond(429, "{}"); respond(200, """{"models":{"gemini-flash":{}}}""")
        assertEquals(listOf("gemini-flash"), repository.models())
        val limited = repository.accounts.value.first { it.id == "limited" }
        assertTrue(limited.cooldownUntil > System.currentTimeMillis())
        assertFalse(limited.needsLogin)
    }

    @Test fun storageFailureDoesNotEraseOrPublishNewAccountState() = runBlocking {
        val store = Store(listOf(credential("one")))
        val repository = repository(store)
        repository.load()
        store.failWrite = true
        try { repository.remove("one"); fail("Write failure swallowed") }
        catch (error: GoogleException) { assertEquals(GoogleFailure.STORAGE, error.failure) }
        assertEquals(1, repository.accounts.value.size)
        assertEquals(1, store.saved.size)
    }

    @Test fun modelCooldownDoesNotBlockDifferentModelsAndSurvivesReload() = runBlocking {
        val store = Store(listOf(credential("one")))
        val first = repository(store)
        respond(429, "{}")
        try { first.generate("gemini-pro", "Fix", "input", 0.7); fail("Limit ignored") }
        catch (error: GoogleException) { assertEquals(GoogleFailure.RATE_LIMITED, error.failure) }
        val reloaded = repository(store)
        respond(200, generated)
        assertEquals("Fixed", reloaded.generate("gemini-flash", "Fix", "input", 0.7))
        try { reloaded.generate("gemini-pro", "Fix", "input", 0.7); fail("Cooldown lost on reload") }
        catch (error: GoogleException) { assertEquals(GoogleFailure.RATE_LIMITED, error.failure) }
        assertEquals(2, server.requestCount)
    }
}
