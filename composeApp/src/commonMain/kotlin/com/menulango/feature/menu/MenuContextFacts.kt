package com.menulango.feature.menu

import com.menulango.data.menu.model.Dish
import kotlin.math.roundToInt

/**
 * The facts behind the context strip: "23 dishes · Greek · avg €12".
 *
 * Pure functions, so the strip can be previewed and reasoned about without Compose.
 */
internal data class MenuContextFacts(
    val dishCount: Int,
    val languageName: String?,
    val averagePrice: String?,
)

internal fun menuContextFacts(
    dishes: List<Dish>,
    languageCode: String?,
    currencyCode: String?,
): MenuContextFacts {
    val prices = dishes.mapNotNull { it.price?.amount }.filter { it > 0 }
    val average =
        if (prices.isEmpty()) {
            null
        } else {
            val mean = prices.average()
            val amount = if (mean >= 10) mean.roundToInt().toString() else ((mean * 10).roundToInt() / 10.0).toString()
            currencyPrefix(currencyCode ?: dishes.firstNotNullOfOrNull { it.price?.currency }) + amount
        }
    return MenuContextFacts(dishes.size, languageCode?.let(::languageName), average)
}

internal fun currencyPrefix(code: String?): String =
    when (code?.uppercase()) {
        null -> ""
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        "JPY", "CNY" -> "¥"
        "KRW" -> "₩"
        "INR" -> "₹"
        "TRY" -> "₺"
        "THB" -> "฿"
        "VND" -> "₫"
        "ILS" -> "₪"
        else -> "$code "
    }

/** English names for the menu languages travellers meet most; anything else shows its code. */
internal fun languageName(code: String): String =
    LANGUAGE_NAMES[code.substringBefore('-').lowercase()] ?: code.uppercase()

private val LANGUAGE_NAMES =
    mapOf(
        "ar" to "Arabic",
        "bg" to "Bulgarian",
        "ca" to "Catalan",
        "cs" to "Czech",
        "da" to "Danish",
        "de" to "German",
        "el" to "Greek",
        "en" to "English",
        "es" to "Spanish",
        "eu" to "Basque",
        "fa" to "Persian",
        "fi" to "Finnish",
        "fr" to "French",
        "he" to "Hebrew",
        "hi" to "Hindi",
        "hr" to "Croatian",
        "hu" to "Hungarian",
        "id" to "Indonesian",
        "it" to "Italian",
        "ja" to "Japanese",
        "ka" to "Georgian",
        "ko" to "Korean",
        "ms" to "Malay",
        "nl" to "Dutch",
        "no" to "Norwegian",
        "pl" to "Polish",
        "pt" to "Portuguese",
        "ro" to "Romanian",
        "ru" to "Russian",
        "sr" to "Serbian",
        "sv" to "Swedish",
        "th" to "Thai",
        "tr" to "Turkish",
        "uk" to "Ukrainian",
        "vi" to "Vietnamese",
        "zh" to "Chinese",
    )
