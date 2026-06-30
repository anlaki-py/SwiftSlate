package com.musheer360.swiftslate.ui.commandsscreen

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
import com.musheer360.swiftslate.domain.CommandValidation
import com.musheer360.swiftslate.domain.CommandValidationResult
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.SlateTextField

@Composable
fun CommandFormCard(
    command: Command?,
    prefix: String,
    existingCommands: List<Command>,
    onSave: (Command) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var trigger by remember { mutableStateOf(command?.trigger ?: prefix) }
    var prompt by remember { mutableStateOf(command?.prompt ?: "") }
    var description by remember { mutableStateOf(command?.description ?: "") }
    var type by remember { mutableStateOf(command?.type ?: CommandType.AI) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isEditing = command != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) stringResource(R.string.commands_edit_title)
                else stringResource(R.string.commands_add_custom_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SlateTextField(
                    value = trigger,
                    onValueChange = { trigger = it; errorMessage = null },
                    label = { Text(stringResource(R.string.commands_trigger_label, prefix)) },
                    singleLine = true
                )

                SlateTextField(
                    value = prompt,
                    onValueChange = { prompt = it; errorMessage = null },
                    label = {
                        Text(
                            if (type == CommandType.TEXT_REPLACER)
                                stringResource(R.string.commands_replacement_label)
                            else stringResource(R.string.commands_prompt_label)
                        )
                    },
                    singleLine = type == CommandType.TEXT_REPLACER,
                    modifier = Modifier.heightIn(
                        min = if (type == CommandType.TEXT_REPLACER) 48.dp else 100.dp
                    )
                )

                SlateTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.commands_description_label)) },
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == CommandType.AI,
                        onClick = { type = CommandType.AI },
                        label = { Text(stringResource(R.string.commands_type_ai)) }
                    )
                    FilterChip(
                        selected = type == CommandType.TEXT_REPLACER,
                        onClick = { type = CommandType.TEXT_REPLACER },
                        label = { Text(stringResource(R.string.commands_type_replacer)) }
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val trimmedTrigger = trigger.trim()
                val validation = CommandValidation.validate(
                    trimmedTrigger = trimmedTrigger,
                    prefix = prefix,
                    existingCommands = existingCommands,
                    editingTrigger = command?.trigger
                )
                when (validation) {
                    is CommandValidationResult.Error -> {
                        errorMessage = when (validation.messageKey) {
                            "prefix" -> context.getString(R.string.commands_error_prefix, prefix)
                            "empty_trigger" -> context.getString(R.string.commands_error_empty_trigger)
                            "duplicate" -> context.getString(R.string.commands_error_duplicate)
                            "conflict" -> context.getString(R.string.commands_error_conflict, validation.conflictTrigger ?: "")
                            else -> validation.messageKey
                        }
                    }
                    is CommandValidationResult.Valid -> {
                        val newCommand = Command(
                            trigger = trimmedTrigger,
                            prompt = prompt.trim(),
                            description = description.trim(),
                            type = type,
                            isBuiltIn = false
                        )
                        onSave(newCommand)
                    }
                }
            }) {
                Text(stringResource(R.string.commands_save_command))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.commands_cancel))
            }
        }
    )
}
