package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.ui.components.SlateTextField
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderDropdown(
    providers: List<Provider>,
    activeProvider: Provider?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (Provider) -> Unit,
    onAddClick: () -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = activeProvider?.name ?: stringResource(R.string.settings_no_provider),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.name) },
                    onClick = { onProviderSelected(provider) },
                    trailingIcon = if (provider.id == activeProvider?.id) {
                        { Text("✓", fontWeight = FontWeight.Bold) }
                    } else null
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_add_provider), color = MaterialTheme.colorScheme.primary) },
                onClick = onAddClick
            )
        }
    }
}

@Composable
internal fun TemperatureSlider(
    temperature: Float,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onTemperatureChange: (Float) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_temperature_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.settings_temperature_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = temperature,
            onValueChange = {
                onTemperatureChange((it * 10).roundToInt() / 10f)
            },
            valueRange = 0f..2f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "%.1f".format(temperature),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun TimeoutSlider(
    timeout: Float,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onTimeoutChange: (Float) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_timeout_desc),
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = timeout,
            onValueChange = {
                onTimeoutChange((it * 2).roundToInt() / 2f)
            },
            valueRange = 5f..60f,
            steps = 109,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "%.0fs".format(timeout),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
