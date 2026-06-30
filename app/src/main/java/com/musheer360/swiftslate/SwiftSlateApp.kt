package com.musheer360.swiftslate

import android.app.Application
import com.musheer360.swiftslate.service.KeepAliveService
import com.musheer360.swiftslate.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SwiftSlateApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        NotificationHelper.createChannel(this)
        KeepAliveService.start(this)
    }
}
