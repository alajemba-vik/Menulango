package com.menulango.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.menulango.core.design.Paper

/**
 * A clickable that squishes a little under the finger and springs back — what makes cards and
 * pills feel like objects rather than regions. With reduce-motion it is a plain click.
 */
@Composable
internal fun Modifier.pressable(
    onClick: () -> Unit,
    role: Role = Role.Button,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = Paper.reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
