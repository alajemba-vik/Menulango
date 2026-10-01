package com.menulango.feature.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.data.currency.ExchangeRates
import com.menulango.data.currency.RateTable
import com.menulango.data.currency.currencyCode
import com.menulango.data.preferences.Preferences
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Turns a menu's prices into the diner's own currency, as a rough guide: "≈ ₦21,400". Rounded
 * so it reads as an estimate, never as a quote.
 */
internal class PriceConverter(
    private val table: RateTable,
    val home: String,
) {
    /** "≈ ₦21,400", or null when [currency] is unknown, already [home], or not in the rates. */
    fun convert(
        amount: Double,
        currency: String?,
    ): String? {
        val from = currencyCode(currency) ?: return null
        if (from == home) return null
        val converted = table.convert(amount, from, home) ?: return null
        return "≈ " + currencyPrefix(home) + formatEstimate(converted)
    }
}

/**
 * The diner's converter, or null while conversion is off or no rates are known yet. Turning it on
 * fetches rates if the phone has none, or only stale ones.
 */
@Composable
internal fun rememberPriceConverter(): PriceConverter? {
    val preferences = koinInject<Preferences>()
    val rates = koinInject<ExchangeRates>()
    val on by preferences.convertPrices.collectAsStateWithLifecycle()
    val home by preferences.homeCurrency.collectAsStateWithLifecycle()
    val table by rates.table.collectAsStateWithLifecycle()
    LaunchedEffect(on) { if (on) rates.refresh() }
    val current = table
    return remember(on, home, current) { if (on && current != null) PriceConverter(current, home) else null }
}

/** Whole units from 100 up, with thousands grouped; two decimals below. */
internal fun formatEstimate(amount: Double): String {
    if (abs(amount) >= 100) {
        return amount
            .roundToLong()
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
    }
    val cents = (amount * 100).roundToLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
