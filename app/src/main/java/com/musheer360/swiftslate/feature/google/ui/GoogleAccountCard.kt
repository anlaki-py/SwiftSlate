package com.musheer360.swiftslate.feature.google.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.feature.google.data.GoogleLoginPhase
import com.musheer360.swiftslate.feature.google.data.GoogleOAuthService
import com.musheer360.swiftslate.feature.google.domain.GoogleAccount
import com.musheer360.swiftslate.service.ErrorMessageMapper
import com.musheer360.swiftslate.ui.components.SlateCard

/** Hoisted account actions and state remain in the ViewModel; UI handles browser navigation. */
@Composable
fun GoogleAccountCard(onProvidersChanged: () -> Unit = {}, vm: GoogleAccountViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<GoogleAccount?>(null) }
    val gap = dimensionResource(R.dimen.google_account_gap)
    val isLoginActive = state.login.phase == GoogleLoginPhase.WAITING || state.login.phase == GoogleLoginPhase.FINISHING
    LaunchedEffect(vm) {
        vm.browser.collect { url ->
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: android.content.ActivityNotFoundException) {
                GoogleOAuthService.cancel(context)
                vm.browserFailed()
            }
        }
    }
    LaunchedEffect(state.login.phase, state.accounts, state.isWorking) { onProvidersChanged() }
    SlateCard {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Text(stringResource(R.string.google_accounts_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.google_accounts_description), style = MaterialTheme.typography.bodySmall)
            if (state.isWorking) CircularProgressIndicator()
            state.accounts.forEach { account ->
                GoogleAccountRow(account, !state.isWorking && !isLoginActive,
                    { vm.setEnabled(account.id, it) }, { vm.refresh(account.id) }, { deleting = account })
            }
            val error = state.login.failure ?: state.error
            if (error != null) {
                Text(ErrorMessageMapper.describe(context, error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                error.verificationUrl?.let { url ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Text(stringResource(R.string.google_verify_account))
                    }
                }
                TextButton(onClick = { vm.dismissError(); vm.load() }) { Text(stringResource(R.string.google_retry)) }
            }
            when (state.login.phase) {
                GoogleLoginPhase.WAITING -> {
                    Text(stringResource(R.string.google_login_waiting))
                    TextButton(onClick = vm::openBrowser) { Text(stringResource(R.string.google_open_browser)) }
                }
                GoogleLoginPhase.FINISHING -> Text(stringResource(R.string.google_login_finishing))
                GoogleLoginPhase.COMPLETE -> Text(stringResource(R.string.google_login_complete))
                else -> Unit
            }
            if (isLoginActive) {
                TextButton(onClick = { GoogleOAuthService.cancel(context) }) { Text(stringResource(R.string.commands_cancel)) }
            } else {
                Button(onClick = {
                    vm.dismissError()
                    try { GoogleOAuthService.start(context) } catch (_: IllegalStateException) { vm.browserFailed() }
                }, enabled = !state.isWorking) { Text(stringResource(R.string.google_add_account)) }
            }
            if (state.accounts.isNotEmpty()) {
                OutlinedButton(onClick = { vm.activate() }, enabled = !state.isWorking) {
                    Text(stringResource(R.string.google_use_provider))
                }
            }
        }
    }
    deleting?.let { account ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.google_remove_account)) },
            text = { Text(stringResource(R.string.google_remove_confirmation, account.email)) },
            confirmButton = { TextButton(onClick = { vm.remove(account.id); deleting = null }) {
                Text(stringResource(R.string.delete_confirm_button))
            } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.commands_cancel)) } })
    }
}

@Composable
private fun GoogleAccountRow(account: GoogleAccount, enabled: Boolean, onEnabled: (Boolean) -> Unit,
                             onRefresh: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyMedium)
                Text(account.email, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = account.isEnabled, onCheckedChange = onEnabled, enabled = enabled)
        }
        val status = when {
            !account.isEnabled -> R.string.google_account_disabled
            account.needsLogin -> R.string.google_error_login_required
            account.verificationUrl != null -> R.string.google_error_verify_account
            account.cooldownUntil > System.currentTimeMillis() -> R.string.google_error_rate_limited
            else -> R.string.google_account_ready
        }
        Text(stringResource(status), style = MaterialTheme.typography.bodySmall)
        account.remainingFraction?.let { fraction ->
            Text(stringResource(R.string.google_quota_remaining, (fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall)
        }
        Row {
            IconButton(onClick = onRefresh, enabled = enabled) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.google_refresh_account))
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.google_remove_account))
            }
            account.verificationUrl?.let { url ->
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Text(stringResource(R.string.google_verify_account))
                }
            }
        }
    }
}
