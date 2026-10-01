package com.menulango.platform

import androidx.compose.runtime.Composable

/**
 * Returns an action that makes sure the app may use Bluetooth and nearby Wi-Fi, asking only now,
 * when the diner hosts or joins a table, then runs [onReady]; [onDenied] if they say no. On iPhone
 * the system asks by itself the first time the table starts, so it simply runs [onReady].
 */
@Composable
internal expect fun rememberNearbyAccess(
    onReady: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit
