package com.menulango.platform

import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal actual fun deviceCurrencyCode(): String? = NSLocale.currentLocale.currencyCode

// "en_US" or "zh-Hans_CN@calendar=…" becomes "en-US" or "zh-Hans-CN".
internal actual fun deviceLanguageTag(): String =
    NSLocale.currentLocale.localeIdentifier
        .substringBefore('@')
        .replace('_', '-')
