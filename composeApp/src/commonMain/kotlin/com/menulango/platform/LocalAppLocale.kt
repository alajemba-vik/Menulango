package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * Switches the language Compose resources are read in, while the app runs, following JetBrains'
 * recipe for Compose Multiplatform: Android swaps the configuration's locale, iOS sets the app's
 * AppleLanguages. A null tag means the phone's own language (or its per-app language setting).
 */
internal expect object LocalAppLocale {
    @Composable
    infix fun provides(languageTag: String?): ProvidedValue<*>
}
