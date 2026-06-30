package com.musheer360.swiftslate.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.musheer360.swiftslate.data.local.AppDatabase
import com.musheer360.swiftslate.data.local.KeyEntity
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : KeyRepository {

    private val keyDao = db.keyDao()

    private val rateLimitedKeys = ConcurrentHashMap<String, Long>()
    private val invalidKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val roundRobinIndex = AtomicInteger(0)

    @Volatile
    override var keystoreAvailable: Boolean = true
        private set

    private companion object {
        private const val KEY_ALIAS = "typeslate_secure_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = "]"
    }

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
            android.util.Log.e("KeyRepository", "Keystore init failed", e)
            keystoreAvailable = false
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            ks.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            android.util.Log.e("KeyRepository", "Failed to get secret key", e)
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
        return Base64.encodeToString(iv, Base64.NO_WRAP) +
                IV_SEPARATOR +
                Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

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

    override suspend fun getKeys(providerId: String): List<String> {
        val entities = keyDao.getByProvider(providerId)
        return entities.mapNotNull { decrypt(it.encryptedKey) }
    }

    override suspend fun addKey(providerId: String, key: String): Boolean {
        if (!keystoreAvailable) return false
        return try {
            val encrypted = encrypt(key)
            keyDao.insert(KeyEntity(providerId = providerId, encryptedKey = encrypted))
            invalidKeys.remove(key)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun removeKey(providerId: String, key: String): Boolean {
        return try {
            val encrypted = encrypt(key)
            keyDao.deleteByValue(providerId, encrypted)
            rateLimitedKeys.remove(key)
            invalidKeys.remove(key)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun removeKeysForProvider(providerId: String) {
        keyDao.deleteByProvider(providerId)
    }

    override suspend fun getNextKey(providerId: String): String? {
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

    override fun reportRateLimit(key: String, retryAfterSeconds: Long) {
        rateLimitedKeys[key] = System.currentTimeMillis() + retryAfterSeconds.coerceIn(1, 600) * 1000
    }

    override fun markInvalid(key: String) {
        invalidKeys.add(key)
    }

    override fun getShortestWaitTimeMs(providerId: String): Long? {
        return try {
            val keys = kotlinx.coroutines.runBlocking { getKeys(providerId) }
            val now = System.currentTimeMillis()
            keys.filter { !invalidKeys.contains(it) }
                .mapNotNull { key ->
                    val limitTime = rateLimitedKeys[key] ?: return@mapNotNull null
                    val remaining = limitTime - now
                    if (remaining > 0) remaining else null
                }
                .minOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
