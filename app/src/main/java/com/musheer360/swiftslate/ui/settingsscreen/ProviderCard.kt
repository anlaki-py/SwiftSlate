package com.musheer360.swiftslate.ui.settingsscreen

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.domain.EndpointValidation
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.model.AiProvider
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderCard(providerManager: ProviderManager, keyManager: KeyManager) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var providers by remember { mutableStateOf(providerManager.getProviders()) }
    var activeProvider by remember(providers) { mutableStateOf(providerManager.getActiveProvider()) }

    var providerExpanded by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }

    var temperature by remember { mutableStateOf(preferences.getFloat("temperature", 0.7f)) }

    // Ensure we always have an active provider
    if (activeProvider == null && providers.isNotEmpty()) {
        providerManager.setActiveProvider(providers.first().id)
        activeProvider = providers.first()
    }

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
                        value = activeProvider?.name ?: "",
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
                        providers.forEach { prov ->
                            DropdownMenuItem(
                                text = { Text(prov.name) },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    providerManager.setActiveProvider(prov.id)
                                    activeProvider = prov
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
                    editingProvider = activeProvider
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
        activeProvider?.let { prov ->
            ModelSelectionSection(
                provider = prov,
                providerManager = providerManager,
                keyManager = keyManager,
                haptic = haptic
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Temperature Slider
        TemperatureSlider(temperature, haptic, preferences) { temperature = it }
    }

    if (showEditDialog) {
        ProviderEditDialog(
            initialProvider = editingProvider,
            onDismiss = { showEditDialog = false },
            onSave = { prov ->
                providerManager.addOrUpdateProvider(prov)
                if (editingProvider == null) {
                    providerManager.setActiveProvider(prov.id)
                }
                providers = providerManager.getProviders()
                showEditDialog = false
            },
            onDelete = { prov ->
                if (providers.size > 1) { // Prevent deleting last provider
                    providerManager.deleteProvider(prov.id)
                    providers = providerManager.getProviders()
                }
                showEditDialog = false
            },
            canDelete = editingProvider != null && providers.size > 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(
    provider: AiProvider,
    providerManager: ProviderManager,
    keyManager: KeyManager,
    haptic: HapticFeedback
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember(provider.selectedModel) { mutableStateOf(provider.selectedModel) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val client = remember { OpenAICompatibleClient() }
    
    // Auto-save typed model if not expanded (manual entry)
    LaunchedEffect(searchQuery) {
        if (!expanded && searchQuery != provider.selectedModel) {
            delay(500) // debounce
            providerManager.updateProviderModel(provider.id, searchQuery)
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { 
            expanded = !expanded 
            if (expanded && models.isEmpty() && !isLoading) {
                // Fetch models
                val key = keyManager.getKeys(provider.id).firstOrNull()
                if (key == null) {
                    fetchError = "Please add an API key first in the Keys tab."
                } else {
                    isLoading = true
                    fetchError = null
                    scope.launch {
                        val result = client.fetchModels(key, provider.endpoint)
                        if (result.isSuccess) {
                            models = result.getOrThrow()
                        } else {
                            fetchError = result.exceptionOrNull()?.message ?: "Failed to fetch models"
                        }
                        isLoading = false
                    }
                }
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
                    providerManager.updateProviderModel(provider.id, searchQuery)
                }
            }
        ) {
            if (fetchError != null) {
                DropdownMenuItem(
                    text = { Text(fetchError!!, color = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false }
                )
            } else if (isLoading) {
                DropdownMenuItem(
                    text = { Text("Loading models...") },
                    onClick = { }
                )
            } else if (models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No models found") },
                    onClick = { expanded = false }
                )
            } else {
                val filtered = models.filter { it.contains(searchQuery, ignoreCase = true) }
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
                            providerManager.updateProviderModel(provider.id, model)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditDialog(
    initialProvider: AiProvider?,
    onDismiss: () -> Unit,
    onSave: (AiProvider) -> Unit,
    onDelete: (AiProvider) -> Unit,
    canDelete: Boolean
) {
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var endpoint by remember { mutableStateOf(initialProvider?.endpoint ?: "") }
    
    val isEndpointValid = EndpointValidation.isSafeToSave(endpoint)
    val isFormValid = name.isNotBlank() && endpoint.isNotBlank() && isEndpointValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProvider == null) "Add Provider" else "Edit Provider") },
        text = {
            Column {
                SlateTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Name (e.g. Local LM Studio)") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SlateTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    placeholder = { Text("Endpoint URL") },
                    isError = endpoint.isNotBlank() && !isEndpointValid
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val prov = initialProvider?.copy(
                        name = name.trim(),
                        endpoint = endpoint.trimEnd('/')
                    ) ?: AiProvider(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        endpoint = endpoint.trimEnd('/'),
                        selectedModel = ""
                    )
                    onSave(prov)
                },
                enabled = isFormValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canDelete && initialProvider != null) {
                    IconButton(onClick = { onDelete(initialProvider) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemperatureSlider(
    temperature: Float,
    haptic: HapticFeedback,
    prefs: android.content.SharedPreferences,
    onTemperatureChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Temperature",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = String.format("%.1f", temperature),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(modifier = Modifier.height(6.dp))

    val sliderState = rememberSliderState(
        value = temperature,
        valueRange = 0f..2f,
        steps = 19,
        onValueChangeFinished = {
            prefs.edit().putFloat("temperature", temperature).apply()
        }
    )
    val sliderInteraction = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outline
    )

    LaunchedEffect(sliderState.value) {
        val newVal = Math.round(sliderState.value * 10) / 10f
        if (newVal != temperature) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onTemperatureChange(newVal)
        }
    }

    Slider(
        state = sliderState,
        interactionSource = sliderInteraction,
        modifier = Modifier.fillMaxWidth().height(26.dp),
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = sliderInteraction,
                colors = sliderColors
            )
        },
        track = {
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = sliderColors
            )
        }
    )
}
