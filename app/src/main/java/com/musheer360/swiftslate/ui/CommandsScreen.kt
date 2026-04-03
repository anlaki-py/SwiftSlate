package com.musheer360.swiftslate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.model.Command
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SlateCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val commandManager = remember { CommandManager(context) }
    var commands by remember { mutableStateOf(commandManager.getCommands()) }
    val prefix = commandManager.getTriggerPrefix()

    // Add command dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Edit dialog state
    var editingCommand by remember { mutableStateOf<Command?>(null) }
    var editTrigger by remember { mutableStateOf("") }
    var editPrompt by remember { mutableStateOf("") }
    var editErrorMessage by remember { mutableStateOf<String?>(null) }

    // Delete confirmation dialog state
    var deletingCommand by remember { mutableStateOf<Command?>(null) }

    // Translate dialog state
    var showTranslateDialog by remember { mutableStateOf(false) }
    var translatePrompt by remember { mutableStateOf(commandManager.getTranslatePrompt()) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAddDialog = true
                    trigger = ""
                    prompt = ""
                    errorMessage = null
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Command",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
                .padding(padding)
        ) {
            ScreenTitle("Commands")

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Translate command card (dynamic)
                item {
                    SlateCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    translatePrompt = commandManager.getTranslatePrompt()
                                    showTranslateDialog = true
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${prefix}tr:<lang>",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Translate text to any language (e.g., ${prefix}tr:ar, ${prefix}tr:es)",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Dynamic",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }

                items(commands.filter { !it.isDynamic }) { cmd ->
                    SlateCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !cmd.isSystem) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editingCommand = cmd
                                    editTrigger = cmd.trigger
                                    editPrompt = cmd.prompt
                                    editErrorMessage = null
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cmd.trigger,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cmd.prompt,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (cmd.isSystem) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "System - cannot modify",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (cmd.isBuiltIn) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Built-in",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            if (!cmd.isSystem) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    deletingCommand = cmd
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Command",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Command Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Custom Command") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = trigger,
                            onValueChange = {
                                trigger = it
                                errorMessage = null
                            },
                            label = { Text("Trigger (e.g., ${prefix}code)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("Prompt (must ask for JUST modified text)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        errorMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmedTrigger = trigger.trim()
                            if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                                if (!trimmedTrigger.startsWith(prefix)) {
                                    errorMessage = "Trigger must start with '$prefix'"
                                    return@Button
                                }
                                if (commands.any { it.trigger == trimmedTrigger }) {
                                    errorMessage = "A command with this trigger already exists"
                                    return@Button
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val newCommand = Command(trimmedTrigger, prompt.trim(), false)
                                commandManager.addCustomCommand(newCommand)
                                commands = commandManager.getCommands()
                                showAddDialog = false
                            }
                        },
                        enabled = trigger.isNotBlank() && prompt.isNotBlank()
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit Dialog
        editingCommand?.let { cmd ->
            AlertDialog(
                onDismissRequest = { editingCommand = null },
                title = {
                    Text(
                        text = when {
                            cmd.isSystem -> "System Command"
                            cmd.isBuiltIn -> "Edit Built-in Command"
                            else -> "Edit Command"
                        }
                    )
                },
                text = {
                    Column {
                        if (!cmd.isSystem) {
                            OutlinedTextField(
                                value = editTrigger,
                                onValueChange = {
                                    editTrigger = it
                                    editErrorMessage = null
                                },
                                label = { Text("Trigger") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editPrompt,
                                onValueChange = { editPrompt = it },
                                label = { Text("Prompt") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 150.dp, max = 300.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        } else {
                            Text(
                                text = cmd.trigger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = cmd.prompt,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "System commands cannot be modified.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        editErrorMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Row {
                        if (cmd.isBuiltIn && !cmd.isSystem) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commandManager.resetBuiltInCommand(cmd.trigger)
                                    commands = commandManager.getCommands()
                                    editingCommand = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                        if (!cmd.isSystem) {
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val trimmedTrigger = editTrigger.trim()

                                    if (!trimmedTrigger.startsWith(prefix)) {
                                        editErrorMessage = "Trigger must start with '$prefix'"
                                        return@Button
                                    }
                                    if (trimmedTrigger != cmd.trigger && commands.any { it.trigger == trimmedTrigger }) {
                                        editErrorMessage = "A command with this trigger already exists"
                                        return@Button
                                    }

                                    if (cmd.isBuiltIn) {
                                        if (trimmedTrigger != cmd.trigger) {
                                            commandManager.updateBuiltInTrigger(cmd.trigger, trimmedTrigger)
                                        }
                                        commandManager.updateBuiltInCommand(cmd.trigger, editPrompt.trim())
                                    } else {
                                        commandManager.removeCustomCommand(cmd.trigger)
                                        commandManager.addCustomCommand(Command(trimmedTrigger, editPrompt.trim(), false))
                                    }
                                    commands = commandManager.getCommands()
                                    editingCommand = null
                                },
                                enabled = editPrompt.isNotBlank() && editTrigger.isNotBlank()
                            ) {
                                Text("Save")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingCommand = null }) {
                        Text(if (cmd.isSystem) "Close" else "Cancel")
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        deletingCommand?.let { cmd ->
            AlertDialog(
                onDismissRequest = { deletingCommand = null },
                title = { Text("Delete Command?") },
                text = {
                    Text("Are you sure you want to delete \"${cmd.trigger}\"?${if (cmd.isBuiltIn) " You can re-enable it later from settings." else ""}")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (cmd.isBuiltIn) {
                                commandManager.disableBuiltInCommand(cmd.trigger)
                            } else {
                                commandManager.removeCustomCommand(cmd.trigger)
                            }
                            commands = commandManager.getCommands()
                            deletingCommand = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingCommand = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Translate Dialog
        if (showTranslateDialog) {
            AlertDialog(
                onDismissRequest = { showTranslateDialog = false },
                title = { Text("Translate Command") },
                text = {
                    Column {
                        Text(
                            text = "Usage: ${prefix}tr:<lang>",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Examples: ${prefix}tr:ar, ${prefix}tr:es, ${prefix}tr:fr",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Prompt Template",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use {lang} as placeholder for language code",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = translatePrompt,
                            onValueChange = { translatePrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                commandManager.resetTranslatePrompt()
                                translatePrompt = commandManager.getTranslatePrompt()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                commandManager.setTranslatePrompt(translatePrompt)
                                showTranslateDialog = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTranslateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
