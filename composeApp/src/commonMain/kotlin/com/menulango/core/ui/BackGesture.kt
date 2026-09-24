package com.menulango.core.ui

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Handles the system back action: the Android back gesture and button, and the iOS edge swipe.
 * The innermost enabled handler wins, so an open sheet closes before its screen pops.
 */
@Composable
internal fun BackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
