package com.menulango

import com.menulango.data.billing.BillingRepository
import com.menulango.di.AppConfig
import com.menulango.di.sharedModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * The one call each platform makes at launch.
 *
 * Exists so Android's Application and iOS's app delegate stay a few lines long and identical in
 * meaning: build the graph, then start billing so the entitlement is known before any screen asks.
 */
public object MenuLango {
    private var started = false

    internal fun start(
        config: AppConfig,
        platformModule: Module,
    ) {
        if (started) return
        started = true
        val koin = startKoin { modules(sharedModule(config), platformModule) }.koin
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { koin.get<BillingRepository>().start() }
    }
}
