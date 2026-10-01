package com.menulango.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space

/** The one filled button style: an aubergine pill. Used for the single primary action on a screen. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    sheen: Boolean = false,
) {
    val colors = Paper.colors
    // The same passing light as the Plus card, for the one button that buys Plus: every few
    // seconds a soft band crosses it, then it rests. Never under reduce motion.
    val light =
        if (sheen && enabled && !Paper.reduceMotion) {
            rememberInfiniteTransition(label = "button-sheen").animateFloat(
                initialValue = -0.6f,
                targetValue = 1.6f,
                animationSpec =
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = SHEEN_CYCLE_MS
                            -0.6f at 0
                            -0.6f at SHEEN_REST_MS
                            1.6f at SHEEN_CYCLE_MS using FastOutSlowInEasing
                        },
                    ),
                label = "button-sheen-x",
            )
        } else {
            null
        }
    Box(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .clip(Shapes.button)
                .suede(colors.seal)
                .drawWithContent {
                    drawContent()
                    light?.value?.let { at ->
                        val x = size.width * at
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                                start = Offset(x - size.width * 0.25f, 0f),
                                end = Offset(x + size.width * 0.05f, size.height),
                            ),
                        )
                    }
                }.alpha(if (enabled) 1f else 0.5f)
                .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.gutter, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = colors.onSeal, strokeWidth = 1.5.dp)
        } else {
            Text(text, style = Paper.type.button, color = colors.onSeal, textAlign = TextAlign.Center)
        }
    }
}

/** A white pill for secondary actions. Never competes with [PrimaryButton]. */
@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = Paper.colors
    Row(
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .clip(Shapes.button)
                .border(BorderStroke(Space.hairline, colors.rule), Shapes.button)
                .background(colors.raised)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.gutter, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.related, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        Text(text, style = Paper.type.button, color = colors.ink)
    }
}

/** A text-only action, for things that must be findable but never loud ("Restore purchases"). */
@Composable
internal fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Paper.colors.inkMuted,
    singleLine: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = Space.touchTarget)
                .clip(Shapes.button)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Paper.type.button,
            color = color,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            softWrap = !singleLine,
        )
    }
}

/** A 48dp round icon target. [label] is read by TalkBack and VoiceOver. */
@Composable
internal fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Paper.colors.ink,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(Space.touchTarget)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Full-width primary action pinned to the lower half, where a thumb can reach it. */
@Composable
internal fun BottomAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    PrimaryButton(text = text, onClick = onClick, busy = busy, modifier = modifier.fillMaxWidth())
}

private const val SHEEN_CYCLE_MS = 4_600
private const val SHEEN_REST_MS = 3_000
