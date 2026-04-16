package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.model.AiProvider
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderCard(viewModel: SettingsViewModel) {
    val haptic = LocalHapticFeedback.current
    val state by viewModel.uiState.collectAsState()

    var providerExpanded by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }

    SlateCard {
        // Provider Selection Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded }
                ) {
                    SlateTextField(
                        value = state.activeProvider?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        containerColor = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        state.providers.forEach { prov ->
                            DropdownMenuItem(
                                text = { Text(prov.name) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setActiveProvider(prov.id)
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Edit Button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    editingProvider = state.activeProvider
                    showEditDialog = true
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Provider",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Add Button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    editingProvider = null
                    showEditDialog = true
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Provider",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Model Dropdown
        state.activeProvider?.let { prov ->
            ModelSelectionSection(viewModel = viewModel, provider = prov, haptic = haptic)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Temperature Slider
        TemperatureSlider(state.temperature, haptic) { viewModel.updateTemperature(it) }
    }

    if (showEditDialog) {
        ProviderEditDialog(
            initialProvider = editingProvider,
            onDismiss = { showEditDialog = false },
            onSave = { prov ->
                viewModel.saveProvider(prov)
                if (editingProvider == null) {
                    viewModel.setActiveProvider(prov.id)
                }
                showEditDialog = false
            },
            onDelete = { prov ->
                if (state.providers.size > 1) { // Prevent deleting last provider
                    viewModel.deleteProvider(prov.id)
                }
                showEditDialog = false
            },
            canDelete = editingProvider != null && state.providers.size > 1
        )
    }
}
