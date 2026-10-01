package com.menulango.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper

/**
 * Shows [content] growing in from nothing the first time it is composed, for a chip the diner
 * just added (a word to avoid, a person at the table). With [animate] false — chips already there
 * when the screen opened — it simply appears, so a screen never opens with everything bouncing.
 */
@Composable
internal fun PopIn(
    animate: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(!animate).apply { targetState = true } }
    val calm = Paper.reduceMotion
    AnimatedVisibility(
        visibleState = state,
        enter =
            if (calm) {
                fadeIn(tween(Motion.QUICK_MS))
            } else {
                fadeIn(tween(Motion.QUICK_MS)) +
                    expandHorizontally(
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                    )
            },
    ) { content() }
}
