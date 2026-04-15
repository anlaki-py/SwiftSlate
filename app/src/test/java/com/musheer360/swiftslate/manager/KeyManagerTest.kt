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
    private val testProvider = "test_provider"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("secure_keys_prefs", 0).edit().clear().commit()
        keyManager = KeyManager(context)
    }

    /** Adds keys and skips the test if Robolectric's KeyStore broke encrypt/decrypt. */
    private fun addKeysAndVerify(vararg keys: String) {
        for (key in keys) keyManager.addKey(testProvider, key)
        Assume.assumeTrue(
            "KeyStore encryption unusable in test env (stored ${keyManager.getKeys(testProvider).size} of ${keys.size})",
            keyManager.getKeys(testProvider).size == keys.size
        )
    }

    // --- addKey / removeKey / getKeys ---

    @Test
    fun getKeys_initiallyEmpty() {
        assertTrue(keyManager.getKeys(testProvider).isEmpty())
    }

    @Test
    fun addKey_appearsInGetKeys() {
        keyManager.addKey(testProvider, "key1")
        Assume.assumeTrue("KeyStore encryption unusable in test env", keyManager.getKeys(testProvider).size == 1)
        assertEquals(listOf("key1"), keyManager.getKeys(testProvider))
    }

    @Test
    fun addKey_duplicateNotAdded() {
        keyManager.addKey(testProvider, "key1")
        keyManager.addKey(testProvider, "key1")
        Assume.assumeTrue("KeyStore encryption unusable in test env", keyManager.getKeys(testProvider).isNotEmpty())
        assertEquals(1, keyManager.getKeys(testProvider).size)
    }

    @Test
    fun removeKey_removesIt() {
        keyManager.addKey(testProvider, "key1")
        keyManager.removeKey(testProvider, "key1")
        assertTrue(keyManager.getKeys(testProvider).isEmpty())
    }

    @Test
    fun removeKey_nonExistent_doesNotCrash() {
        keyManager.removeKey(testProvider, "nonexistent")
    }

    // --- getNextKey (round-robin) ---

    @Test
    fun getNextKey_noKeys_returnsNull() {
        assertNull(keyManager.getNextKey(testProvider))
    }

    @Test
    fun getNextKey_oneKey_alwaysReturnsThatKey() {
        keyManager.addKey(testProvider, "only")
        Assume.assumeTrue("KeyStore encryption unusable in test env", keyManager.getKeys(testProvider).isNotEmpty())
        assertEquals("only", keyManager.getNextKey(testProvider))
        assertEquals("only", keyManager.getNextKey(testProvider))
        assertEquals("only", keyManager.getNextKey(testProvider))
    }

    @Test
    fun getNextKey_twoKeys_alternates() {
        addKeysAndVerify("a", "b")
        val first = keyManager.getNextKey(testProvider)
        val second = keyManager.getNextKey(testProvider)
        assertNotEquals(first, second)
        assertTrue(setOf(first, second) == setOf("a", "b"))
    }

    @Test
    fun getNextKey_threeKeys_cyclesThroughAll() {
        addKeysAndVerify("a", "b", "c")
        val results = (1..6).map { keyManager.getNextKey(testProvider) }
        assertTrue(results.containsAll(listOf("a", "b", "c")))
    }

    // --- reportRateLimit ---

    @Test
    fun reportRateLimit_keyIsSkipped() {
        addKeysAndVerify("a", "b")
        keyManager.reportRateLimit("a", 600)
        // All calls should return "b" since "a" is rate-limited
        assertEquals("b", keyManager.getNextKey(testProvider))
        assertEquals("b", keyManager.getNextKey(testProvider))
    }

    @Test
    fun reportRateLimit_afterCooldown_keyAvailableAgain() {
        addKeysAndVerify("a")
        keyManager.reportRateLimit("a", 1)
        assertNull(keyManager.getNextKey(testProvider)) // rate-limited, only key
        Thread.sleep(1100)
        assertEquals("a", keyManager.getNextKey(testProvider))
    }

    @Test
    fun reportRateLimit_clampedToMax600() {
        addKeysAndVerify("a", "b")
        keyManager.reportRateLimit("a", 9999)
        // "a" should be rate-limited but clamped to 600s, so still skipped now
        assertEquals("b", keyManager.getNextKey(testProvider))
    }

    // --- markInvalid ---

    @Test
    fun markInvalid_keyIsSkipped() {
        addKeysAndVerify("a", "b")
        keyManager.markInvalid("a")
        assertEquals("b", keyManager.getNextKey(testProvider))
        assertEquals("b", keyManager.getNextKey(testProvider))
    }

    @Test
    fun markInvalid_reAddingKeyClearsInvalid() {
        addKeysAndVerify("a")
        keyManager.markInvalid("a")
        assertNull(keyManager.getNextKey(testProvider))
        keyManager.addKey(testProvider, "a") // re-add clears invalid
        assertEquals("a", keyManager.getNextKey(testProvider))
    }

    // --- getShortestWaitTimeMs ---

    @Test
    fun getShortestWaitTimeMs_noKeys_returnsNull() {
        assertNull(keyManager.getShortestWaitTimeMs(testProvider))
    }

    @Test
    fun getShortestWaitTimeMs_noRateLimitedKeys_returnsNull() {
        keyManager.addKey(testProvider, "a")
        assertNull(keyManager.getShortestWaitTimeMs(testProvider))
    }

    @Test
    fun getShortestWaitTimeMs_returnsShortestWait() {
        addKeysAndVerify("a", "b")
        keyManager.reportRateLimit("a", 10)
        keyManager.reportRateLimit("b", 60)
        val wait = keyManager.getShortestWaitTimeMs(testProvider)
        assertNotNull(wait)
        // "a" has ~10s wait, "b" has ~60s wait, shortest should be around 10s
        assertTrue(wait!! in 1..10_000)
    }
}
