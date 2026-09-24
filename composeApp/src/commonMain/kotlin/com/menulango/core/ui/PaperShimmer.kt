package com.menulango.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space

/**
 * A soft, low-contrast sheen that drifts across placeholder shapes — the feel of light moving over
 * paper grain, not a bright loading sweep. With reduce-motion it is a still tone.
 */
@Composable
internal fun Modifier.paperShimmer(): Modifier {
    val colors = Paper.colors
    val base = colors.sunk
    val sheen = colors.rule
    if (Paper.reduceMotion) return background(base, Shapes.chip)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    return drawWithCache {
        val width = size.width
        onDrawBehind {
            val x = width * progress
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, sheen, base),
                    start = Offset(x - width * 0.6f, 0f),
                    end = Offset(x + width * 0.6f, size.height),
                ),
            )
        }
    }
}

/** Placeholder for one dish row while the menu is being read. */
@Composable
internal fun DishRowPlaceholder(
    modifier: Modifier = Modifier,
    nameWidth: Dp = 180.dp,
) {
    Column(modifier.padding(vertical = Space.md), verticalArrangement = Arrangement.spacedBy(Space.related)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.width(nameWidth).height(18.dp).paperShimmer())
            Box(Modifier.width(44.dp).height(14.dp).paperShimmer())
        }
        Box(Modifier.width(nameWidth * 0.6f).height(11.dp).paperShimmer())
    }
}
