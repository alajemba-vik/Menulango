package com.menulango.di

import com.menulango.data.DeviceIdentity
import com.menulango.data.billing.BillingRepository
import com.menulango.data.db.MenuLangoDatabase
import com.menulango.data.history.EatenHistory
import com.menulango.data.menu.MenuRepository
import com.menulango.data.menu.local.MenuCache
import com.menulango.data.menu.remote.MenuApi
import com.menulango.data.menu.remote.MenuResponseParser
import com.menulango.data.menu.remote.ProxyMenuApi
import com.menulango.data.preferences.Preferences
import com.menulango.data.quota.QuotaStorage
import com.menulango.data.quota.ScanQuota
import com.menulango.data.tips.Tips
import com.menulango.feature.capture.CaptureViewModel
import com.menulango.feature.choose.ChooseViewModel
import com.menulango.feature.menu.MenuViewModel
import com.menulango.feature.menu.PageInbox
import com.menulango.feature.menus.MenusViewModel
import com.menulango.feature.order.OrderBook
import com.menulango.feature.order.NoteTranslator
import com.menulango.feature.order.onDeviceNoteTranslator
import com.menulango.feature.paywall.PaywallViewModel
import com.menulango.feature.settings.SettingsViewModel
import com.menulango.resources.Res
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

private val AppScope = named("appScope")

/**
 * Everything shared between Android and iOS. Platform modules add only the SQLite driver.
 */
internal fun sharedModule(config: AppConfig): Module =
    module {
        single { config }
        single(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        single { Settings() }
        single { DeviceIdentity(get()) }
        single { MenuResponseParser(failLoudly = config.isDebug) }
        single { proxyHttpClient() }
        single<MenuApi> { ProxyMenuApi(get(), get(), get(), get()) }
        single { MenuLangoDatabase(get()) }
        single { MenuCache(get(), get(), Dispatchers.IO, ::nowMillis) }
        single { EatenHistory(get(), Dispatchers.IO, ::nowMillis) }
        single {
            MenuRepository(
                api = get(),
                parser = get(),
                cache = get(),
                sampleMenu = { Res.readBytes(SAMPLE_MENU_PATH) },
                log = { println("MenuLango $it") },
            )
        }
        single { ScanQuota(SettingsQuotaStorage(get()), currentMonth = ::currentMonthKey) }
        single { BillingRepository(get(), get(AppScope)) }

        viewModel { CaptureViewModel(get(), get(), get(), get()) }
        single { PageInbox() }
        single { Preferences(get()) }
        single { Tips(get()) }
        single { OrderBook(get()) }
        single<NoteTranslator> { onDeviceNoteTranslator() }
        viewModel { params -> MenuViewModel(params.get(), get(), get(), get(), get(), get(), get(), get()) }
        viewModel { MenusViewModel(get(), ::nowMillis) }
        viewModel { SettingsViewModel(get(), get(), get(), get()) }
        viewModel { params -> ChooseViewModel(params.get(), params.get(), params.get(), get(), get(), get()) }
        viewModel { params -> PaywallViewModel(params.get(), get()) }
    }

private const val SAMPLE_MENU_PATH = "files/sample_menu.json"

private fun proxyHttpClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) { json(MenuResponseParser.LenientJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            // Between chunks, not in total: a long menu keeps streaming for several seconds.
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 90_000
        }
    }

private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** "2026-09": the free tier resets when this changes. */
private fun currentMonthKey(): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "${today.year}-${today.month.number.toString().padStart(2, '0')}"
}

private class SettingsQuotaStorage(
    private val settings: Settings,
) : QuotaStorage {
    override var month: String?
        get() = settings.getStringOrNull(KEY_MONTH)
        set(value) = if (value == null) settings.remove(KEY_MONTH) else settings.putString(KEY_MONTH, value)
    override var used: Int
        get() = settings.getInt(KEY_USED, 0)
        set(value) = settings.putInt(KEY_USED, value)

    private companion object {
        const val KEY_MONTH = "quota.month"
        const val KEY_USED = "quota.used"
    }
}
