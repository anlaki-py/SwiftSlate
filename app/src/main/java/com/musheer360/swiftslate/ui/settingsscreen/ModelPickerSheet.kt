package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Bottom sheet dialog for selecting a model from a fetched list,
 * with search filtering and a manual-entry fallback.
 *
 * @param models The list of model IDs fetched from the provider.
 * @param selectedModel The currently selected model ID.
 * @param isLoading True while models are being fetched.
 * @param errorMessage Non-null if model fetching failed.
 * @param onModelSelected Callback when a model is picked.
 * @param onDismiss Callback to close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    models: List<String>,
    selectedModel: String,
    isLoading: Boolean,
    errorMessage: String?,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var manualModel by remember { mutableStateOf("") }

    val filteredModels = remember(models, searchQuery) {
        if (searchQuery.isBlank()) models
        else models.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_select_model),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Loading state
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.settings_models_loading),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            // Error state — show manual input
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ManualModelInput(manualModel, haptic, onModelSelected) { manualModel = it }
                return@Column
            }

            // Empty state — show manual input
            if (models.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_models_empty),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ManualModelInput(manualModel, haptic, onModelSelected) { manualModel = it }
                return@Column
            }

            // Search field
            SlateTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.settings_models_search)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Model list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredModels, key = { it }) { model ->
                    val isSelected = model == selectedModel
                    ModelListItem(model, isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onModelSelected(model)
                    }
                }
            }

            // Manual entry fallback at bottom
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_models_manual),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ManualModelInput(manualModel, haptic, onModelSelected) { manualModel = it }
        }
    }
}

/**
 * A single model entry in the model list.
 *
 * @param model The model ID string.
 * @param isSelected Whether this model is currently active.
 * @param onClick Callback when tapped.
 */
@Composable
private fun ModelListItem(
    model: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = model,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

/**
 * Manual model ID text input with a "Use" button.
 *
 * @param value Current text field value.
 * @param haptic Haptic feedback provider.
 * @param onModelSelected Callback when the user confirms a manual entry.
 * @param onValueChange Updates the text field state.
 */
@Composable
private fun ManualModelInput(
    value: String,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onModelSelected: (String) -> Unit,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SlateTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.settings_model_placeholder)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                if (value.isNotBlank()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onModelSelected(value.trim())
                }
            },
            enabled = value.isNotBlank(),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("OK")
        }
    }
}
