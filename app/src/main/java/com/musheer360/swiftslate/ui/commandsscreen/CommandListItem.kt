package com.musheer360.swiftslate.ui.commandsscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.ui.components.SlateItemCard

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
internal fun CompactCommandItem(
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
    val isUndeletable = cmd.builtInKey != null &&
        commandManager.isUndeletable(cmd.builtInKey)
    val hasActions = !isUndoCommand

    // Long-press action dialog state
    var showActionDialog by remember { mutableStateOf(false) }

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
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
            CommandItemContent(
                cmd = cmd,
                isExpanded = isExpanded,
                collapseLabel = collapseLabel,
                expandLabel = expandLabel,
                onToggleExpand = onToggleExpand,
                onLongPress = { showActionDialog = true }
            )
        }
    } else {
        CommandItemContent(
            cmd = cmd,
            isExpanded = isExpanded,
            collapseLabel = collapseLabel,
            expandLabel = expandLabel,
            onToggleExpand = onToggleExpand,
            onLongPress = {}
        )
    }

    // Long-press action modal
    if (showActionDialog) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text(cmd.trigger, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = { showActionDialog = false; onEdit() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.commands_edit_command),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!isUndeletable) {
                        TextButton(
                            onClick = { showActionDialog = false; onDelete() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.commands_delete_command),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionDialog = false }) {
                    Text(stringResource(R.string.backup_import_cancel))
                }
            }
        )
    }
}

/**
 * The visual content of a command list item — trigger and expandable description.
 * Extracted so both swipeable and non-swipeable items share the same layout.
 *
 * @param cmd The command to display.
 * @param isExpanded Whether the description is visible.
 * @param collapseLabel Accessibility label for collapsing.
 * @param expandLabel Accessibility label for expanding.
 * @param onToggleExpand Callback to toggle expand/collapse.
 * @param onLongPress Callback for long-press gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommandItemContent(
    cmd: Command,
    isExpanded: Boolean,
    collapseLabel: String,
    expandLabel: String,
    onToggleExpand: () -> Unit,
    onLongPress: () -> Unit
) {
    SlateItemCard(
        modifier = Modifier.combinedClickable(
            interactionSource = null,
            indication = null,
            onClickLabel = if (isExpanded) collapseLabel else expandLabel,
            onClick = { onToggleExpand() },
            onLongClick = { onLongPress() }
        )
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Trigger name only — clean, no badges
            Text(
                text = cmd.trigger,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Expanded details — prompt/description only
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
                        text = if (cmd.prompt.length > 80) cmd.prompt.take(77) + "\u2026" else cmd.prompt,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
