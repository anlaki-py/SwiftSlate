package com.musheer360.swiftslate.ui.commandsscreen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.model.Command

@Composable
internal fun DeleteCommandDialog(
    commandToDelete: Command?,
    onConfirm: (Command) -> Unit,
    onDismiss: () -> Unit
) {
    commandToDelete?.let { cmd ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.delete_confirm_command_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onConfirm(cmd) }) {
                    Text(
                        stringResource(R.string.delete_confirm_button),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }
}

@Composable
internal fun ResetCommandDialog(
    builtInToReset: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    builtInToReset?.let { key ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.commands_reset_confirm_title)) },
            text = { Text(stringResource(R.string.commands_reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onConfirm(key) }) {
                    Text(stringResource(R.string.commands_reset_command))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }
}
