package com.musheer360.swiftslate.ui.commandsscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Collapsible form card for adding or editing commands.
 * Shows type selector only for custom commands, translate hint for translate,
 * and a "Reset to Default" button for overridden built-in commands.
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
internal fun CommandFormCard(
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
                    CommandTypeSelector(
                        selectedType = selectedType,
                        onTypeChange = onTypeChange
                    )
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

/**
 * Segmented button row for choosing between AI and Text Replacer command types.
 *
 * @param selectedType The currently active command type.
 * @param onTypeChange Callback invoked when the user taps a different type.
 */
@Composable
private fun CommandTypeSelector(
    selectedType: CommandType,
    onTypeChange: (CommandType) -> Unit
) {
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
}
