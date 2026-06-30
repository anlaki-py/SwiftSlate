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
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.shared.SwiftSlateViewModel

@Composable
fun CommandsScreen(vm: SwiftSlateViewModel) {
    val haptic = LocalHapticFeedback.current
    val state by vm.commandsState.collectAsState()

    val displayCommands = remember(state.commands) {
        val (builtIn, custom) = state.commands.partition { it.isBuiltIn }
        builtIn + custom
    }

    var trigger by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedType by rememberSaveable { mutableStateOf(CommandType.AI) }
    var editingTrigger by rememberSaveable { mutableStateOf<String?>(null) }
    var editingBuiltInKey by rememberSaveable { mutableStateOf<String?>(null) }
    var isFormExpanded by rememberSaveable { mutableStateOf(false) }

    var commandToDelete by remember { mutableStateOf<Command?>(null) }
    var builtInToReset by remember { mutableStateOf<String?>(null) }

    val prefix = vm.getTriggerPrefix()
    val errorPrefixMsg = stringResource(R.string.commands_error_prefix, prefix)
    val errorDuplicateMsg = stringResource(R.string.commands_error_duplicate)
    val errorConflictTemplate = stringResource(R.string.commands_error_conflict, "\u0000")
    val errorEmptyTrigger = stringResource(R.string.commands_error_empty_trigger)
    val collapseLabel = stringResource(R.string.commands_collapse)
    val expandLabel = stringResource(R.string.commands_expand)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    val filteredCommands = remember(displayCommands, searchQuery) {
        if (searchQuery.isBlank()) displayCommands
        else displayCommands.filter { it.trigger.contains(searchQuery, ignoreCase = true) }
    }

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
        targetValue = if (isFormExpanded) 0f else 180f,
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

            SlateCard(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
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
                        val isUndoCommand = cmd.builtInKey == "undo"
                        val isUndeletable = cmd.builtInKey != null && vm.isUndeletable(cmd.builtInKey)

                        CompactCommandItem(
                            cmd = cmd,
                            isExpanded = isExpanded,
                            isUndoCommand = isUndoCommand,
                            isUndeletable = isUndeletable,
                            collapseLabel = collapseLabel,
                            expandLabel = expandLabel,
                            onToggleExpand = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                expandedIds = if (isExpanded) expandedIds - cmd.trigger
                                else expandedIds + cmd.trigger
                            },
                            onEdit = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isEditingOverridden = editingBuiltInKey != null && vm.isBuiltInOverridden(editingBuiltInKey)

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
            isBuiltInOverridden = isEditingOverridden,
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
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val trimmedTrigger = trigger.trim()
                if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                    val result = CommandValidation.validate(
                        trimmedTrigger, prefix, state.commands, editingTrigger
                    )
                    when (result) {
                        is CommandValidationResult.Error -> {
                            errorMessage = when (result.messageKey) {
                                "prefix" -> errorPrefixMsg
                                "empty_trigger" -> errorEmptyTrigger
                                "duplicate" -> errorDuplicateMsg
                                "conflict" -> errorConflictTemplate.replace("\u0000", result.conflictTrigger ?: "")
                                else -> null
                            }
                            return@CommandFormCard
                        }
                        is CommandValidationResult.Valid -> { /* proceed */ }
                    }
                    val saveError = vm.saveCommand(
                        trigger = trigger,
                        prompt = prompt,
                        description = description,
                        type = selectedType,
                        editingTrigger = editingTrigger,
                        editingBuiltInKey = editingBuiltInKey
                    )
                    if (saveError == null) {
                        resetForm()
                    }
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
            vm.deleteCommand(cmdToDelete)
            expandedIds = expandedIds - cmdToDelete.trigger
            if (editingTrigger == cmdToDelete.trigger) resetForm()
            commandToDelete = null
        },
        onDismiss = { commandToDelete = null }
    )

    // Reset confirmation dialog
    ResetCommandDialog(
        builtInToReset = builtInToReset,
        onConfirm = { key ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.resetBuiltInCommand(key)
            if (editingBuiltInKey == key) resetForm()
            builtInToReset = null
        },
        onDismiss = { builtInToReset = null }
    )
}
