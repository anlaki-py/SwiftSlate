package com.musheer360.swiftslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.musheer360.swiftslate.service.KeepAliveService
import com.musheer360.swiftslate.ui.DashboardScreen
import com.musheer360.swiftslate.ui.KeysScreen
import com.musheer360.swiftslate.ui.commandsscreen.CommandsScreen
import com.musheer360.swiftslate.ui.settingsscreen.SettingsScreen
import com.musheer360.swiftslate.ui.theme.SwiftSlateTheme
import dagger.hilt.android.AndroidEntryPoint

enum class Tab(val titleRes: Int, val icon: ImageVector) {
    Dashboard(R.string.dashboard_title, Icons.Default.Home),
    Keys(R.string.keys_title, Icons.Default.Lock),
    Commands(R.string.commands_title, Icons.AutoMirrored.Filled.List),
    Settings(R.string.settings_title, Icons.Default.Settings)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        KeepAliveService.start(this)

        enableEdgeToEdge()
        setContent {
            SwiftSlateTheme {
                SwiftSlateMainScreen()
            }
        }
    }
}

@Composable
fun SwiftSlateMainScreen(vm: SwiftSlateViewModel = hiltViewModel()) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Dashboard) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                NavigationSuiteItem(
                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.titleRes)) },
                    label = { Text(stringResource(tab.titleRes)) },
                    selected = selectedTab == tab,
                    onClick = {
                        if (selectedTab != tab) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTab = tab
                        }
                    }
                )
            }
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                Tab.Dashboard -> DashboardScreen(viewModel = vm)
                Tab.Keys -> KeysScreen(viewModel = vm)
                Tab.Commands -> CommandsScreen(viewModel = vm)
                Tab.Settings -> SettingsScreen(viewModel = vm)
            }
        }
    }
}
