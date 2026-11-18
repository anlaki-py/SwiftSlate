package com.musheer360.swiftslate.feature.google.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.musheer360.swiftslate.MainActivity
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.SwiftSlateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the temporary OAuth callback alive while the user completes browser consent. */
class GoogleOAuthService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var login: Job? = null
    private val services get() = (application as SwiftSlateApp).aiServices

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            login?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (login?.isActive == true) {
            services.login.openBrowser()
            return START_NOT_STICKY
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            getString(R.string.google_login_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 41, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 42, Intent(this, GoogleOAuthService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.google_login_notification_title))
            .setContentText(getString(R.string.google_login_waiting))
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, getString(R.string.commands_cancel), cancel).build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        login = scope.launch {
            try {
                services.login.run(getString(R.string.google_callback_received), getString(R.string.google_callback_rejected),
                    beforeExchange = { awaitGoogleOAuthNetwork(this@GoogleOAuthService) }) {
                    services.activateGoogleProvider()
                }
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "google_sign_in"
        private const val NOTIFICATION_ID = 72
        private const val ACTION_CANCEL = "com.musheer360.swiftslate.CANCEL_GOOGLE_LOGIN"
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GoogleOAuthService::class.java))
        }
        fun cancel(context: Context) { context.stopService(Intent(context, GoogleOAuthService::class.java)) }
    }
}
