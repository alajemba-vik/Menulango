package com.menulango.core.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space

/** The one filled button style: a coral pill. Used for the single primary action on a screen. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val colors = Paper.colors
    Box(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .clip(Shapes.button)
                .suede(colors.seal)
                .alpha(if (enabled) 1f else 0.5f)
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
) {
    Box(
        modifier =
            modifier
                .size(Space.touchTarget)
                .clip(CircleShape)
                .background(background)
                .clickable(role = Role.Button, onClick = onClick)
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
