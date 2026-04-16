package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages encrypted API key storage scoped per provider.
 *
 * Keys are encrypted with AES-256-GCM using the Android Keystore and
 * stored in SharedPreferences under `"keys_array_{providerId}"`.
 * Includes round-robin key rotation with rate-limit and invalid-key tracking.
 *
 * @param context Application context for SharedPreferences and Keystore access.
 */
class KeyManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("secure_keys_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "typeslate_secure_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = "]"
        private const val KEY_PREFIX = "keys_array_"
        private const val CACHE_TTL_MS = 5_000L
    }

    /** Per-provider rate-limited key tracking (key → cooldown-end timestamp). */
    private val rateLimitedKeys = ConcurrentHashMap<String, Long>()

    /** Per-provider set of permanently invalid keys. */
    private val invalidKeys: MutableSet<String> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val roundRobinIndex = AtomicInteger(0)

    /** Per-provider key cache: providerId → (list, timestamp). */
    private val cache = ConcurrentHashMap<String, Pair<List<String>, Long>>()

    @Volatile
    var keystoreAvailable: Boolean = true
        private set

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                val generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
                )
                generator.init(spec)
                generator.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.e("KeyManager", "Keystore init failed", e)
            keystoreAvailable = false
        }
    }

    /**
     * Retrieves the AES secret key from the Android Keystore.
     *
     * @return The [SecretKey], or null if unavailable.
     */
    private fun getSecretKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            ks.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            android.util.Log.e("KeyManager", "Failed to get secret key", e)
            null
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plainText The string to encrypt.
     * @return IV and ciphertext encoded as Base64, separated by [IV_SEPARATOR].
     */
    private fun encrypt(plainText: String): String {
        val secretKey = getSecretKey()
            ?: throw IllegalStateException("Keystore unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) +
                IV_SEPARATOR +
                Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    /**
     * Decrypts a previously encrypted string.
     *
     * @param encryptedString The IV+ciphertext string produced by [encrypt].
     * @return The decrypted plaintext, or null on failure.
     */
    private fun decrypt(encryptedString: String): String? {
        if (!encryptedString.contains(IV_SEPARATOR)) return null
        val parts = encryptedString.split(IV_SEPARATOR)
        if (parts.size != 2) return null
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    /**
     * Returns all API keys for a given provider.
     *
     * @param providerId The UUID of the provider.
     * @return Decrypted list of key strings, or empty.
     */
    @Synchronized
    fun getKeys(providerId: String): List<String> {
        val now = System.currentTimeMillis()
        val cached = cache[providerId]
        if (cached != null && now - cached.second < CACHE_TTL_MS) return cached.first

        val prefKey = KEY_PREFIX + providerId
        val encryptedStr = prefs.getString(prefKey, null) ?: return emptyList()
        val jsonStr = decrypt(encryptedStr) ?: run {
            cache[providerId] = emptyList<String>() to now
            return emptyList()
        }
        val list = try { JSONArray(jsonStr).toStringList() } catch (_: Exception) { emptyList() }
        cache[providerId] = list to now
        return list
    }

    /**
     * Persists the key list for a provider.
     *
     * @param providerId The UUID of the provider.
     * @param keys The full key list to save.
     * @return True if encryption and save succeeded.
     */
    @Synchronized
    private fun saveKeys(providerId: String, keys: List<String>): Boolean {
        val arr = JSONArray(keys)
        return try {
            prefs.edit()
                .putString(KEY_PREFIX + providerId, encrypt(arr.toString()))
                .apply()
            cache[providerId] = keys to System.currentTimeMillis()
            true
        } catch (_: Exception) {
            cache.remove(providerId)
            false
        }
    }

    /**
     * Adds an API key for a provider if not already present.
     *
     * @param providerId The UUID of the provider.
     * @param key The API key to add.
     * @return True if saved successfully.
     */
    @Synchronized
    fun addKey(providerId: String, key: String): Boolean {
        val keys = getKeys(providerId).toMutableList()
        if (!keys.contains(key)) {
            keys.add(key)
            if (!saveKeys(providerId, keys)) return false
        }
        invalidKeys.remove(key)
        return true
    }

    /**
     * Removes an API key for a provider.
     *
     * @param providerId The UUID of the provider.
     * @param key The API key to remove.
     * @return True if saved successfully.
     */
    @Synchronized
    fun removeKey(providerId: String, key: String): Boolean {
        val keys = getKeys(providerId).toMutableList()
        keys.remove(key)
        val saved = saveKeys(providerId, keys)
        rateLimitedKeys.remove(key)
        invalidKeys.remove(key)
        return saved
    }

    /**
     * Removes all stored keys for a provider (called when provider is deleted).
     *
     * @param providerId The UUID of the provider to wipe keys for.
     */
    @Synchronized
    fun removeKeysForProvider(providerId: String) {
        prefs.edit().remove(KEY_PREFIX + providerId).apply()
        cache.remove(providerId)
    }

    /**
     * Returns the next usable key via round-robin, skipping invalid and rate-limited keys.
     *
     * @param providerId The UUID of the provider.
     * @return A valid key, or null if none available.
     */
    @Synchronized
    fun getNextKey(providerId: String): String? {
        val keys = getKeys(providerId)
        if (keys.isEmpty()) return null

        val now = System.currentTimeMillis()
        val validKeys = keys.filter { key ->
            if (invalidKeys.contains(key)) return@filter false
            val limitTime = rateLimitedKeys[key] ?: 0L
            now > limitTime
        }
        if (validKeys.isEmpty()) return null

        val idx = (roundRobinIndex.getAndIncrement() and Int.MAX_VALUE) % validKeys.size
        return validKeys[idx]
    }

    /**
     * Marks a key as rate-limited with a cooldown period.
     *
     * @param key The rate-limited API key.
     * @param retryAfterSeconds Cooldown duration (clamped to 1–600s).
     */
    fun reportRateLimit(key: String, retryAfterSeconds: Long = 60) {
        val cooldown = retryAfterSeconds.coerceIn(1, 600)
        rateLimitedKeys[key] = System.currentTimeMillis() + cooldown * 1_000
    }

    /**
     * Marks a key as permanently invalid (e.g. revoked or wrong).
     *
     * @param key The invalid API key.
     */
    fun markInvalid(key: String) {
        invalidKeys.add(key)
    }

    /**
     * Returns the shortest remaining wait time across all rate-limited keys for a provider.
     *
     * @param providerId The UUID of the provider.
     * @return Milliseconds until the next key becomes available, or null.
     */
    fun getShortestWaitTimeMs(providerId: String): Long? {
        val keys = getKeys(providerId)
        if (keys.isEmpty()) return null
        val now = System.currentTimeMillis()
        val waits = keys.filter { !invalidKeys.contains(it) }
            .mapNotNull { key ->
                val limitTime = rateLimitedKeys[key] ?: return@mapNotNull null
                val remaining = limitTime - now
                if (remaining > 0) remaining else null
            }
        return waits.minOrNull()
    }
}
