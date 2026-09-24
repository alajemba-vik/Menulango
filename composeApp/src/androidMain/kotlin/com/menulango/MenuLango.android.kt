package com.menulango

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.menulango.data.db.MenuLangoDatabase
import com.menulango.di.AppConfig
import org.koin.dsl.module

/** Android entry point: call from `Application.onCreate`. */
public fun MenuLango.start(
    context: Context,
    config: AppConfig,
) {
    val appContext = context.applicationContext
    start(
        config,
        module {
            single<SqlDriver> { AndroidSqliteDriver(MenuLangoDatabase.Schema, appContext, "menulango.db") }
        },
    )
}
