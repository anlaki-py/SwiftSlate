# SwiftSlate — Android Process Persistence Best Practices

## Overview

SwiftSlate's architecture is built around `AssistantService`, which extends Android's `AccessibilityService`. This is actually the **strongest possible foundation** for an always-on app — accessibility services receive special OS-level treatment that regular foreground services don't. However, there are still several gaps in the current codebase that can lead to the service being killed or failing silently. This report covers each one with targeted code fixes.

---

## 1. Accessibility Service Is Already Your Best Anchor — Configure It Properly

`AccessibilityService` runs in a privileged process. Android will restart it automatically if it crashes, and it cannot be killed by the battery optimizer alone. However, the manifest configuration matters a lot.

**Add these attributes to your service declaration in `AndroidManifest.xml`:**

```xml
<service
    android:name=".service.AssistantService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true"
    android:stopWithTask="false"
    android:persistent="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**Key attribute:** `android:stopWithTask="false"` ensures the service keeps running even when the user swipes away the app from recents. Without this, SwiftSlate could die the moment the user dismisses the MainActivity.

**`accessibility_service_config.xml`** (if not already present):

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewTextChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRequestFilterKeyEvents"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_description" />
```

---

## 2. Add a Foreground Service With a Persistent Notification — Maximize Survival

While `AssistantService` (your `AccessibilityService`) already gets privileged treatment, pairing it with a **companion foreground service** gives you a second line of defense. A running foreground service with a visible notification tells Android that the process is actively doing meaningful work, making it extremely reluctant to reclaim it — even under heavy memory pressure or aggressive OEM battery management.

The idea is simple: `KeepAliveService` runs alongside `AssistantService`, holds a `START_STICKY` flag so it is immediately restarted after any kill, and shows a minimal persistent notification to satisfy Android's foreground service requirements. When the boot receiver fires, it starts this service first, which in turn primes the process so `AssistantService` resumes without a cold start.

### Step 1 — Create the Notification Channel

Channels must be created before any notification can be shown, and must be created every time the app starts (Android deduplicates them safely). Add this to `SwiftSlateApp.onCreate()`:

```kotlin
// SwiftSlateApp.kt
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SwiftSlateApp : Application() {

    companion object {
        const val CHANNEL_ID = "swiftslate_keepalive"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        commandManager.getTriggerPrefix() // pre-warm
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SwiftSlate Active",
                NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW = no sound, no heads-up
            ).apply {
                description = "Keeps SwiftSlate running in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
```

Using `IMPORTANCE_LOW` is the right call here — it keeps the notification visible in the status bar and drawer without making a sound or showing a heads-up banner every time the service starts.

### Step 2 — Create `KeepAliveService`

```kotlin
// swiftslate/service/KeepAliveService.kt
package com.musheer360.swiftslate.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.musheer360.swiftslate.MainActivity
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.SwiftSlateApp

class KeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SwiftSlateApp.NOTIFICATION_ID, buildNotification())
        // START_STICKY: if the OS kills this service, it will be restarted
        // automatically with a null intent — no data loss since we hold no state here
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Called when the user swipes the app from recents.
        // Re-schedule our own restart via a PendingIntent alarm so we come back
        // even if stopWithTask somehow fires on older ROMs.
        val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(): Notification {
        // Tapping the notification opens MainActivity
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SwiftSlateApp.CHANNEL_ID)
            .setContentTitle("SwiftSlate is active")
            .setContentText("Type a command like ?fix or ?improve in any app")
            .setSmallIcon(R.drawable.ic_notification) // use your existing icon
            .setContentIntent(openAppIntent)
            .setOngoing(true)       // Cannot be dismissed by the user
            .setSilent(true)        // No sound even if device is loud
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
```

The `onTaskRemoved` override is the key extra step — it schedules a self-restart via `AlarmManager` the moment the user swipes the app away, covering the gap window before `START_STICKY` kicks in on older or heavily modified ROMs.

### Step 3 — Start `KeepAliveService` From `AssistantService`

The best place to start the companion service is inside `onServiceConnected` — this fires when the accessibility service is fully bound and ready, guaranteeing both services are alive at the same time:

```kotlin
// AssistantService.kt
override fun onServiceConnected() {
    super.onServiceConnected()

    // Start the foreground keep-alive companion
    val keepAliveIntent = Intent(this, KeepAliveService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(keepAliveIntent)
    } else {
        startService(keepAliveIntent)
    }

    // ... rest of your existing onServiceConnected logic
}
```

