package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.domain.ModelFetcher
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField
import kotlinx.coroutines.launch

/**
 * Provider configuration card — provider selector with add/edit/delete,
 * model picker with dynamic fetching, and temperature slider.
 *
 * @param providerManager Manager for CRUD operations on user-defined providers.
 * @param keyManager Manager for per-provider API keys.
 * @param prefs SharedPreferences for temperature setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderCard(
    providerManager: ProviderManager,
    keyManager: KeyManager,
    prefs: android.content.SharedPreferences
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var providers by remember { mutableStateOf(providerManager.getProviders()) }
    var activeProvider by remember { mutableStateOf(providerManager.getActiveProvider()) }
    var providerExpanded by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    // Model fetching state
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelFetchError by remember { mutableStateOf<String?>(null) }

    var temperature by remember { mutableStateOf(prefs.getFloat("temperature", 0.7f)) }

    /** Fetches models for the active provider using its first available key. */
    fun fetchModelsForProvider(provider: Provider) {
        val key = keyManager.getNextKey(provider.id)
        if (key == null) {
            fetchedModels = emptyList()
            modelFetchError = null // No error — just no keys
            return
        }
        isLoadingModels = true
        modelFetchError = null
        scope.launch {
            val result = ModelFetcher.fetchModels(key, provider.endpoint)
            isLoadingModels = false
            if (result.isSuccess) {
                fetchedModels = result.getOrDefault(emptyList())
                modelFetchError = null
            } else {
                fetchedModels = emptyList()
                modelFetchError = result.exceptionOrNull()?.message
            }
        }
    }

    SlateCard {
        // Provider dropdown
        ProviderDropdown(
            providers = providers,
            activeProvider = activeProvider,
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded },
            onProviderSelected = { provider ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                providerManager.setActiveProvider(provider.id)
                activeProvider = provider
                providerExpanded = false
                fetchModelsForProvider(provider)
            },
            onAddClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                providerExpanded = false
                showAddDialog = true
            }
        )

        // Active provider actions
        if (activeProvider != null) {
            Spacer(modifier = Modifier.height(8.dp))

            // Endpoint display + edit/delete buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeProvider!!.endpoint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showEditDialog = true },
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

            // Model selector button
            val modelLabel = activeProvider!!.selectedModel.ifBlank {
                stringResource(R.string.settings_select_model)
            }
            SlateTextField(
                value = modelLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .noRippleClickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        fetchModelsForProvider(activeProvider!!)
                        showModelPicker = true
                    }
            )
        } else {
            // No provider hint
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_no_provider_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TemperatureSlider(temperature, haptic, prefs) { temperature = it }
    }

    // -- Dialogs --

    if (showAddDialog) {
        ProviderFormDialog(
            title = stringResource(R.string.settings_add_provider),
            onSave = { name, endpoint ->
                val provider = providerManager.addProvider(name, endpoint)
                providers = providerManager.getProviders()
                activeProvider = providerManager.getActiveProvider()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showEditDialog && activeProvider != null) {
        ProviderFormDialog(
            initialName = activeProvider!!.name,
            initialEndpoint = activeProvider!!.endpoint,
            title = stringResource(R.string.settings_edit_provider),
            onSave = { name, endpoint ->
                providerManager.updateProvider(activeProvider!!.id, name = name, endpoint = endpoint)
                providers = providerManager.getProviders()
                activeProvider = providerManager.getActiveProvider()
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
                    keyManager.removeKeysForProvider(activeProvider!!.id)
                    providerManager.removeProvider(activeProvider!!.id)
                    providers = providerManager.getProviders()
                    activeProvider = providerManager.getActiveProvider()
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
        val noKeysMsg = if (keyManager.getKeys(activeProvider!!.id).isEmpty()) {
            stringResource(R.string.settings_models_no_keys)
        } else null

        ModelPickerSheet(
            models = fetchedModels,
            selectedModel = activeProvider!!.selectedModel,
            isLoading = isLoadingModels,
            errorMessage = noKeysMsg ?: modelFetchError,
            onModelSelected = { model ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                providerManager.updateProvider(activeProvider!!.id, selectedModel = model)
                activeProvider = providerManager.getActiveProvider()
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}
