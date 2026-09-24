package com.menulango.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The most recent non-null [value]. Lets a sheet keep showing its dish while it animates away
 * after the selection has already been cleared.
 */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val holder = remember { Holder<T>() }
    if (value != null) holder.last = value
    return holder.last
}

private class Holder<T : Any> {
    var last: T? = null
}
