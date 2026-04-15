package com.musheer360.swiftslate.ui.settingsscreen

import android.content.SharedPreferences
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.domain.EndpointValidation
import com.musheer360.swiftslate.domain.EndpointValidationResult
import com.musheer360.swiftslate.model.ProviderType
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Provider configuration card — provider dropdown, model selector
 * (Gemini/Groq dropdowns or custom text field), endpoint input for
 * custom providers, and temperature slider.
 *
 * @param prefs SharedPreferences for reading and persisting settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderCard(prefs: SharedPreferences) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var saveEndpointJob by remember { mutableStateOf<Job?>(null) }
    var saveModelJob by remember { mutableStateOf<Job?>(null) }

    var providerType by remember {
        mutableStateOf(prefs.getString("provider_type", ProviderType.GEMINI) ?: ProviderType.GEMINI)
    }
    var providerExpanded by remember { mutableStateOf(false) }

    var selectedModel by remember {
        mutableStateOf(prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite")
    }
    var modelExpanded by remember { mutableStateOf(false) }
    val geminiModels = listOf(
        "gemini-2.5-flash-lite", "gemini-3-flash-preview", "gemini-3.1-flash-lite-preview"
    )

    var groqModel by remember {
        mutableStateOf(prefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile")
    }
    var groqModelExpanded by remember { mutableStateOf(false) }
    val groqModels = listOf(
        "llama-3.3-70b-versatile", "llama-3.1-8b-instant",
        "openai/gpt-oss-120b", "openai/gpt-oss-20b",
        "meta-llama/llama-4-scout-17b-16e-instruct"
    )

    var customEndpoint by rememberSaveable {
        mutableStateOf(prefs.getString("custom_endpoint", "") ?: "")
    }
    var customModel by rememberSaveable {
        mutableStateOf(prefs.getString("custom_model", "") ?: "")
    }
    var endpointError by remember { mutableStateOf<String?>(null) }

    val endpointErrorScheme = stringResource(R.string.settings_endpoint_error_scheme)
    val endpointErrorSpaces = stringResource(R.string.settings_endpoint_error_spaces)

    var temperature by remember { mutableStateOf(prefs.getFloat("temperature", 0.7f)) }

    // Flush pending debounced writes when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            saveEndpointJob?.cancel()
            saveModelJob?.cancel()
            val editor = prefs.edit()
            var needsWrite = false
            if (customEndpoint != (prefs.getString("custom_endpoint", "") ?: "")) {
                if (EndpointValidation.isSafeToSave(customEndpoint)) {
                    editor.putString("custom_endpoint", customEndpoint)
                    needsWrite = true
                }
            }
            if (customModel != (prefs.getString("custom_model", "") ?: "")) {
                editor.putString("custom_model", customModel)
                needsWrite = true
            }
            if (needsWrite) editor.apply()
        }
    }

    SlateCard {
        // Provider dropdown
        ProviderDropdown(
            providerType = providerType,
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded },
            onProviderSelected = { type ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                providerType = type
                prefs.edit().putString("provider_type", type).apply()
                providerExpanded = false
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Custom endpoint input — only for custom provider
        if (providerType == ProviderType.CUSTOM) {
            Text(
                text = stringResource(R.string.settings_endpoint_desc),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SlateTextField(
                value = customEndpoint,
                onValueChange = {
                    customEndpoint = it
                    val result = EndpointValidation.validate(it)
                    endpointError = when (result) {
                        is EndpointValidationResult.Valid -> null
                        is EndpointValidationResult.Error -> when (result.messageKey) {
                            "spaces" -> endpointErrorSpaces
                            else -> endpointErrorScheme
                        }
                    }
                    if (endpointError == null) {
                        saveEndpointJob?.cancel()
                        saveEndpointJob = scope.launch {
                            delay(500)
                            prefs.edit().putString("custom_endpoint", it).apply()
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.settings_endpoint_placeholder)) },
                isError = endpointError != null
            )
            endpointError?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Model selector — varies by provider
        when (providerType) {
            ProviderType.GEMINI -> ModelDropdown(
                selectedModel = selectedModel,
                models = geminiModels,
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = !modelExpanded },
                onModelSelected = { model ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedModel = model
                    prefs.edit().putString("model", model).apply()
                    modelExpanded = false
                }
            )
            ProviderType.GROQ -> ModelDropdown(
                selectedModel = groqModel,
                models = groqModels,
                expanded = groqModelExpanded,
                onExpandedChange = { groqModelExpanded = !groqModelExpanded },
                onModelSelected = { model ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    groqModel = model
                    prefs.edit().putString("groq_model", model).apply()
                    groqModelExpanded = false
                }
            )
            else -> {
                Text(
                    text = stringResource(R.string.settings_model_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SlateTextField(
                    value = customModel,
                    onValueChange = {
                        customModel = it
                        saveModelJob?.cancel()
                        saveModelJob = scope.launch {
                            delay(500)
                            prefs.edit().putString("custom_model", it).apply()
                        }
                    },
                    placeholder = { Text(stringResource(R.string.settings_model_placeholder)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TemperatureSlider(temperature, haptic, prefs) { temperature = it }
    }
}

/**
 * Provider type dropdown — Gemini, Groq, or Custom.
 *
 * @param providerType Currently selected provider key.
 * @param expanded Whether the dropdown menu is showing.
 * @param onExpandedChange Toggles the dropdown.
 * @param onProviderSelected Callback when a provider is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    providerType: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onProviderSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange() }
    ) {
        SlateTextField(
            value = when (providerType) {
                ProviderType.GEMINI -> stringResource(R.string.settings_provider_gemini)
                ProviderType.GROQ -> stringResource(R.string.settings_provider_groq)
                else -> stringResource(R.string.settings_provider_custom)
            },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            expanded = expanded,
            onDismissRequest = { onProviderSelected(providerType) }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_provider_gemini)) },
                onClick = { onProviderSelected(ProviderType.GEMINI) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_provider_groq)) },
                onClick = { onProviderSelected(ProviderType.GROQ) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_provider_custom)) },
                onClick = { onProviderSelected(ProviderType.CUSTOM) }
            )
        }
    }
}

/**
 * Reusable model dropdown — used for both Gemini and Groq model lists.
 *
 * @param selectedModel Currently selected model string.
 * @param models List of available model names.
 * @param expanded Whether the dropdown is open.
 * @param onExpandedChange Toggles the dropdown.
 * @param onModelSelected Callback when a model is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    selectedModel: String,
    models: List<String>,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { onExpandedChange() }
    ) {
        SlateTextField(
            value = selectedModel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            expanded = expanded,
            onDismissRequest = { /* dismiss handled by onExpandedChange */ }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = { onModelSelected(model) }
                )
            }
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
private fun TemperatureSlider(
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
