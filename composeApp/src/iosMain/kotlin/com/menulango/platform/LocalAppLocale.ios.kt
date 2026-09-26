package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

internal actual object LocalAppLocale {
    private const val APPLE_LANGUAGES = "AppleLanguages"

    /** The phone's language as the app started, before any choice made in MenuLango. */
    private val system: String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val current = staticCompositionLocalOf { system }

    @Composable
    actual infix fun provides(languageTag: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (languageTag == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(listOf(languageTag), forKey = APPLE_LANGUAGES)
        }
        return current provides (languageTag ?: system)
    }
}
