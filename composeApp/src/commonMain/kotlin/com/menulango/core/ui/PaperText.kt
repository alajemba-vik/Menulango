package com.menulango.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.menulango.core.design.Paper
import com.menulango.core.design.Space

/** An 11sp uppercase label: WHAT IT IS, INGREDIENTS. Marked as a heading for screen readers. */
@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Paper.colors.inkFaint,
) {
    Text(
        text = text.uppercase(),
        style = Paper.type.label,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/** A printed rule. Paper separates with hairlines and tone, not borders and shadows. */
@Composable
internal fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = Paper.colors.rule,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Space.hairline)
            .background(color),
    )
}
