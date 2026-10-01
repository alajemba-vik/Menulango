package com.menulango

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.menulango.data.AppAttestation
import com.menulango.data.backup.IosBackupFileBridge
import com.menulango.data.db.MenuLangoDatabase
import com.menulango.di.AppConfig
import com.menulango.feature.order.IosNoteTranslationBridge
import com.menulango.feature.order.installIosNoteTranslationBridge
import com.menulango.feature.table.IosNearbyBridge
import com.menulango.feature.table.IosNearbyTransport
import com.menulango.feature.table.NearbyTransport
import com.menulango.platform.IosAppCheckAttestation
import com.menulango.platform.IosAppCheckBridge
import com.menulango.platform.installIosBackupFileBridge
import org.koin.dsl.module
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIViewController

/** iOS entry point: call once from the app's `init`, before the first view appears. */
public fun startMenuLango(
    proxyUrl: String,
    revenueCatApiKey: String,
    isDebug: Boolean,
    betaTools: Boolean,
    noteTranslationBridge: IosNoteTranslationBridge,
    backupFileBridge: IosBackupFileBridge,
    appCheckBridge: IosAppCheckBridge,
    nearbyBridge: IosNearbyBridge,
) {
    installIosNoteTranslationBridge(noteTranslationBridge)
    installIosBackupFileBridge(backupFileBridge)
    MenuLango.start(
        AppConfig(proxyUrl = proxyUrl, revenueCatApiKey = revenueCatApiKey, isDebug = isDebug, betaTools = betaTools),
        module {
            single<SqlDriver> { NativeSqliteDriver(MenuLangoDatabase.Schema, "menulango.db") }
            single<AppAttestation> { IosAppCheckAttestation(appCheckBridge) }
            single<NearbyTransport> { IosNearbyTransport(nearbyBridge) }
        },
    )
}

/** The whole app, as a view controller SwiftUI can host. Named as a type because Swift reads it as one. */
@Suppress("ktlint:standard:function-naming", "FunctionName")
public fun MainViewController(): UIViewController =
    ComposeUIViewController {
        MenuLangoApp(reduceMotion = UIAccessibilityIsReduceMotionEnabled())
    }
