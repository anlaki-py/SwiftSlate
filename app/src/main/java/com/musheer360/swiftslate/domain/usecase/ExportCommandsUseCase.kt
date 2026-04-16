package com.musheer360.swiftslate.domain.usecase

import android.content.Context
import android.net.Uri
import com.musheer360.swiftslate.manager.CommandManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportCommandsUseCase(
    private val context: Context,
    private val commandManager: CommandManager
) {
    suspend operator fun invoke(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(commandManager.exportCommands().toByteArray())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
