package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.model.AiProvider
import com.musheer360.swiftslate.ui.components.SlateTextField
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSection(
    viewModel: SettingsViewModel,
    provider: AiProvider,
    haptic: HapticFeedback
) {
    val state by viewModel.uiState.collectAsState()
    
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember(provider.selectedModel) { mutableStateOf(provider.selectedModel) }
    
    // Auto-save typed model if not expanded (manual entry)
    LaunchedEffect(searchQuery) {
        if (!expanded && searchQuery != provider.selectedModel) {
            delay(500) // debounce
            viewModel.updateProviderModel(provider.id, searchQuery)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { 
            expanded = !expanded 
            if (expanded && state.models.isEmpty() && !state.isFetchingModels) {
                viewModel.fetchModels()
            }
        }
    ) {
        SlateTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search or type model ID") },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        
        ExposedDropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            expanded = expanded,
            onDismissRequest = { 
                expanded = false
                if (searchQuery.isNotBlank() && searchQuery != provider.selectedModel) {
                    viewModel.updateProviderModel(provider.id, searchQuery)
                }
            }
        ) {
            if (state.fetchError != null) {
                DropdownMenuItem(
                    text = { Text(state.fetchError!!, color = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false }
                )
            } else if (state.isFetchingModels) {
                DropdownMenuItem(
                    text = { Text("Loading models...") },
                    onClick = { }
                )
            } else if (state.models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No models found") },
                    onClick = { expanded = false }
                )
            } else {
                val filtered = state.models.filter { it.contains(searchQuery, ignoreCase = true) }
                if (filtered.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Tip: You can manually type a model name if not listed.") },
                        onClick = { expanded = false }
                    )
                }
                filtered.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            searchQuery = model
                            viewModel.updateProviderModel(provider.id, model)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
