package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.domain.EndpointValidation
import com.musheer360.swiftslate.ui.components.SlateTextField

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
    var endpointError by remember { mutableStateOf(false) }

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
                    onValueChange = { endpoint = it; endpointError = false },
                    label = { Text(stringResource(R.string.settings_endpoint_title)) },
                    placeholder = { Text(stringResource(R.string.settings_endpoint_placeholder)) },
                    isError = endpointError,
                    singleLine = true
                )
                if (endpointError) {
                    Text(
                        text = stringResource(R.string.settings_endpoint_error_scheme),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validation = EndpointValidation.validate(endpoint.trim())
                if (validation is EndpointValidationResult.Error) {
                    endpointError = true
                } else {
                    onSave(name.trim(), endpoint.trim())
                }
            }) {
                Text(stringResource(R.string.commands_save_command))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.commands_cancel))
            }
        }
    )
}
