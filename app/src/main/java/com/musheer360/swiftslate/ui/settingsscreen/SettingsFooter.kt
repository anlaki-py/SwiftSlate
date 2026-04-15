package com.musheer360.swiftslate.ui.settingsscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.BuildConfig
import com.musheer360.swiftslate.R

/**
 * Settings footer — version display with check-for-updates link
 * and developer credit.
 */
@Composable
internal fun SettingsFooter() {
    val uriHandler = LocalUriHandler.current

    // Version + check updates
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(interactionSource = null, indication = null) {
                uriHandler.openUri("https://github.com/anlaki-py/SwiftSlate/releases/latest")
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.dashboard_version, BuildConfig.VERSION_NAME) + " · ",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.settings_check_updates),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }

    // Developer credit
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_made_by) + " ",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "anlaki",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(interactionSource = null, indication = null) {
                uriHandler.openUri("https://anlaki.dev")
            }
        )
    }
}
