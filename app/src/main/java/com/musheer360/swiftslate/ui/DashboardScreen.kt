package com.musheer360.swiftslate.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.service.AccessibilityHelper
import com.musheer360.swiftslate.service.BatteryOptimizationHelper
import com.musheer360.swiftslate.ui.components.BatteryOptimizationCard
import com.musheer360.swiftslate.ui.components.ScreenTitle
import com.musheer360.swiftslate.ui.components.SectionHeader
import com.musheer360.swiftslate.ui.components.SlateCard
import com.musheer360.swiftslate.ui.components.SlateDivider
import com.musheer360.swiftslate.ui.shared.SwiftSlateViewModel
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(vm: SwiftSlateViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by vm.dashboardState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val listener = AccessibilityManager.AccessibilityStateChangeListener {
            vm.refreshDashboard()
        }
        am.addAccessibilityStateChangeListener(listener)
        onDispose { am.removeAccessibilityStateChangeListener(listener) }
    }

    LaunchedEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(500)
            while (true) {
                vm.refreshDashboard()
                delay(3000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.dashboard_title))

        SectionHeader(stringResource(R.string.service_status_title))
        SlateCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isServiceEnabled) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (state.isServiceEnabled) stringResource(R.string.service_status_active)
                        else stringResource(R.string.service_status_inactive),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (state.isServiceEnabled) {
                            vm.stopService()
                        } else {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isServiceEnabled) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (state.isServiceEnabled) stringResource(R.string.service_stop)
                        else stringResource(R.string.service_enable),
                        color = if (state.isServiceEnabled) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SlateDivider()
            Spacer(modifier = Modifier.height(12.dp))

            if (state.activeProviderName != null) {
                Text(
                    text = stringResource(R.string.dashboard_provider_info, state.activeProviderName!!, state.keyCount),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_no_provider_hint),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(stringResource(R.string.battery_optimization_title))
        BatteryOptimizationCard(
            isBatteryOptimized = state.isBatteryOptimized,
            onUnrestrictClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(stringResource(R.string.dashboard_how_to_use_title))
        SlateCard {
            Text(
                text = stringResource(R.string.dashboard_how_to_use_body, state.currentPrefix),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
