package com.menulango.data.currency

import com.menulango.data.AppAttestation
import com.menulango.data.DeviceIdentity
import com.menulango.di.AppConfig
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Every currency against one base, as the proxy hands them out. */
@Serializable
internal data class RateTable(
    val base: String,
    /** When the source last published, in seconds since the epoch. */
    val updated: Long,
    val rates: Map<String, Double>,
) {
    /** [amount] of [from] in [to], or null when either currency is unknown. */
    fun convert(
        amount: Double,
        from: String,
        to: String,
    ): Double? {
        if (from == to) return amount
        val fromRate = rates[from]?.takeIf { it > 0 } ?: return null
        val toRate = rates[to]?.takeIf { it > 0 } ?: return null
        return amount / fromRate * toRate
    }
}

/**
 * Exchange rates for showing a menu's prices in the diner's own currency, fetched through the
 * proxy and kept on the phone, so a menu read offline still converts with the last known rates.
 * Refreshed at most every few hours. Every failure is quiet: the printed price is always shown.
 */
internal class ExchangeRates(
    private val client: HttpClient,
    private val config: AppConfig,
    private val device: DeviceIdentity,
    private val attestation: AppAttestation,
    private val settings: Settings,
    private val now: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val state = MutableStateFlow(readStored())

    val table: StateFlow<RateTable?> = state.asStateFlow()

    /** Fetches new rates when the kept ones are missing or stale. */
    suspend fun refresh() {
        lock.withLock {
            val fetchedAt = settings.getLong(KEY_FETCHED_AT, 0L)
            if (state.value != null && now() - fetchedAt < REFRESH_AFTER_MS) return
            if (!config.hasProxy) return
            val fresh =
                try {
                    fetch()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return
            settings.putString(KEY_TABLE, json.encodeToString(RateTable.serializer(), fresh))
            settings.putLong(KEY_FETCHED_AT, now())
            state.value = fresh
        }
    }

    private suspend fun fetch(): RateTable? {
        val token = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) { attestation.token() }
        val answer =
            client.get("${config.proxyUrl.trimEnd('/')}/rates") {
                header(DEVICE_HEADER, device.id)
                if (token != null) header(APP_CHECK_HEADER, token)
            }
        if (!answer.status.isSuccess()) return null
        return json.decodeFromString(RateTable.serializer(), answer.bodyAsText()).takeIf { it.rates.isNotEmpty() }
    }

    private fun readStored(): RateTable? =
        settings.getStringOrNull(KEY_TABLE)?.let {
            try {
                json.decodeFromString(RateTable.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }

    private companion object {
        const val KEY_TABLE = "rates.table"
        const val KEY_FETCHED_AT = "rates.fetchedAt"
        const val REFRESH_AFTER_MS = 6 * 60 * 60 * 1000L
        const val DEVICE_HEADER = "X-Device-Id"
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val APP_CHECK_TIMEOUT_MS = 5_000L
    }
}
