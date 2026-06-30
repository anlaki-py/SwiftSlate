package com.musheer360.swiftslate.ui.settingsscreen

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.data.remote.OpenAiClient
import com.musheer360.swiftslate.data.repository.KeyRepository
import com.musheer360.swiftslate.data.repository.ProviderRepository
import com.musheer360.swiftslate.domain.ModelFetcher
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderCard(
    providerRepository: ProviderRepository,
    keyRepository: KeyRepository,
    openAiClient: OpenAiClient
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var activeProvider by remember { mutableStateOf<Provider?>(null) }
    var providerExpanded by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelFetchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        providers = providerRepository.getProviders()
        activeProvider = providerRepository.getActiveProvider()
    }

    fun fetchModelsForProvider(provider: Provider) {
        scope.launch {
            val key = keyRepository.getNextKey(provider.id)
            if (key == null) {
                fetchedModels = emptyList()
                modelFetchError = null
                return@launch
            }
            isLoadingModels = true
            modelFetchError = null
            val result = ModelFetcher.fetchModels(key, provider.endpoint, openAiClient)
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
        ProviderDropdown(
            providers = providers,
            activeProvider = activeProvider,
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded },
            onProviderSelected = { provider ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    providerRepository.setActiveProvider(provider.id)
                    activeProvider = providerRepository.getActiveProvider()
                    providers = providerRepository.getProviders()
                }
                providerExpanded = false
                fetchModelsForProvider(provider)
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

            val modelLabel = activeProvider!!.selectedModel.ifBlank {
                stringResource(R.string.settings_select_model)
            }
            val hasModel = activeProvider!!.selectedModel.isNotBlank()
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    fetchModelsForProvider(activeProvider!!)
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
    }

    if (showAddDialog) {
        ProviderFormDialog(
            title = stringResource(R.string.settings_add_provider),
            onSave = { name, endpoint ->
                scope.launch {
                    providerRepository.addProvider(name, endpoint)
                    providers = providerRepository.getProviders()
                    activeProvider = providerRepository.getActiveProvider()
                }
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
                scope.launch {
                    providerRepository.updateProvider(activeProvider!!.id, name = name, endpoint = endpoint)
                    providers = providerRepository.getProviders()
                    activeProvider = providerRepository.getActiveProvider()
                }
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
                    scope.launch {
                        keyRepository.removeKeysForProvider(activeProvider!!.id)
                        providerRepository.removeProvider(activeProvider!!.id)
                        providers = providerRepository.getProviders()
                        activeProvider = providerRepository.getActiveProvider()
                    }
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
        var hasKeys by remember { mutableStateOf(true) }
        LaunchedEffect(activeProvider) {
            hasKeys = keyRepository.getKeys(activeProvider!!.id).isNotEmpty()
        }
        val noKeysMsg = if (!hasKeys) stringResource(R.string.settings_models_no_keys) else null

        ModelPickerSheet(
            models = fetchedModels,
            selectedModel = activeProvider!!.selectedModel,
            isLoading = isLoadingModels,
            errorMessage = noKeysMsg ?: modelFetchError,
            onModelSelected = { model ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    providerRepository.updateProvider(activeProvider!!.id, selectedModel = model)
                    activeProvider = providerRepository.getActiveProvider()
                }
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}
