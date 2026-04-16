package com.musheer360.swiftslate.domain.usecase

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.domain.KeyValidation
import com.musheer360.swiftslate.domain.KeyValidationResult
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AddKeyStatus {
    data class Success(val newKeysList: List<String>) : AddKeyStatus()
    data class Error(val message: String) : AddKeyStatus()
    object Duplicate : AddKeyStatus()
}

class ManageKeyUseCase(
    private val context: Context,
    private val keyManager: KeyManager,
    private val openAIClient: OpenAICompatibleClient
) {
    suspend fun addKey(provider: AiProvider, key: String): AddKeyStatus = withContext(Dispatchers.IO) {
        val existingKeys = keyManager.getKeys(provider.id)
        val result = KeyValidation.validate(
            key = key,
            provider = provider,
            existingKeys = existingKeys,
            client = openAIClient,
            fallbackErrorMessage = "Validation failed"
        )
        
        when (result) {
            is KeyValidationResult.Duplicate -> AddKeyStatus.Duplicate
            is KeyValidationResult.Invalid -> AddKeyStatus.Error(result.message)
            is KeyValidationResult.Valid -> {
                if (!keyManager.addKey(provider.id, key)) {
                    AddKeyStatus.Error("Failed to save to Keystore")
                } else {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    AddKeyStatus.Success(keyManager.getKeys(provider.id))
                }
            }
        }
    }

    suspend fun removeKey(providerId: String, key: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (keyManager.removeKey(providerId, key)) {
            Result.success(keyManager.getKeys(providerId))
        } else {
            Result.failure(Exception("Failed to remove key from Keystore"))
        }
    }
}
