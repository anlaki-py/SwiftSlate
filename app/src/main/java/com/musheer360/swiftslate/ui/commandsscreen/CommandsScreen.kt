package com.musheer360.swiftslate.ui.commandsscreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.domain.CommandValidation
import com.musheer360.swiftslate.domain.CommandValidationResult
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SlateCard

/**
 * Main commands screen with compact expandable items, search bar at top,
 * and an add/edit form at the bottom. Deleted commands (including built-in)
 * are hidden entirely. The undo command is visible but uneditable.
 *
 * @param commandManager The command manager instance for data operations.
 */
@Composable
fun CommandsScreen(commandManager: CommandManager) {
    val haptic = LocalHapticFeedback.current
    var commands by remember { mutableStateOf(commandManager.getCommands()) }

    // Sort built-in first, then custom
    val displayCommands = remember(commands) {
        val (builtIn, custom) = commands.partition { it.isBuiltIn }
        builtIn + custom
    }

    // Form state
    var trigger by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf(CommandType.AI) }
    var editingTrigger by rememberSaveable { mutableStateOf<String?>(null) }
    var editingBuiltInKey by rememberSaveable { mutableStateOf<String?>(null) }
    var isFormExpanded by rememberSaveable { mutableStateOf(false) }

    // Dialog state
    var commandToDelete by remember { mutableStateOf<Command?>(null) }
    var builtInToReset by remember { mutableStateOf<String?>(null) }

    val prefix = commandManager.getTriggerPrefix()
    val errorPrefixMsg = stringResource(R.string.commands_error_prefix, prefix)
    val errorDuplicateMsg = stringResource(R.string.commands_error_duplicate)
    val errorConflictTemplate = stringResource(R.string.commands_error_conflict, "\u0000")
    val errorEmptyTrigger = stringResource(R.string.commands_error_empty_trigger)
    val collapseLabel = stringResource(R.string.commands_collapse)
    val expandLabel = stringResource(R.string.commands_expand)

    // Search & expand state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    /** Filters commands by search query against trigger text. */
    val filteredCommands = remember(displayCommands, searchQuery) {
        if (searchQuery.isBlank()) displayCommands
        else displayCommands.filter { it.trigger.contains(searchQuery, ignoreCase = true) }
    }

    /** Refreshes command list from the manager. */
    fun refreshCommands() {
        commands = commandManager.getCommands()
    }

    /** Resets all form fields and collapses the form. */
    fun resetForm() {
        trigger = ""
        prompt = ""
        errorMessage = null
        editingTrigger = null
        editingBuiltInKey = null
        selectedType = CommandType.AI
        isFormExpanded = false
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isFormExpanded) 0f else 180f,
        animationSpec = tween(250),
        label = "chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { } // Hardware layer for smooth NavHost slide animations
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.commands_title))

        // Search pill — shown when there are commands to search
        if (displayCommands.isNotEmpty()) {
            CommandSearchBar(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                expandedIds = expandedIds,
                filteredCommands = filteredCommands,
                expandLabel = expandLabel,
                collapseLabel = collapseLabel,
                onToggleExpandAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expandedIds = if (expandedIds.isEmpty()) {
                        filteredCommands.map { it.trigger }.toSet()
                    } else {
                        emptySet()
                    }
                }
            )

            // Commands list — takes all available vertical space
            SlateCard(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    // Empty search results message
                    if (filteredCommands.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Text(
                                text = stringResource(R.string.commands_search_empty),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(filteredCommands, key = { it.trigger }) { cmd ->
                        val isExpanded = cmd.trigger in expandedIds
                        // Undo command is a system utility — never editable
                        val isUndoCommand = cmd.builtInKey == "undo"

                        CompactCommandItem(
                            cmd = cmd,
                            isExpanded = isExpanded,
                            isUndoCommand = isUndoCommand,
                            commandManager = commandManager,
                            collapseLabel = collapseLabel,
                            expandLabel = expandLabel,
                            onToggleExpand = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                expandedIds = if (isExpanded) expandedIds - cmd.trigger
                                else expandedIds + cmd.trigger
                            },
                            onEdit = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Strip :<lang> display suffix for translate
                                trigger = if (cmd.builtInKey == "translate") {
                                    cmd.trigger.replace(":<lang>", "")
                                } else cmd.trigger
                                prompt = cmd.prompt
                                selectedType = cmd.type
                                editingTrigger = cmd.trigger
                                editingBuiltInKey = cmd.builtInKey
                                errorMessage = null
                                isFormExpanded = true
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                commandToDelete = cmd
                            }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Collapsible add/edit form — anchored at bottom
        CommandFormCard(
            isFormExpanded = isFormExpanded,
            chevronRotation = chevronRotation,
            editingTrigger = editingTrigger,
            editingBuiltInKey = editingBuiltInKey,
            trigger = trigger,
            prompt = prompt,
            selectedType = selectedType,
            errorMessage = errorMessage,
            prefix = prefix,
            collapseLabel = collapseLabel,
            expandLabel = expandLabel,
            commandManager = commandManager,
            onToggleExpand = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isFormExpanded = !isFormExpanded
            },
            onTriggerChange = { trigger = it; errorMessage = null },
            onPromptChange = { prompt = it; errorMessage = null },
            onTypeChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedType = it
            },
            onCancel = { resetForm() },
            onResetRequest = { builtInToReset = editingBuiltInKey },
            onSave = {
                val trimmedTrigger = trigger.trim()
                if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                    // Delegate validation to the domain layer
                    val result = CommandValidation.validate(
                        trimmedTrigger, prefix, commands, editingTrigger
                    )
                    when (result) {
                        is CommandValidationResult.Error -> {
                            errorMessage = when (result.messageKey) {
                                "prefix" -> errorPrefixMsg
                                "empty_trigger" -> errorEmptyTrigger
                                "duplicate" -> errorDuplicateMsg
                                "conflict" -> errorConflictTemplate.replace(
                                    "\u0000", result.conflictTrigger ?: ""
                                )
                                else -> null
                            }
                            return@CommandFormCard
                        }
                        is CommandValidationResult.Valid -> { /* proceed */ }
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    if (editingBuiltInKey != null) {
                        // Built-in override — strip trailing colons for translate
                        val saveTrigger = if (editingBuiltInKey == "translate") {
                            trimmedTrigger.trimEnd(':')
                        } else trimmedTrigger
                        commandManager.overrideBuiltInCommand(
                            editingBuiltInKey!!, saveTrigger, prompt.trim(), ""
                        )
                    } else {
                        // Custom command flow
                        if (editingTrigger != null) {
                            commandManager.removeCustomCommand(editingTrigger!!)
                        }
                        commandManager.addCustomCommand(
                            Command(trimmedTrigger, prompt.trim(), false, selectedType,
                                description = "")
                        )
                    }
                    refreshCommands()
                    resetForm()
                }
            },
            onSaveEnabled = trigger.isNotBlank() && trigger.trim() != prefix && prompt.isNotBlank()
        )
    }

    // Delete confirmation dialog
    DeleteCommandDialog(
        commandToDelete = commandToDelete,
        onConfirm = { cmdToDelete ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (cmdToDelete.builtInKey != null) {
                commandManager.deleteBuiltInCommand(cmdToDelete.builtInKey)
            } else {
                commandManager.removeCustomCommand(cmdToDelete.trigger)
            }
            expandedIds = expandedIds - cmdToDelete.trigger
            // Close form if editing the deleted command
            if (editingTrigger == cmdToDelete.trigger) resetForm()
            refreshCommands()
            commandToDelete = null
        },
        onDismiss = { commandToDelete = null }
    )

    // Reset confirmation dialog
    ResetCommandDialog(
        builtInToReset = builtInToReset,
        onConfirm = { key ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            commandManager.resetBuiltInCommand(key)
            if (editingBuiltInKey == key) resetForm()
            refreshCommands()
            builtInToReset = null
        },
        onDismiss = { builtInToReset = null }
    )
}

/**
 * Confirmation dialog shown before deleting a command.
 *
 * @param commandToDelete The command pending deletion, or null to hide.
 * @param onConfirm Callback with the confirmed command to delete.
 * @param onDismiss Callback to cancel and hide the dialog.
 */
@Composable
private fun DeleteCommandDialog(
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

/**
 * Confirmation dialog shown before resetting a built-in command to defaults.
 *
 * @param builtInToReset The built-in key pending reset, or null to hide.
 * @param onConfirm Callback with the confirmed key to reset.
 * @param onDismiss Callback to cancel and hide the dialog.
 */
@Composable
private fun ResetCommandDialog(
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
