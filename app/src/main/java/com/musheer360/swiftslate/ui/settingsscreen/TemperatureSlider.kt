package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemperatureSlider(
    temperature: Float,
    haptic: HapticFeedback,
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
            onTemperatureChange(temperature)
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
