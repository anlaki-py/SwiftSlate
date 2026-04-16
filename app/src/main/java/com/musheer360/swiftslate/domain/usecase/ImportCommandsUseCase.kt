package com.musheer360.swiftslate.domain.usecase

import android.content.Context
import android.net.Uri
import com.musheer360.swiftslate.manager.CommandManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportCommandsUseCase(
    private val context: Context,
    private val commandManager: CommandManager
) {
    suspend operator fun invoke(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val text = reader.readText()
                if (text.length > 1_000_000) null else text
            } ?: ""
            if (commandManager.importCommands(json)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to parse commands backup."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
