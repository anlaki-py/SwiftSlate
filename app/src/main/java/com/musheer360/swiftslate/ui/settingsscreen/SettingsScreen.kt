package com.musheer360.swiftslate.ui.settingsscreen

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.manager.ProviderManager
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader

/**
 * Main settings screen — scrollable column that assembles all
 * settings section cards: provider config, trigger prefix, backup/restore,
 * and the footer with version info.
 *
 * @param commandManager The command manager for prefix and backup operations.
 * @param providerManager The provider manager for CRUD operations.
 * @param keyManager The key manager for per-provider key access.
 * @param prefs SharedPreferences for provider/model settings.
 */
@Composable
fun SettingsScreen(
    commandManager: CommandManager,
    providerManager: ProviderManager,
    keyManager: KeyManager,
    prefs: SharedPreferences
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { } // Hardware layer for smooth NavHost slide animations
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle(stringResource(R.string.settings_title))

        // Provider / model / endpoint / temperature
        SectionHeader(stringResource(R.string.settings_provider_title))
        ProviderCard(
            providerManager = providerManager,
            keyManager = keyManager,
            prefs = prefs
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Trigger prefix
        SectionHeader(stringResource(R.string.settings_trigger_prefix_title))
        TriggerPrefixCard(commandManager = commandManager)

        Spacer(modifier = Modifier.height(12.dp))

        // Backup / restore
        SectionHeader(stringResource(R.string.backup_title))
        BackupCard(commandManager = commandManager)

        Spacer(modifier = Modifier.height(24.dp))

        // Version + credits
        SettingsFooter()

        Spacer(modifier = Modifier.height(16.dp))
    }
}
