package com.musheer360.swiftslate.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateItemCard
import com.musheer360.swiftslate.ui.components.SlateTextField
import com.musheer360.swiftslate.ui.shared.SwiftSlateViewModel

@Composable
fun KeysScreen(vm: SwiftSlateViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by vm.keysState.collectAsState()

    var newKey by remember { mutableStateOf("") }
    var keyToDelete by remember { mutableStateOf<String?>(null) }

    val keystoreErrorMsg = stringResource(R.string.keys_keystore_error)

    if (state.providerId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ScreenTitle(stringResource(R.string.keys_title))
            SlateCard {
                Text(
                    text = stringResource(R.string.keys_no_provider),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        return
    }

    LaunchedEffect(Unit) { vm.refreshKeys() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.keys_title))

        Text(
            text = stringResource(R.string.keys_provider_label, state.providerName ?: ""),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

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

        SlateCard {
            SlateTextField(
                value = newKey,
                onValueChange = { if (it.length <= 256) newKey = it },
                label = { Text(stringResource(R.string.keys_api_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (newKey.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.setKeyTesting(true)
                        vm.addKey(newKey)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                },
                enabled = newKey.isNotBlank() && !state.isTesting && state.keystoreAvailable,
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (state.keys.isNotEmpty()) {
            SlateCard {
                LazyColumn(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.keys, key = { index, k -> "$index-${k.hashCode()}" }) { _, key ->
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
                                    keyToDelete = key
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

        Spacer(modifier = Modifier.height(24.dp))
    }

    keyToDelete?.let { keyValue ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_key_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.removeKey(keyValue)
                    keyToDelete = null
                }) {
                    Text(stringResource(R.string.delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text(stringResource(R.string.commands_cancel))
                }
            }
        )
    }
}
