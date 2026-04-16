package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { } 
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle(stringResource(R.string.settings_title))

        // Provider / model / endpoint
        SectionHeader(stringResource(R.string.settings_provider_title))
        ProviderCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(12.dp))

        // Trigger prefix
        SectionHeader(stringResource(R.string.settings_trigger_prefix_title))
        TriggerPrefixCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(12.dp))

        // Backup / restore
        SectionHeader(stringResource(R.string.backup_title))
        BackupCard(viewModel = viewModel)

        Spacer(modifier = Modifier.height(24.dp))

        // Version + credits
        SettingsFooter()

        Spacer(modifier = Modifier.height(16.dp))
    }
}