### Step 4 — Register `KeepAliveService` in the Manifest

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- Android 14+ requires declaring the foreground service type -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".service.KeepAliveService"
    android:exported="false"
    android:stopWithTask="false"
    android:foregroundServiceType="specialUse">
    <!-- specialUse is the correct type for companion keep-alive services
         that don't fit camera/microphone/location/etc categories -->
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="keepAlive" />
</service>
```

> **Note on Android 14+:** Google tightened foreground service type requirements in API 34. The `specialUse` type with a declared subtype property is the correct approach for a keep-alive companion that doesn't fit a specific data-type category. You will need to justify this use case in your Play Store declaration if you publish there.

### What This Achieves

Running both `AssistantService` and `KeepAliveService` together gives you a layered defense:

- `AssistantService` is privileged and auto-restarted by the OS on crash
- `KeepAliveService` holds a foreground slot, keeping the process priority elevated so it is the last thing Android considers reclaiming under memory pressure
- The persistent notification signals to every OEM battery manager that this process is intentionally ongoing work
- `onTaskRemoved` + `AlarmManager` covers the swipe-from-recents edge case that `stopWithTask="false"` doesn't always catch on custom ROMs

---

## 3. Handle BOOT_COMPLETED — Start on Boot and Reinitialize State

A key misconception: accessibility services **do survive reboots** — if the user has enabled them, Android re-enables them automatically. But your **app process** doesn't start until something triggers it. The boot receiver solves this by immediately starting `KeepAliveService` on boot, which primes the process and elevates its priority before `AssistantService` even resumes.

**`BootReceiver.kt`:**

```kotlin
package com.musheer360.swiftslate.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.musheer360.swiftslate.service.KeepAliveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("SwiftSlate", "Boot completed — starting KeepAliveService to prime process")

            // Start the foreground service immediately on boot.
            // This primes the process with a foreground slot before AssistantService
            // resumes, preventing a cold-start gap window on aggressive OEMs.
            val keepAliveIntent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(keepAliveIntent)
            } else {
                context.startService(keepAliveIntent)
            }
        }
    }
}
```

**In `AndroidManifest.xml`:**

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<receiver
    android:name=".receiver.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
        <!-- For Huawei devices -->
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</receiver>
```

---

## 4. Fix the Coroutine Scope — Add a Global Exception Handler

Your current `AssistantService` uses a `SupervisorJob`, which is correct — a child coroutine failure won't cancel siblings. But there is no `CoroutineExceptionHandler`, meaning unhandled exceptions in coroutines are silently swallowed.

**Current code (gap):**

```kotlin
private val serviceJob = SupervisorJob()
private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
```

**Fixed version:**

```kotlin
private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("AssistantService", "Unhandled coroutine exception", throwable)
    // Optionally show an overlay toast for unexpected errors
    handler.post {
        showOverlayToast("⚠ Unexpected error: ${throwable.localizedMessage}", isError = true)
    }
}

private val serviceJob = SupervisorJob()
private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO + exceptionHandler)
```

This gives you visibility into coroutine failures and the ability to show feedback to the user instead of the service silently doing nothing.

---

## 5. Clean Up Resources in onDestroy — Prevent Memory Leaks That Cause Crashes

`AssistantService` manages several objects that hold references to the window and view system: overlay toasts, animators, and `Handler` runnables. If these aren't torn down on `onDestroy`, they can cause memory leaks that eventually lead to a crash — which kills the service.

**Add this to `AssistantService`:**

```kotlin
override fun onDestroy() {
    super.onDestroy()

    // Cancel all coroutines
    serviceScope.cancel()

    // Cancel current API job
    currentJob?.cancel()

    // Remove all pending handler callbacks
    handler.removeCallbacksAndMessages(null)

    // Cancel and clean up animators
    dismissAnimator?.cancel()
    enterAnimator?.cancel()
    dismissAnimator = null
    enterAnimator = null

    // Remove overlay view from window if still attached
    try {
        currentOverlayToast?.let { view ->
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
        }
    } catch (e: Exception) {
        Log.w("AssistantService", "Error removing overlay on destroy", e)
    }
    currentOverlayToast = null
}
```

---

## 6. Use SwiftSlateApp for Global Initialization

Your `SwiftSlateApp` is currently empty. This is a missed opportunity. Any shared managers or caches that are expensive to initialize (like `CommandManager` or `KeyManager`) should be initialized once here rather than inside the service.

**`SwiftSlateApp.kt`:**

