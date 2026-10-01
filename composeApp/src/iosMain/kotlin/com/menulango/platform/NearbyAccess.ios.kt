package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal actual fun rememberNearbyAccess(
    onReady: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val ready = rememberUpdatedState(onReady)
    return { ready.value() }
}
