package com.menulango

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.menulango.data.AppAttestation
import com.menulango.data.db.MenuLangoDatabase
import com.menulango.di.AppConfig
import com.menulango.feature.table.AndroidNearbyTransport
import com.menulango.feature.table.NearbyTransport
import com.menulango.platform.installAndroidFeedbackContext
import org.koin.dsl.module

/** Android entry point: call from `Application.onCreate`. */
public fun MenuLango.start(
    context: Context,
    config: AppConfig,
    attestation: AppAttestation = AppAttestation.None,
) {
    val appContext = context.applicationContext
    installAndroidFeedbackContext(appContext)
    start(
        config,
        module {
            single<SqlDriver> { AndroidSqliteDriver(MenuLangoDatabase.Schema, appContext, "menulango.db") }
            single<NearbyTransport> { AndroidNearbyTransport(appContext) }
            single { attestation }
        },
    )
}
