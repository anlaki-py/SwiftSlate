package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.SwiftSlateViewModel
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader

@Composable
fun SettingsScreen(viewModel: SwiftSlateViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle(stringResource(R.string.settings_title))

        SectionHeader(stringResource(R.string.settings_provider_title))
        ProviderCard(
            providerRepository = viewModel.providerRepository,
            keyRepository = viewModel.keyRepository,
            openAiClient = viewModel.openAiClient
        )

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader(stringResource(R.string.settings_trigger_prefix_title))
        TriggerPrefixCard(commandRepository = viewModel.commandRepository)

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader(stringResource(R.string.backup_title))
        BackupCard(commandRepository = viewModel.commandRepository)

        Spacer(modifier = Modifier.height(24.dp))

        SettingsFooter()

        Spacer(modifier = Modifier.height(16.dp))
    }
}
