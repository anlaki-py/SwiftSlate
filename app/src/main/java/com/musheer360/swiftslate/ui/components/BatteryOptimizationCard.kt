package com.musheer360.swiftslate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R

/**
 * Dashboard card that shows battery-optimization status and lets the user
 * disable it with a single tap.
 *
 * Displays a green dot + "Unrestricted" when the app is exempt, or a
 * red dot + "Restricted" with an action button when it is not.
 *
 * @param isBatteryOptimized `true` when the app is **not** exempt from
 *                           battery optimization (i.e. restricted).
 * @param onUnrestrictClick  Callback invoked when the user taps "Unrestrict".
 */
@Composable
fun BatteryOptimizationCard(
    isBatteryOptimized: Boolean,
    onUnrestrictClick: () -> Unit
) {
    SlateCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Green = unrestricted (good), Red = restricted (bad)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isBatteryOptimized) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (!isBatteryOptimized) stringResource(R.string.battery_optimization_exempt)
                    else stringResource(R.string.battery_optimization_restricted),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Show the unrestrict button only when battery optimization is active
            if (isBatteryOptimized) {
                Button(
                    onClick = onUnrestrictClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        stringResource(R.string.battery_optimization_disable),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
