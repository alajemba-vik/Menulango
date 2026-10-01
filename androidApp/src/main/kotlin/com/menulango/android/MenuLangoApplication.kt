package com.menulango.android

import android.app.Application
import com.menulango.MenuLango
import com.menulango.di.AppConfig
import com.menulango.start

/** Builds the dependency graph once per process. */
class MenuLangoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MenuLango.start(
            context = this,
            config =
                AppConfig(
                    proxyUrl = BuildConfig.PROXY_URL,
                    revenueCatApiKey = BuildConfig.REVENUECAT_ANDROID_KEY,
                    isDebug = BuildConfig.DEBUG,
                    betaTools = BuildConfig.BETA_TOOLS,
                ),
            attestation = startAppCheck(this),
        )
    }
}
