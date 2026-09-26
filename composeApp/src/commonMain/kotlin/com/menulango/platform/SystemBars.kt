package com.menulango.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the status bar's clock and battery readable on the app's own background. The app can be
 * set to light while the phone is dark (or the other way round), and the system would otherwise
 * draw white icons on cream paper.
 */
@Composable
internal expect fun SystemBarsFollow(darkTheme: Boolean)
