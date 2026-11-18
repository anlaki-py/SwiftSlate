package com.musheer360.swiftslate.feature.google.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Some Android builds briefly block app traffic when the browser hands off a loopback callback. */
internal suspend fun awaitGoogleOAuthNetwork(context: Context) {
    if (Build.VERSION.SDK_INT < 29) return
    val manager = context.getSystemService(ConnectivityManager::class.java)
    var callback: ConnectivityManager.NetworkCallback? = null
    try {
        withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                        if (!blocked && resumed.compareAndSet(false, true) && continuation.isActive) continuation.resume(Unit)
                    }
                }
                manager.registerDefaultNetworkCallback(checkNotNull(callback))
            }
        }
    } finally {
        callback?.let {
            try { manager.unregisterNetworkCallback(it) }
            catch (_: IllegalArgumentException) { android.util.Log.d("GoogleOAuth", "Network callback was already removed") }
        }
    }
}
