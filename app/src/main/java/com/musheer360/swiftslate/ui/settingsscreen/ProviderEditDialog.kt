package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.domain.EndpointValidation
import com.musheer360.swiftslate.model.AiProvider
import com.musheer360.swiftslate.ui.components.SlateTextField
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditDialog(
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
