package com.musheer360.swiftslate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musheer360.swiftslate.service.KeepAliveService
import com.musheer360.swiftslate.ui.commandsscreen.CommandsScreen
import com.musheer360.swiftslate.ui.dashboardscreen.DashboardScreen
import com.musheer360.swiftslate.ui.keysscreen.KeysScreen
import com.musheer360.swiftslate.ui.settingsscreen.SettingsScreen
import com.musheer360.swiftslate.ui.dashboardscreen.DashboardViewModel
import com.musheer360.swiftslate.ui.keysscreen.KeysViewModel
import com.musheer360.swiftslate.ui.settingsscreen.SettingsViewModel
import com.musheer360.swiftslate.ui.theme.SwiftSlateTheme

enum class Tab(@StringRes val titleRes: Int, val icon: ImageVector) {
    Dashboard(R.string.dashboard_title, Icons.Default.Home),
    Keys(R.string.keys_title, Icons.Default.Lock),
    Commands(R.string.commands_title, Icons.AutoMirrored.Filled.List),
    Settings(R.string.settings_title, Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    /**
     * Sets up the Compose UI and ensures the keep-alive foreground service
     * is running.
     *
     * Starting the service here (in addition to [SwiftSlateApp.onCreate])
     * guarantees a foreground-activity context, which satisfies Android 12+
     * FGS start restrictions on all OEMs including Xiaomi/HyperOS.
     *
     * @param savedInstanceState Saved instance state bundle.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guaranteed foreground context — most reliable FGS start point
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
fun SwiftSlateMainScreen(vm: SwiftSlateViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Dashboard) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = stringResource(tab.titleRes)
                            )
                        },
                        label = null,
                        selected = selectedTab == tab,
                        onClick = {
                            if (selectedTab != tab) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = tab
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val screens = remember {
            Tab.entries.associateWith { tab ->
                movableContentOf {
                        val factory = AppViewModelFactory(
                            vm.getApplication(), vm.keyManager, vm.commandManager, vm.providerManager
                        )
                        when (tab) {
                            Tab.Dashboard -> DashboardScreen(viewModel<DashboardViewModel>(factory = factory))
                            Tab.Keys -> KeysScreen(viewModel<KeysViewModel>(factory = factory))
                            Tab.Commands -> CommandsScreen(vm.commandManager)
                            Tab.Settings -> SettingsScreen(viewModel<SettingsViewModel>(factory = factory))
                        }
                }
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal)
                    AnimatedContentTransitionScope.SlideDirection.Left
                else
                    AnimatedContentTransitionScope.SlideDirection.Right
                slideIntoContainer(direction, tween(250, easing = FastOutSlowInEasing)) togetherWith
                    slideOutOfContainer(direction, tween(250, easing = FastOutSlowInEasing))
            },
            label = "tab_transition"
        ) { tab ->
            screens[tab]?.invoke()
        }
    }
}
