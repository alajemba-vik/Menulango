package com.menulango.platform

import java.util.Currency
import java.util.Locale

internal actual fun deviceCurrencyCode(): String? =
    try {
        Currency.getInstance(Locale.getDefault())?.currencyCode
    } catch (e: IllegalArgumentException) {
        // A locale with no region ("en") has no currency.
        null
    }
