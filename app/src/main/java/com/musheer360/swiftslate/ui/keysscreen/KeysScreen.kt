package com.musheer360.swiftslate.ui.keysscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.domain.KeyValidation
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateItemCard
import com.musheer360.swiftslate.ui.components.SlateTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(viewModel: KeysViewModel) {
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current

    val state by viewModel.uiState.collectAsState()

    val validAddedMsg = stringResource(R.string.keys_valid_added)
    val alreadyAddedMsg = stringResource(R.string.keys_already_added)
    val validationFailedMsg = stringResource(R.string.keys_validation_failed)
    val keystoreErrorMsg = stringResource(R.string.keys_keystore_error)

    var providerExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { } // Creates a hardware layer for smooth NavHost slide animations
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.keys_title))

        if (!state.keystoreAvailable) {
            SlateCard {
                Text(
                    text = keystoreErrorMsg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Provider Selector
        if (state.providers.isNotEmpty() && state.selectedProvider != null) {
            SectionHeader("Manage keys for Provider")
            SlateCard {
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded }
                ) {
                    SlateTextField(
                        value = state.selectedProvider!!.name,
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
                                    viewModel.selectProvider(prov)
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (state.selectedProvider != null) {
            SectionHeader(stringResource(R.string.keys_api_key_label))
            SlateCard {
                SlateTextField(
                    value = state.newKey,
                    onValueChange = { viewModel.setNewKey(it) },
                    label = { Text(stringResource(R.string.keys_api_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.addKey(
                            validAddedMsg, alreadyAddedMsg
                        )
                    },
                    enabled = state.newKey.isNotBlank() && !state.isTesting && state.keystoreAvailable,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(if (state.isTesting) stringResource(R.string.keys_testing) else stringResource(R.string.keys_add_key))
                }
                if (state.testResult != null) {
                    Text(
                        text = state.testResult!!,
                        color = if (state.testSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                // Help URL
                state.selectedProvider?.let { prov ->
                    val (apiKeyUrl, providerName) = KeyValidation.getApiKeyUrl(prov)
                    if (apiKeyUrl != null && providerName != null) {
                        Text(
                            text = stringResource(R.string.keys_get_api_key, providerName),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(interactionSource = null, indication = null) { uriHandler.openUri(apiKeyUrl) }
                                .heightIn(min = 48.dp)
                                .wrapContentHeight(Alignment.CenterVertically)
                                .padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (state.keys.isNotEmpty()) {
                SectionHeader(stringResource(R.string.dashboard_api_keys_title))
                SlateCard {
                    LazyColumn(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.keys, key = { index, k -> "$index-${k.hashCode()}" }) { index, key ->
                            SlateItemCard {
                                Text(
                                    text = "••••••••" + key.takeLast(4),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}
                                )
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setKeyToDelete(key)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.keys_delete_key),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        } else {
            // No providers case
            Text("Please add a Provider in Settings first.")
        }
    }

    state.keyToDelete?.let { keyValue ->
        AlertDialog(
            onDismissRequest = { viewModel.setKeyToDelete(null) },
            title = { Text(stringResource(R.string.delete_confirm_key_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.removeKey(keyValue, keystoreErrorMsg)
                }) {
                    Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setKeyToDelete(null) }) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }
}