package com.musheer360.swiftslate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateItemCard
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Main commands screen showing all built-in and custom commands.
 * Supports adding, editing, deleting, and resetting commands.
 *
 * @param commandManager The command manager instance for data operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen(commandManager: CommandManager) {
    val haptic = LocalHapticFeedback.current
    var commands by remember { mutableStateOf(commandManager.getCommands()) }
    var deletedCommands by remember { mutableStateOf(commandManager.getDeletedBuiltInCommands()) }
    val displayCommands = remember(commands) {
        commands.filter { it.isBuiltIn } + commands.filter { !it.isBuiltIn }
    }

    // Form state
    var trigger by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
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
    val errorEmptyTrigger = stringResource(R.string.commands_error_empty_trigger)
    val collapseLabel = stringResource(R.string.commands_collapse)
    val expandLabel = stringResource(R.string.commands_expand)

    /** Refreshes both active and deleted command lists from the manager. */
    fun refreshCommands() {
        commands = commandManager.getCommands()
        deletedCommands = commandManager.getDeletedBuiltInCommands()
    }

    /** Resets all form fields and collapses the form. */
    fun resetForm() {
        trigger = ""
        prompt = ""
        description = ""
        errorMessage = null
        editingTrigger = null
        editingBuiltInKey = null
        selectedType = CommandType.AI
        isFormExpanded = false
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isFormExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.commands_title))

        // Collapsible add/edit form card
        CommandFormCard(
            isFormExpanded = isFormExpanded,
            chevronRotation = chevronRotation,
            editingTrigger = editingTrigger,
            editingBuiltInKey = editingBuiltInKey,
            trigger = trigger,
            prompt = prompt,
            description = description,
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
            onDescriptionChange = { description = it },
            onTypeChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedType = it
            },
            onCancel = { resetForm() },
            onResetRequest = { builtInToReset = editingBuiltInKey },
            onSave = {
                val trimmedTrigger = trigger.trim()
                if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                    if (!trimmedTrigger.startsWith(prefix)) {
                        errorMessage = errorPrefixMsg
                        return@CommandFormCard
                    }
                    if (trimmedTrigger == prefix || trimmedTrigger.length <= prefix.length) {
                        errorMessage = errorEmptyTrigger
                        return@CommandFormCard
                    }
                    // Duplicate check — exclude the command currently being edited
                    if (commands.any { it.trigger == trimmedTrigger && it.trigger != editingTrigger }) {
                        errorMessage = errorDuplicateMsg
                        return@CommandFormCard
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    if (editingBuiltInKey != null) {
                        // Saving a built-in override — strip trailing colons for translate
                        val saveTrigger = if (editingBuiltInKey == "translate") {
                            trimmedTrigger.trimEnd(':')
                        } else trimmedTrigger
                        commandManager.overrideBuiltInCommand(
                            editingBuiltInKey!!, saveTrigger, prompt.trim(), description.trim()
                        )
                    } else {
                        // Custom command flow
                        if (editingTrigger != null) {
                            commandManager.removeCustomCommand(editingTrigger!!)
                        }
                        commandManager.addCustomCommand(
                            Command(trimmedTrigger, prompt.trim(), false, selectedType,
                                description = description.trim())
                        )
                    }
                    refreshCommands()
                    resetForm()
                }
            },
            onSaveEnabled = trigger.isNotBlank() && trigger.trim() != prefix && prompt.isNotBlank()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (displayCommands.isNotEmpty() || deletedCommands.isNotEmpty()) {
            SectionHeader(stringResource(R.string.commands_title))
            SlateCard {
                LazyColumn(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    // Active commands
                    itemsIndexed(displayCommands, key = { _, cmd -> cmd.trigger }) { _, cmd ->
                        CommandListItem(
                            cmd = cmd,
                            commandManager = commandManager,
                            onEdit = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // For translate, strip the :<lang> display suffix
                                trigger = if (cmd.builtInKey == "translate") {
                                    cmd.trigger.replace(":<lang>", "")
                                } else cmd.trigger
                                prompt = cmd.prompt
                                description = cmd.description
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
                    // Deleted built-in commands section
                    if (deletedCommands.isNotEmpty()) {
                        item(key = "deleted_header") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.commands_deleted_section).uppercase(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(
                            deletedCommands,
                            key = { _, cmd -> "deleted_${cmd.builtInKey}" }
                        ) { _, cmd ->
                            DeletedCommandItem(
                                cmd = cmd,
                                onRestore = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commandManager.resetBuiltInCommand(cmd.builtInKey!!)
                                    refreshCommands()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Delete confirmation dialog
    commandToDelete?.let { cmdToDelete ->
        AlertDialog(
            onDismissRequest = { commandToDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_command_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (cmdToDelete.builtInKey != null) {
                        commandManager.deleteBuiltInCommand(cmdToDelete.builtInKey)
                    } else {
                        commandManager.removeCustomCommand(cmdToDelete.trigger)
                    }
                    // Close form if we were editing this command
                    if (editingTrigger == cmdToDelete.trigger) resetForm()
                    refreshCommands()
                    commandToDelete = null
                }) {
                    Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { commandToDelete = null }) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }

    // Reset confirmation dialog
    builtInToReset?.let { key ->
        AlertDialog(
            onDismissRequest = { builtInToReset = null },
            title = { Text(stringResource(R.string.commands_reset_confirm_title)) },
            text = { Text(stringResource(R.string.commands_reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    commandManager.resetBuiltInCommand(key)
                    if (editingBuiltInKey == key) resetForm()
                    refreshCommands()
                    builtInToReset = null
                }) {
                    Text(stringResource(R.string.commands_reset_command))
                }
            },
            dismissButton = {
                TextButton(onClick = { builtInToReset = null }) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }
}

/**
 * Collapsible form card for adding or editing commands.
 * Shows type selector only for custom commands, translate hint for translate,
 * and a "Reset to Default" button for overridden built-in commands.
 */
@Composable
private fun CommandFormCard(
    isFormExpanded: Boolean,
    chevronRotation: Float,
    editingTrigger: String?,
    editingBuiltInKey: String?,
    trigger: String,
    prompt: String,
    description: String,
    selectedType: CommandType,
    errorMessage: String?,
    prefix: String,
    collapseLabel: String,
    expandLabel: String,
    commandManager: CommandManager,
    onToggleExpand: () -> Unit,
    onTriggerChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTypeChange: (CommandType) -> Unit,
    onCancel: () -> Unit,
    onResetRequest: () -> Unit,
    onSave: () -> Unit,
    onSaveEnabled: Boolean
) {
    // Title switches between "Add Custom Command" and "Edit Command"
    val formTitle = if (editingTrigger != null) {
        stringResource(R.string.commands_edit_title)
    } else {
        stringResource(R.string.commands_add_custom_title)
    }

    SlateCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = if (isFormExpanded) collapseLabel else expandLabel
                ) { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = isFormExpanded,
            enter = expandVertically(
                animationSpec = tween(250),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(200, delayMillis = 50)),
            exit = shrinkVertically(
                animationSpec = tween(200),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(150))
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))

                // Type selector — hidden when editing built-in commands (always AI)
                if (editingBuiltInKey == null) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedType == CommandType.AI,
                            onClick = { onTypeChange(CommandType.AI) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(stringResource(R.string.commands_type_ai))
                        }
                        SegmentedButton(
                            selected = selectedType == CommandType.TEXT_REPLACER,
                            onClick = { onTypeChange(CommandType.TEXT_REPLACER) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                inactiveBorderColor = MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(stringResource(R.string.commands_type_replacer))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                SlateTextField(
                    value = trigger,
                    onValueChange = onTriggerChange,
                    label = { Text(stringResource(R.string.commands_trigger_label, prefix)) },
                    singleLine = true
                )

                // Translate hint — explains that :<lang> is appended dynamically
                if (editingBuiltInKey == "translate") {
                    Text(
                        text = stringResource(R.string.commands_translate_hint, trigger.trim()),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = {
                        Text(
                            if (selectedType == CommandType.AI) stringResource(R.string.commands_prompt_label)
                            else stringResource(R.string.commands_replacement_label)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Description field — optional, shown below prompt
                Spacer(modifier = Modifier.height(8.dp))
                SlateTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text(stringResource(R.string.commands_description_label)) },
                    singleLine = true
                )

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Cancel button — visible when editing any command
                if (editingTrigger != null) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.commands_cancel))
                    }
                }

                // Reset to Default — visible when editing an overridden built-in
                if (editingBuiltInKey != null && commandManager.isBuiltInOverridden(editingBuiltInKey)) {
                    TextButton(
                        onClick = onResetRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.commands_reset_command),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = onSaveEnabled,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        if (editingTrigger != null) stringResource(R.string.commands_save_command)
                        else stringResource(R.string.commands_add_command)
                    )
                }
            }
        }
    }
}

/**
 * A single command item in the active commands list.
 * Shows trigger, prompt, type chips, and edit/delete action buttons.
 *
 * @param cmd The command to display.
 * @param commandManager Used to check if the command is undeletable.
 * @param onEdit Callback when the edit button is tapped.
 * @param onDelete Callback when the delete button is tapped.
 */
@Composable
private fun CommandListItem(
    cmd: Command,
    commandManager: CommandManager,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SlateItemCard {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cmd.trigger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (cmd.isBuiltIn) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (cmd.isOverridden) stringResource(R.string.commands_modified)
                               else stringResource(R.string.commands_built_in),
                        fontSize = 11.sp,
                        color = if (cmd.isOverridden) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cmd.type == CommandType.TEXT_REPLACER) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.commands_type_replacer),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cmd.description.ifBlank {
                    if (cmd.prompt.length > 80) cmd.prompt.take(77) + "…" else cmd.prompt
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Edit button — always shown on all commands
        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.commands_edit_command),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        // Delete button — hidden for undeletable built-ins (translate, undo)
        val showDelete = cmd.builtInKey == null ||
            !commandManager.isUndeletable(cmd.builtInKey)
        if (showDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.commands_delete_command),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * A deleted built-in command item shown in the "Deleted" section.
 * Displays with muted colors and a "Restore" button.
 *
 * @param cmd The deleted command to display.
 * @param onRestore Callback when the restore button is tapped.
 */
@Composable
private fun DeletedCommandItem(
    cmd: Command,
    onRestore: () -> Unit
) {
    SlateItemCard {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cmd.trigger,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cmd.description.ifBlank {
                    if (cmd.prompt.length > 80) cmd.prompt.take(77) + "…" else cmd.prompt
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        TextButton(onClick = onRestore) {
            Text(stringResource(R.string.commands_restore_command))
        }
    }
}