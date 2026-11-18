package com.musheer360.swiftslate.ui.settingsscreen

import com.musheer360.swiftslate.model.ProviderProtocol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.shared.SwiftSlateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderCard(vm: SwiftSlateViewModel) {
    val haptic = LocalHapticFeedback.current
    val state by vm.settingsState.collectAsState()

    val modelState by vm.modelState.collectAsState()
    val providers = state.providers.map { Provider(it.id, it.name, it.endpoint, it.selectedModel, it.protocol) }
    val activeProvider = providers.firstOrNull { it.id == state.activeProviderId }
    var providerExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    SlateCard {
        ProviderDropdown(
            providers = providers,
            activeProvider = activeProvider,
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded },
            onProviderSelected = { provider ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.setActiveProvider(provider.id)
                providerExpanded = false
                vm.fetchProviderModels(provider.id)
            },
            onAddClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                providerExpanded = false
                showAddDialog = true
            }
        )

        if (activeProvider != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeProvider.protocol == ProviderProtocol.GOOGLE_ACCOUNT)
                        stringResource(R.string.google_provider_name) else activeProvider.endpoint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showEditDialog = true },
                    enabled = activeProvider.protocol != ProviderProtocol.GOOGLE_ACCOUNT,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_edit_provider), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_delete_provider), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val modelLabel = activeProvider.selectedModel.ifBlank {
                stringResource(R.string.settings_select_model)
            }
            val hasModel = activeProvider.selectedModel.isNotBlank()
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.fetchProviderModels(activeProvider.id)
                    showModelPicker = true
                },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = modelLabel,
                    fontSize = 15.sp,
                    color = if (hasModel) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_no_provider_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TemperatureSlider(
            temperature = state.temperature,
            haptic = haptic,
            onTemperatureChange = { vm.updateTemperature(it) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        TimeoutSlider(
            timeout = state.timeout,
            haptic = haptic,
            onTimeoutChange = { vm.updateTimeout(it) }
        )
    }

    if (showAddDialog) {
        ProviderFormDialog(
            title = stringResource(R.string.settings_add_provider),
            onSave = { name, endpoint, protocol ->
                vm.addProvider(name, endpoint, protocol)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showEditDialog && activeProvider != null) {
        ProviderFormDialog(
            initialName = activeProvider.name,
            initialEndpoint = activeProvider.endpoint,
            title = stringResource(R.string.settings_edit_provider),
            initialProtocol = activeProvider.protocol,
            isEditing = true,
            onSave = { name, endpoint, _ ->
                vm.updateProvider(activeProvider.id, name = name, endpoint = endpoint)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showDeleteConfirm && activeProvider != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_confirm_provider_title)) },
            text = { Text(stringResource(R.string.delete_confirm_provider_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.removeProvider(activeProvider.id)
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }

    if (showModelPicker && activeProvider != null) {

        ModelPickerSheet(
            models = if (modelState.providerId == activeProvider.id) modelState.models else emptyList(),
            selectedModel = activeProvider.selectedModel,
            isLoading = modelState.isLoading,
            errorMessage = modelState.error,
            onModelSelected = { model ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val currentProvider = activeProvider
                vm.updateProvider(currentProvider.id, selectedModel = model)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}
