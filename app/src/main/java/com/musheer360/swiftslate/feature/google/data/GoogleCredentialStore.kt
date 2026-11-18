package com.musheer360.swiftslate.feature.google.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.feature.google.domain.GoogleException
import com.musheer360.swiftslate.feature.google.domain.GoogleFailure
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal data class GoogleCredential(
    val account: GoogleAccount,
    val projectId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val cooldowns: Map<String, Long> = emptyMap()
) {
    override fun toString(): String = "GoogleCredential(redacted)"
}

internal interface GoogleAccountStore {
    fun read(): List<GoogleCredential>
    fun write(accounts: List<GoogleCredential>)
}

/** AES-GCM credentials live in noBackupFilesDir, with atomic replacement and no plaintext fallback. */
internal class GoogleCredentialStore(context: Context) : GoogleAccountStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "google-accounts.enc"))

    override fun read(): List<GoogleCredential> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        try {
            val bytes = file.openRead().use { stream ->
                require(stream.channel.size() in 29..MAX_BYTES.toLong())
                stream.readBytes()
            }
            require(bytes.size in 29..MAX_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val array = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val cooldownJson = item.optJSONObject("cooldowns")
                val cooldowns = cooldownJson?.keys()?.asSequence()?.associateWith { cooldownJson?.optLong(it, 0) ?: 0L }.orEmpty()
                GoogleCredential(
                    GoogleAccount(item.getString("id"), item.getString("email"), item.getString("name"),
                        item.optBoolean("enabled", true), item.optBoolean("needsLogin", false),
                        cooldownUntil = cooldowns.values.maxOrNull() ?: 0),
                    item.getString("project"), item.getString("access"), item.getString("refresh"), item.getLong("expires"), cooldowns
                )
            }
        } catch (error: Exception) {
            android.util.Log.e("GoogleCredentials", "Encrypted account read failed: ${error.javaClass.simpleName}")
            // Keep unreadable storage intact so a temporary keystore failure cannot erase accounts.
            throw GoogleException(GoogleFailure.STORAGE)
        }
    }

    override fun write(accounts: List<GoogleCredential>) {
        try {
            val array = JSONArray()
            accounts.forEach { credential -> array.put(JSONObject().apply {
                put("id", credential.account.id)
                put("email", credential.account.email)
                put("name", credential.account.name)
                put("enabled", credential.account.isEnabled)
                put("needsLogin", credential.account.needsLogin)
                put("project", credential.projectId)
                put("access", credential.accessToken)
                put("refresh", credential.refreshToken)
                put("expires", credential.expiresAt)
                put("cooldowns", JSONObject(credential.cooldowns))
            }) }
            val plain = array.toString().toByteArray(Charsets.UTF_8)
            require(plain.size < MAX_BYTES - 32)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.iv + cipher.doFinal(plain)
            val stream = file.startWrite()
            try {
                stream.write(encrypted)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        } catch (error: Exception) {
            android.util.Log.e("GoogleCredentials", "Encrypted account write failed: ${error.javaClass.simpleName}")
            throw GoogleException(GoogleFailure.STORAGE)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "swiftslate_google_accounts"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAX_BYTES = 1_048_576
    }
}
