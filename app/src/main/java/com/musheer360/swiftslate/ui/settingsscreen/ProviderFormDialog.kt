package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.domain.EndpointValidation
import com.musheer360.swiftslate.domain.EndpointValidationResult
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Dialog for adding or editing a provider. Collects a name and endpoint URL,
 * validates the endpoint, and calls back on save.
 *
 * @param initialName Pre-filled name (empty for new providers).
 * @param initialEndpoint Pre-filled endpoint URL (empty for new providers).
 * @param title Dialog title string.
 * @param onSave Callback with (name, endpoint) when the user confirms.
 * @param onDismiss Callback to close the dialog.
 */
@Composable
internal fun ProviderFormDialog(
    initialName: String = "",
    initialEndpoint: String = "",
    title: String,
    onSave: (name: String, endpoint: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var endpoint by remember { mutableStateOf(initialEndpoint) }
    var endpointError by remember { mutableStateOf<String?>(null) }

    val endpointErrorScheme = stringResource(R.string.settings_endpoint_error_scheme)
    val endpointErrorSpaces = stringResource(R.string.settings_endpoint_error_spaces)

    val isValid = name.isNotBlank() && endpoint.isNotBlank() && endpointError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SlateTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_provider_name)) },
                    placeholder = { Text(stringResource(R.string.settings_provider_name_hint)) },
                    singleLine = true
                )

                SlateTextField(
                    value = endpoint,
                    onValueChange = {
                        endpoint = it
                        val result = EndpointValidation.validate(it)
                        endpointError = when (result) {
                            is EndpointValidationResult.Valid -> null
                            is EndpointValidationResult.Error -> when (result.messageKey) {
                                "spaces" -> endpointErrorSpaces
                                else -> endpointErrorScheme
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.settings_endpoint_title)) },
                    placeholder = { Text(stringResource(R.string.settings_endpoint_placeholder)) },
                    singleLine = true,
                    isError = endpointError != null
                )

                endpointError?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), endpoint.trim().trimEnd('/')) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.commands_save_command))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.commands_cancel))
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
