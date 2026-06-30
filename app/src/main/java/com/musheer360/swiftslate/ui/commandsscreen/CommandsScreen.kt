package com.musheer360.swiftslate.ui.commandsscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.SwiftSlateViewModel
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.model.CommandType
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader
import com.musheer360.swiftslate.ui.components.SlateCard

@Composable
fun CommandsScreen(viewModel: SwiftSlateViewModel) {
    val haptic = LocalHapticFeedback.current
    val commands by viewModel.commands.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }
    var editingCommand by remember { mutableStateOf<Command?>(null) }

    val builtInCommands = commands.filter { it.isBuiltIn }
    val customCommands = commands.filter { !it.isBuiltIn }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.commands_title))

        CommandSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showAddForm = true
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.commands_add_command))
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (commands.isEmpty()) {
            SlateCard {
                Text(
                    text = stringResource(R.string.commands_search_empty),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filteredBuiltIn = if (searchQuery.isBlank()) builtInCommands
                    else builtInCommands.filter { matchesSearch(it, searchQuery) }
                val filteredCustom = if (searchQuery.isBlank()) customCommands
                    else customCommands.filter { matchesSearch(it, searchQuery) }

                if (filteredBuiltIn.isNotEmpty()) {
                    item {
                        SectionHeader(text = stringResource(R.string.commands_built_in))
                    }
                    items(filteredBuiltIn, key = { it.trigger }) { cmd ->
                        CommandListItem(
                            command = cmd,
                            onEdit = { editingCommand = cmd },
                            onDelete = {
                                viewModel.viewModelScope.launch {
                                    viewModel.commandRepository.deleteBuiltInCommand(cmd.builtInKey!!)
                                }
                            },
                            onReset = {
                                viewModel.viewModelScope.launch {
                                    viewModel.commandRepository.resetBuiltInCommand(cmd.builtInKey!!)
                                }
                            }
                        )
                    }
                }

                if (filteredCustom.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionHeader(text = stringResource(R.string.commands_custom_title))
                    }
                    items(filteredCustom, key = { it.trigger }) { cmd ->
                        CommandListItem(
                            command = cmd,
                            onEdit = { editingCommand = cmd },
                            onDelete = {
                                viewModel.viewModelScope.launch {
                                    viewModel.commandRepository.removeCustomCommand(cmd.trigger)
                                }
                            },
                            onReset = null
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showAddForm || editingCommand != null) {
        CommandFormCard(
            command = editingCommand,
            prefix = viewModel.commandRepository.getTriggerPrefix(),
            existingCommands = commands,
            onSave = { command ->
                viewModel.viewModelScope.launch {
                    if (editingCommand != null) {
                        viewModel.commandRepository.removeCustomCommand(editingCommand!!.trigger)
                    }
                    viewModel.commandRepository.addCustomCommand(command)
                }
                showAddForm = false
                editingCommand = null
            },
            onDismiss = {
                showAddForm = false
                editingCommand = null
            }
        )
    }
}

private fun matchesSearch(command: Command, query: String): Boolean {
    val q = query.lowercase()
    return command.trigger.lowercase().contains(q) ||
        command.description.lowercase().contains(q) ||
        command.prompt.lowercase().contains(q)
}
