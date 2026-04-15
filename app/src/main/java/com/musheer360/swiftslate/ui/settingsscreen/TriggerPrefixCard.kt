package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateTextField

/**
 * Trigger prefix configuration card — single-character input with
 * inline validation for length, whitespace, and alphanumeric restrictions.
 *
 * @param commandManager Used to read and update the trigger prefix.
 */
@Composable
internal fun TriggerPrefixCard(commandManager: CommandManager) {
    val haptic = LocalHapticFeedback.current

    var triggerPrefix by remember { mutableStateOf(commandManager.getTriggerPrefix()) }
    var prefixError by remember { mutableStateOf<String?>(null) }

    val prefixErrorLength = stringResource(R.string.settings_prefix_error_length)
    val prefixErrorWhitespace = stringResource(R.string.settings_prefix_error_whitespace)
    val prefixErrorAlphanumeric = stringResource(R.string.settings_prefix_error_alphanumeric)

    SlateCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_trigger_prefix_desc, triggerPrefix),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            )
            SlateTextField(
                value = triggerPrefix,
                onValueChange = { input ->
                    val filtered = input.take(1)
                    triggerPrefix = filtered
                    prefixError = when {
                        filtered.length != 1 -> prefixErrorLength
                        filtered[0].isWhitespace() -> prefixErrorWhitespace
                        filtered[0].isLetterOrDigit() -> prefixErrorAlphanumeric
                        else -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            commandManager.setTriggerPrefix(filtered)
                            null
                        }
                    }
                },
                isError = prefixError != null,
                modifier = Modifier.width(64.dp)
            )
        }
        prefixError?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