```kotlin
package com.musheer360.swiftslate

import android.app.Application
import android.util.Log
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager

class SwiftSlateApp : Application() {

    // Lazily initialized singletons — safe to access from anywhere
    val commandManager: CommandManager by lazy { CommandManager(this) }
    val keyManager: KeyManager by lazy { KeyManager(this) }

    override fun onCreate() {
        super.onCreate()
        Log.d("SwiftSlateApp", "Application initialized")
        // Pre-warm the command manager so first keystroke isn't slow
        commandManager.getTriggerPrefix()
    }
}
```

Then in `AssistantService`, replace direct instantiation:

```kotlin
// Before
private lateinit var keyManager: KeyManager
private lateinit var commandManager: CommandManager

// After — access from Application
private val app: SwiftSlateApp get() = application as SwiftSlateApp
private val keyManager get() = app.keyManager
private val commandManager get() = app.commandManager
```

This ensures managers are not re-created on service restarts, and their state is consistent across the process lifetime.

---

## 7. Handle OEM Battery Killers — The Real Problem on Most Devices

On stock Android, your accessibility service is effectively unkillable. The real enemy is OEM battery management layers on **Xiaomi (MIUI)**, **Huawei (EMUI)**, **Samsung (One UI)**, **OnePlus**, and **Oppo/Realme**. These have separate whitelists completely independent of the standard battery optimization setting.

**Guide users from your `DashboardScreen` or `SettingsScreen`:**

```kotlin
fun openManufacturerSettings(context: Context) {
    val intents = listOf(
        // Xiaomi / MIUI
        Intent().setComponent(ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HideAppsContainerManagementActivity"
        )),
        // Huawei / EMUI
        Intent().setComponent(ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startemission.StartupEmissionActivity"
        )),
        // Samsung One UI
        Intent().setComponent(ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.battery.ui.BatteryActivity"
        )),
        // OnePlus
        Intent().setComponent(ComponentName(
            "com.oneplus.security",
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
        )),
        // Fallback: standard battery optimization
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )

    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) { /* try next */ }
    }
}
```

Call this from a "Fix battery settings" button in your settings screen, shown prominently when the user is on a known problematic OEM (detect via `Build.MANUFACTURER`).

---

## 8. Add FCM Silent Push as a Revival Fallback (Optional but Robust)

For cases where the service gets killed and doesn't auto-restart (rare on accessibility services, but possible on heavily customized ROMs), a silent FCM push can act as a wake-up signal.

```kotlin
// In your FirebaseMessagingService subclass
class SwiftSlateFCMService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // Silent push — no notification shown, just a revival ping
        if (message.data["type"] == "heartbeat_ping") {
            val serviceIntent = Intent(this, AssistantService::class.java)
            // The accessibility service will be restarted by the system anyway,
            // but this ensures your Application is initialized
            applicationContext.startService(serviceIntent)
        }
    }
}
```

This requires setting up Firebase in the project, but for a production app with users on aggressive OEMs, it's the most reliable fallback.

---

## 9. SharedPreferences: Use `commit()` for Critical State, `apply()` for Everything Else

In `CommandManager`, prefix changes use `commit()` (synchronous write) — this is correct. However, other writes use `apply()` which is async and can be lost if the process is killed mid-write. For settings that would break the service if lost (like the API key index), use `commit()`.

```kotlin
// For non-critical data (command list, preferences) — async is fine
prefs.edit().putString("custom_commands", newArr.toString()).apply()

// For critical settings that would break the service if lost — use synchronous write
settingsPrefs.edit().putString(PREF_TRIGGER_PREFIX, newPrefix).commit()

// For KeyManager — always synchronous since losing key index is catastrophic
keyPrefs.edit().putString("active_key_id", keyId).commit()
```

---

## Summary

| Technique | Priority | Status in SwiftSlate |
|---|---|---|
| `stopWithTask="false"` in manifest | 🔴 Critical | Likely missing |
| Foreground service + persistent notification | 🔴 Critical | Missing |
| `CoroutineExceptionHandler` on scope | 🔴 Critical | Missing |
| Proper `onDestroy` cleanup | 🔴 Critical | Likely incomplete |
| Start foreground service on `BOOT_COMPLETED` | 🟠 High | Missing |
| OEM battery whitelist guidance | 🟠 High | Missing |
| `SwiftSlateApp` singleton managers | 🟠 High | Empty class |
| `commit()` for critical prefs | 🟡 Medium | Partially done |
| FCM silent push revival | 🟢 Optional | Not present |

The biggest immediate wins are items 1, 2, 3, and 4 — the manifest flag, the foreground service companion, the exception handler, and the `onDestroy` cleanup. Together they form a layered defense that covers every common cause of process death in accessibility apps.
