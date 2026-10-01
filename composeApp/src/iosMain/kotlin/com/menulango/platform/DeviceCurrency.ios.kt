package com.menulango.platform

import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

internal actual fun deviceCurrencyCode(): String? = NSLocale.currentLocale.currencyCode
