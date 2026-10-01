package com.menulango.data.currency

/**
 * The ISO 4217 code behind what a menu or the model wrote: a code passes through, a symbol only
 * one currency uses becomes its code. "$", "¥" and "F" are left unknown on purpose: they belong to
 * too many currencies to convert safely.
 */
internal fun currencyCode(raw: String?): String? {
    val cleaned = raw?.trim() ?: return null
    if (cleaned.length == 3 && cleaned.all { it.isLetter() && it.code < 128 }) return cleaned.uppercase()
    return UNAMBIGUOUS_SYMBOLS[cleaned]
}

private val UNAMBIGUOUS_SYMBOLS =
    mapOf(
        "€" to "EUR",
        "£" to "GBP",
        "₹" to "INR",
        "₩" to "KRW",
        "₺" to "TRY",
        "฿" to "THB",
        "₫" to "VND",
        "₪" to "ILS",
        "₦" to "NGN",
        "₱" to "PHP",
        "₴" to "UAH",
        "₸" to "KZT",
        "₾" to "GEL",
        "₽" to "RUB",
    )

/** The currencies most diners call home, offered first when choosing one. */
internal val COMMON_CURRENCIES =
    listOf(
        "USD",
        "EUR",
        "GBP",
        "JPY",
        "CNY",
        "INR",
        "KRW",
        "CAD",
        "AUD",
        "CHF",
        "NGN",
        "XOF",
        "AED",
        "SAR",
        "BRL",
        "MXN",
    )
