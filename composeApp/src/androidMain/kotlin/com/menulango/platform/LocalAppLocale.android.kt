package com.menulango.platform

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

internal actual object LocalAppLocale {
    /** The locale the system gave the app, remembered before any override. */
    private var system: Locale? = null

    @Composable
    actual infix fun provides(languageTag: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val base = system ?: Locale.getDefault().also { system = it }
        val locale = languageTag?.let(Locale::forLanguageTag) ?: base
        Locale.setDefault(locale)
        val updated = Configuration(configuration).apply { setLocale(locale) }
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(updated, resources.displayMetrics)
        return LocalConfiguration provides updated
    }
}
