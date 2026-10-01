package com.menulango.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.menulango.core.design.Paper
import kotlinx.coroutines.launch

/**
 * Lands a newly added element where it belongs, starting from [origin] (a point in root
 * coordinates, such as the text field it was typed in): it drops out of there and settles into
 * place with a little bounce, so the diner sees their word become a chip rather than a chip just
 * appearing. Null [origin], or reduced motion, leaves it in place.
 */
@Composable
internal fun Modifier.flyInFrom(origin: Offset?): Modifier {
    if (origin == null || Paper.reduceMotion) return this
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    var ready by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return this
        .onGloballyPositioned { coordinates ->
            if (started) return@onGloballyPositioned
            started = true
            val here = coordinates.positionInRoot()
            scope.launch {
                dx.snapTo(origin.x - here.x)
                dy.snapTo(origin.y - here.y)
                // Hidden until it sits at the origin, so it never flashes in its final place first.
                ready = true
                launch { dx.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }
                dy.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
            }
        }.graphicsLayer {
            translationX = dx.value
            translationY = dy.value
            alpha = if (ready) 1f else 0f
        }
}
