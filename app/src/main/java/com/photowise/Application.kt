package com.photowise

import android.app.Application
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.KotlinXJsonConverter
import dev.hotwire.core.config.Hotwire
import dev.hotwire.navigation.config.registerBridgeComponents
import timber.log.Timber
import dev.hotwire.core.BuildConfig

class PhotowiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())
        Hotwire.config.debugLoggingEnabled = BuildConfig.DEBUG
        // Initialize Hotwire
        Hotwire.config.debugLoggingEnabled = true
        Hotwire.config.jsonConverter = KotlinXJsonConverter()

        // Register the speech bridge component factory
        Hotwire.registerBridgeComponents(
            BridgeComponentFactory("speech") { name, delegate ->
                SpeechComponent(this, delegate)
            }
        )
    }
}