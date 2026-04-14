package com.musheer360.swiftslate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateItemCard
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Main commands screen with compact expandable items, search bar at top,
 * and an add/edit form at the bottom. Deleted commands (including built-in)
 * are hidden entirely. The undo command is visible but uneditable.
 *
 * @param commandManager The command manager instance for data operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            .graphicsLayer { } // Hardware layer for smooth NavHost slide animations
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.commands_title))

        // Search pill — shown when there are commands to search
        if (displayCommands.isNotEmpty()) {
            SearchBar(
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

        // Collapsible add/edit form — anchored at bottom
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
                    // Duplicate check — exclude the command being edited
                    if (commands.any { it.trigger == trimmedTrigger && it.trigger != editingTrigger }) {
                        errorMessage = errorDuplicateMsg
                        return@CommandFormCard
                    }
                    // Conflict check — catch prefix overlaps
                    val conflicting = commands.firstOrNull {
                        it.trigger != editingTrigger &&
                        (it.trigger.startsWith(trimmedTrigger) || trimmedTrigger.startsWith(it.trigger))
                    }
                    if (conflicting != null) {
                        errorMessage = errorConflictTemplate.replace("\u0000", conflicting.trigger)
                        return@CommandFormCard
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    if (editingBuiltInKey != null) {
                        // Built-in override — strip trailing colons for translate
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
                    expandedIds = expandedIds - cmdToDelete.trigger
                    // Close form if editing the deleted command
                    if (editingTrigger == cmdToDelete.trigger) resetForm()
                    refreshCommands()
                    commandToDelete = null
                }) {
                    Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.onSurface)
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
 * Search pill with query input, clear button, and expand/collapse-all toggle.
 *
 * @param searchQuery Current search text.
 * @param onQueryChange Callback when query text changes.
 * @param expandedIds Set of currently expanded command triggers.
 * @param filteredCommands The filtered command list for expand-all.
 * @param expandLabel Accessibility label for expanding.
 * @param collapseLabel Accessibility label for collapsing.
 * @param onToggleExpandAll Callback to toggle expand/collapse all.
 */
@Composable
private fun SearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    expandedIds: Set<String>,
    filteredCommands: List<Command>,
    expandLabel: String,
    collapseLabel: String,
    onToggleExpandAll: () -> Unit
) {
    val searchLabel = stringResource(R.string.commands_search_hint)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { contentDescription = searchLabel },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = searchLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // Clear button — visible when there is a search query
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.commands_search_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(interactionSource = null, indication = null) {
                            onQueryChange("")
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Expand/collapse all toggle
            Icon(
                imageVector = if (expandedIds.isEmpty()) Icons.Default.List
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expandedIds.isEmpty()) expandLabel else collapseLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(interactionSource = null, indication = null) {
                        onToggleExpandAll()
                    }
            )
        }
    }
}

/**
 * Compact command list item — shows only the trigger by default.
 * Tap to expand and see the prompt/description + edit/delete actions.
 * The undo command is always visible but never editable.
 *
 * @param cmd The command to display.
 * @param isExpanded Whether this command's details are visible.
 * @param isUndoCommand True if this is the system undo command.
 * @param commandManager Used to check if the command is undeletable.
 * @param collapseLabel Accessibility label for collapsing.
 * @param expandLabel Accessibility label for expanding.
 * @param onToggleExpand Callback to toggle expand/collapse.
 * @param onEdit Callback when the edit action is tapped.
 * @param onDelete Callback when the delete action is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactCommandItem(
    cmd: Command,
    isExpanded: Boolean,
    isUndoCommand: Boolean,
    commandManager: CommandManager,
    collapseLabel: String,
    expandLabel: String,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Determine if this command supports actions (undo never does)
    val isUndeletable = cmd.builtInKey != null &&
        commandManager.isUndeletable(cmd.builtInKey)
    val hasActions = !isUndoCommand

    if (hasActions) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                    SwipeToDismissBoxValue.EndToStart -> {
                        if (!isUndeletable) { onDelete(); false } else false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = if (direction == SwipeToDismissBoxValue.StartToEnd)
                        Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (direction == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.commands_edit_command),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (!isUndeletable) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.commands_delete_command),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = !isUndeletable
        ) {
            CommandItemContent(cmd, isExpanded, collapseLabel, expandLabel, onToggleExpand)
        }
    } else {
        // Undo command — no swipe, just the card
        CommandItemContent(cmd, isExpanded, collapseLabel, expandLabel, onToggleExpand)
    }
}

/**
 * The visual content of a command list item — trigger, badges, expandable description.
 * Extracted so both swipeable and non-swipeable items share the same layout.
 *
 * @param cmd The command to display.
 * @param isExpanded Whether the description is visible.
 * @param collapseLabel Accessibility label for collapsing.
 * @param expandLabel Accessibility label for expanding.
 * @param onToggleExpand Callback to toggle expand/collapse.
 */
@Composable
private fun CommandItemContent(
    cmd: Command,
    isExpanded: Boolean,
    collapseLabel: String,
    expandLabel: String,
    onToggleExpand: () -> Unit
) {
    SlateItemCard(
        modifier = Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = if (isExpanded) collapseLabel else expandLabel
        ) { onToggleExpand() }
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Header row — trigger + badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cmd.trigger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (cmd.isBuiltIn) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (cmd.isOverridden) stringResource(R.string.commands_modified)
                               else stringResource(R.string.commands_built_in),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    if (cmd.type == CommandType.TEXT_REPLACER) {
                        Text(
                            text = stringResource(R.string.commands_type_replacer),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded details — prompt/description only, actions handled by swipe
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(250),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(250),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(150))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cmd.description.ifBlank {
                            if (cmd.prompt.length > 80) cmd.prompt.take(77) + "\u2026" else cmd.prompt
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Collapsible form card for adding or editing commands.
 * Shows type selector only for custom commands, translate hint for translate,
 * and a \"Reset to Default\" button for overridden built-in commands.
 *
 * @param isFormExpanded Whether the form body is visible.
 * @param chevronRotation Current rotation angle of the chevron icon.
 * @param editingTrigger Trigger of the command being edited, or null.
 * @param editingBuiltInKey Built-in key of the command being edited, or null.
 * @param trigger Current trigger text in the form.
 * @param prompt Current prompt text in the form.
 * @param description Current description text in the form.
 * @param selectedType Currently selected command type.
 * @param errorMessage Error message to display, or null.
 * @param prefix The current trigger prefix character.
 * @param collapseLabel Accessibility label for collapsing.
 * @param expandLabel Accessibility label for expanding.
 * @param commandManager Used to check override state.
 * @param onToggleExpand Callback to toggle form visibility.
 * @param onTriggerChange Callback when trigger text changes.
 * @param onPromptChange Callback when prompt text changes.
 * @param onDescriptionChange Callback when description text changes.
 * @param onTypeChange Callback when command type changes.
 * @param onCancel Callback to cancel editing.
 * @param onResetRequest Callback to request a built-in reset.
 * @param onSave Callback to save the command.
 * @param onSaveEnabled Whether the save button should be enabled.
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
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = isFormExpanded,
            enter = expandVertically(
                animationSpec = tween(250),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(250),
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
                SlateTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = {
                        Text(
                            if (selectedType == CommandType.AI) stringResource(R.string.commands_prompt_label)
                            else stringResource(R.string.commands_replacement_label)
                        )
                    },
                    singleLine = false,
                    modifier = Modifier.height(100.dp)
                )

                // Description field — optional
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