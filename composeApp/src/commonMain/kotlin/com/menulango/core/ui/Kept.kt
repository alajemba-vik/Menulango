package com.menulango.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * State that must outlive the app's tree being rebuilt when the language changes, such as how
 * far Settings is scrolled, so switching language leaves the diner exactly where they were.
 */
internal class KeptState {
    private val values = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(
        key: String,
        create: () -> T,
    ): T = values.getOrPut(key, create) as T
}

internal val LocalKeptState = staticCompositionLocalOf { KeptState() }

/** Like `remember`, but survives a language change. */
@Composable
internal fun <T : Any> rememberKept(
    key: String,
    create: () -> T,
): T {
    val kept = LocalKeptState.current
    return remember(kept, key) { kept.get(key, create) }
}
