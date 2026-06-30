package com.musheer360.swiftslate.ui.settingsscreen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.data.BackupResult
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.shared.SwiftSlateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup/restore card — export and import app state as JSON files.
 * Includes file picker launchers, specific success/error feedback,
 * and an import confirmation dialog.
 */
@Composable
internal fun BackupCard(vm: SwiftSlateViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupSuccess by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val exportSuccessMsg = stringResource(R.string.backup_export_success)
    val exportErrorMsg = stringResource(R.string.backup_export_error)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                            val text = reader.readText()
                            if (text.length > 1_000_000) null else text
                        } ?: ""
                    }
                    if (json == null) {
                        backupMessage = stringResource(R.string.backup_import_error_too_large)
                        backupSuccess = false
                        return@launch
                    }
                    when (val result = vm.importCommands(json)) {
                        is BackupResult.Success -> {
                            backupMessage = stringResource(R.string.backup_import_success)
                            backupSuccess = true
                        }
                        is BackupResult.Error -> {
                            backupMessage = importErrorMessage(result.messageKey)
                            backupSuccess = false
                        }
                    }
                } catch (_: Exception) {
                    backupMessage = stringResource(R.string.backup_import_error)
                    backupSuccess = false
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(vm.exportCommands().toByteArray())
                        }
                    }
                    backupMessage = exportSuccessMsg
                    backupSuccess = true
                } catch (_: Exception) {
                    backupMessage = exportErrorMsg
                    backupSuccess = false
                }
            }
        }
    }

    SlateCard {
        Text(
            text = stringResource(R.string.backup_desc),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    backupMessage = null
                    exportLauncher.launch("swiftslate-backup.json")
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.backup_export))
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    backupMessage = null
                    showImportConfirm = true
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.backup_import))
            }
        }
        backupMessage?.let { msg ->
            Text(
                text = msg,
                color = if (backupSuccess) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.backup_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.backup_import_cancel))
                }
            }
        )
    }
}

@Composable
private fun importErrorMessage(messageKey: String): String {
    return when (messageKey) {
        "invalid_format" -> stringResource(R.string.backup_import_error_invalid_format)
        "version_unsupported" -> stringResource(R.string.backup_import_error_version)
        "checksum_mismatch" -> stringResource(R.string.backup_import_error_checksum)
        "too_many_commands" -> stringResource(R.string.backup_import_error_too_many)
        "invalid_trigger" -> stringResource(R.string.backup_import_error_trigger)
        "invalid_prompt" -> stringResource(R.string.backup_import_error_prompt)
        "invalid_type" -> stringResource(R.string.backup_import_error_type)
        "parse_error" -> stringResource(R.string.backup_import_error_parse)
        else -> stringResource(R.string.backup_import_error)
    }
}
