package com.musheer360.swiftslate.manager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyManagerTest {
    private lateinit var keyManager: KeyManager

    /** Fixed provider ID used across all tests. */
    private val testProviderId = "test-provider-id"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("secure_keys_prefs", 0).edit().clear().commit()
        keyManager = KeyManager(context)
    }

    /** Adds keys and skips the test if Robolectric's KeyStore broke encrypt/decrypt. */
    private fun addKeysAndVerify(vararg keys: String) {
        for (key in keys) keyManager.addKey(testProviderId, key)
        Assume.assumeTrue(
            "KeyStore encryption unusable in test env (stored ${keyManager.getKeys(testProviderId).size} of ${keys.size})",
            keyManager.getKeys(testProviderId).size == keys.size
        )
    }

    // --- addKey / removeKey / getKeys ---

    @Test
    fun getKeys_initiallyEmpty() {
        assertTrue(keyManager.getKeys(testProviderId).isEmpty())
    }

    @Test
    fun addKey_appearsInGetKeys() {
        keyManager.addKey(testProviderId, "key1")
        Assume.assumeTrue("KeyStore encryption unusable in test env", keyManager.getKeys(testProviderId).size == 1)
        assertEquals(listOf("key1"), keyManager.getKeys(testProviderId))
    }

    @Test
    fun addKey_duplicateNotAdded() {
        keyManager.addKey(testProviderId, "key1")
        keyManager.addKey(testProviderId, "key1")
        Assume.assumeTrue("KeyStore encryption unusable in test env", keyManager.getKeys(testProviderId).isNotEmpty())
        assertEquals(1, keyManager.getKeys(testProviderId).size)
    }

    @Test
    fun removeKey_removesIt() {
        keyManager.addKey(testProviderId, "key1")
        keyManager.removeKey(testProviderId, "key1")
        assertTrue(keyManager.getKeys(testProviderId).isEmpty())
    }

    @Test
    fun removeKey_nonExistent_doesNotCrash() {
        keyManager.removeKey(testProviderId, "nonexistent")
    }

    // --- getNextKey (round-robin) ---

    @Test
    fun getNextKey_noKeys_returnsNull() {
        assertNull(keyManager.getNextKey(testProviderId))
    }

    @Test
    fun getNextKey_oneKey_alwaysReturnsThatKey() {
        keyManager.addKey(testProviderId, "only")
        Assume.assumeTrue("KeyStore encryption unusable in test env", keyManager.getKeys(testProviderId).isNotEmpty())
        assertEquals("only", keyManager.getNextKey(testProviderId))
        assertEquals("only", keyManager.getNextKey(testProviderId))
        assertEquals("only", keyManager.getNextKey(testProviderId))
    }

    @Test
    fun getNextKey_twoKeys_alternates() {
        addKeysAndVerify("a", "b")
        val first = keyManager.getNextKey(testProviderId)
        val second = keyManager.getNextKey(testProviderId)
        assertNotEquals(first, second)
        assertTrue(setOf(first, second) == setOf("a", "b"))
    }

    @Test
    fun getNextKey_threeKeys_cyclesThroughAll() {
        addKeysAndVerify("a", "b", "c")
        val results = (1..6).map { keyManager.getNextKey(testProviderId) }
        assertTrue(results.containsAll(listOf("a", "b", "c")))
    }

    // --- reportRateLimit ---

    @Test
    fun reportRateLimit_keyIsSkipped() {
        addKeysAndVerify("a", "b")
        keyManager.reportRateLimit("a", 600)
        // All calls should return "b" since "a" is rate-limited
        assertEquals("b", keyManager.getNextKey(testProviderId))
        assertEquals("b", keyManager.getNextKey(testProviderId))
    }

    @Test
    fun reportRateLimit_afterCooldown_keyAvailableAgain() {
        addKeysAndVerify("a")
        keyManager.reportRateLimit("a", 1)
        assertNull(keyManager.getNextKey(testProviderId)) // rate-limited, only key
        Thread.sleep(1100)
        assertEquals("a", keyManager.getNextKey(testProviderId))
    }

    @Test
    fun reportRateLimit_clampedToMax600() {
        addKeysAndVerify("a", "b")
        keyManager.reportRateLimit("a", 9999)
        // "a" should be rate-limited but clamped to 600s, so still skipped now
        assertEquals("b", keyManager.getNextKey(testProviderId))
    }

    // --- markInvalid ---

    @Test
    fun markInvalid_keyIsSkipped() {
        addKeysAndVerify("a", "b")
        keyManager.markInvalid("a")
        assertEquals("b", keyManager.getNextKey(testProviderId))
        assertEquals("b", keyManager.getNextKey(testProviderId))
    }

    @Test
    fun markInvalid_reAddingKeyClearsInvalid() {
        addKeysAndVerify("a")
        keyManager.markInvalid("a")
        assertNull(keyManager.getNextKey(testProviderId))
        keyManager.addKey(testProviderId, "a") // re-add clears invalid
        assertEquals("a", keyManager.getNextKey(testProviderId))
    }

    // --- getShortestWaitTimeMs ---

    @Test
    fun getShortestWaitTimeMs_noKeys_returnsNull() {
        assertNull(keyManager.getShortestWaitTimeMs(testProviderId))
    }

    @Test
    fun getShortestWaitTimeMs_noRateLimitedKeys_returnsNull() {
        keyManager.addKey(testProviderId, "a")
        assertNull(keyManager.getShortestWaitTimeMs(testProviderId))
    }

    @Test
    fun getShortestWaitTimeMs_returnsShortestWait() {
        addKeysAndVerify("a", "b")
        keyManager.reportRateLimit("a", 10)
        keyManager.reportRateLimit("b", 60)
        val wait = keyManager.getShortestWaitTimeMs(testProviderId)
        assertNotNull(wait)
        // "a" has ~10s wait, "b" has ~60s wait, shortest should be around 10s
        assertTrue(wait!! in 1..10_000)
    }
}
