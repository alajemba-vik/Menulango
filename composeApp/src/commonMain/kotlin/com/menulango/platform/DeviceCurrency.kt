package com.menulango.platform

/** The ISO 4217 code of the phone's own region's currency ("EUR" in France), or null if unknown. */
internal expect fun deviceCurrencyCode(): String?

/** The phone's own language as a BCP-47 tag ("fr-FR"), for explaining menus when the app follows the phone. */
internal expect fun deviceLanguageTag(): String
