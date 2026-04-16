package com.musheer360.swiftslate.ui.settingsscreen

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.model.Provider
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Provider dropdown selector built from user-defined providers,
 * with an "Add Provider" option at the bottom.
 *
 * @param providers All user-defined providers.
 * @param activeProvider Currently selected provider, or null.
 * @param expanded Whether the dropdown menu is showing.
 * @param onExpandedChange Toggles the dropdown.
 * @param onProviderSelected Callback when a provider is chosen.
 * @param onAddClick Callback when "Add Provider" is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderDropdown(
    providers: List<Provider>,
    activeProvider: Provider?,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onProviderSelected: (Provider) -> Unit,
    onAddClick: () -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange() }
    ) {
        SlateTextField(
            value = activeProvider?.name ?: stringResource(R.string.settings_no_provider),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            expanded = expanded,
            onDismissRequest = { onExpandedChange() }
        ) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.name) },
                    onClick = { onProviderSelected(provider) }
                )
            }
            // Add Provider option
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_add_provider),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = onAddClick
            )
        }
    }
}

/**
 * Inline temperature slider with label and current value display.
 * Range: 0.0–2.0 in 0.1 steps. Saves to SharedPreferences on release.
 *
 * @param temperature Current temperature value.
 * @param haptic Haptic feedback provider.
 * @param prefs SharedPreferences to persist the value.
 * @param onTemperatureChange Callback when temperature changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemperatureSlider(
    temperature: Float,
    haptic: HapticFeedback,
    prefs: SharedPreferences,
    onTemperatureChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_temperature_desc),
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

/**
 * Clickable modifier that suppresses the default ripple indication.
 * Used for text fields that act as tap targets (e.g. model selector).
 *
 * @param onClick The action to perform on click.
 * @return A [Modifier] with click handling and no ripple.
 */
internal fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}
