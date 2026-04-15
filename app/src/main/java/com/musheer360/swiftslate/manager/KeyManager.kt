package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secure_keys_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "typeslate_secure_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = "]"
        private const val CACHE_TTL_MS = 5_000L

        private fun getPrefKeyArray(providerId: String) = "keys_array_$providerId"
    }

    private val rateLimitedKeys = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val invalidKeys: MutableSet<String> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val roundRobinIndices = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    
    private val cachedProviderKeys = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val cacheTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    var keystoreAvailable: Boolean = true
        private set

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.e("KeyManager", "Keystore init failed", e)
            keystoreAvailable = false
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            android.util.Log.e("KeyManager", "Failed to get secret key", e)
            null
        }
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getSecretKey()
            ?: throw IllegalStateException("Keystore unavailable")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherTextString = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        return "$ivString$IV_SEPARATOR$cipherTextString"
    }

    private fun decrypt(encryptedString: String): String? {
        if (!encryptedString.contains(IV_SEPARATOR)) {
            return null // corrupted or legacy plaintext — no longer supported
        }
        val parts = encryptedString.split(IV_SEPARATOR)
        if (parts.size != 2) return null
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainTextBytes = cipher.doFinal(cipherText)
            String(plainTextBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    @Synchronized
    fun getKeys(providerId: String): List<String> {
        val now = System.currentTimeMillis()
        val cached = cachedProviderKeys[providerId]
        val timestamp = cacheTimestamps[providerId] ?: 0L
        if (cached != null && now - timestamp < CACHE_TTL_MS) return cached
        
        val encryptedStr = prefs.getString(getPrefKeyArray(providerId), null) ?: return emptyList()
        val jsonStr = decrypt(encryptedStr) ?: run {
            cachedProviderKeys[providerId] = emptyList()
            cacheTimestamps[providerId] = System.currentTimeMillis()
            return emptyList()
        }
        
        val list = try { JSONArray(jsonStr).toStringList() } catch (_: Exception) { emptyList() }
        cachedProviderKeys[providerId] = list
        cacheTimestamps[providerId] = System.currentTimeMillis()
        return list
    }

    @Synchronized
    private fun saveKeys(providerId: String, keys: List<String>): Boolean {
        val arr = JSONArray(keys)
        return try {
            prefs.edit().putString(getPrefKeyArray(providerId), encrypt(arr.toString())).apply()
            cachedProviderKeys[providerId] = keys
            cacheTimestamps[providerId] = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            cachedProviderKeys.remove(providerId)
            cacheTimestamps.remove(providerId)
            false
        }
    }

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

    @Synchronized
    fun removeKey(providerId: String, key: String): Boolean {
        val keys = getKeys(providerId).toMutableList()
        keys.remove(key)
        val saved = saveKeys(providerId, keys)
        rateLimitedKeys.remove(key)
        invalidKeys.remove(key)
        return saved
    }

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
        
        val counter = roundRobinIndices.getOrPut(providerId) { AtomicInteger(0) }
        val idx = (counter.getAndIncrement() and Int.MAX_VALUE) % validKeys.size
        return validKeys[idx]
    }

    fun reportRateLimit(key: String, retryAfterSeconds: Long = 60) {
        val cooldown = retryAfterSeconds.coerceIn(1, 600)
        rateLimitedKeys[key] = System.currentTimeMillis() + cooldown * 1_000
    }

    fun markInvalid(key: String) {
        invalidKeys.add(key)
    }

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
