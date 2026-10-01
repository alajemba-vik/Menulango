package com.menulango.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space

/**
 * The app's one message bar, built like the other floating bars (the picks bar, Help me choose):
 * an ink pill, the same type, the message on the left and its action on the right in its own
 * column, so a long message wraps beside the action and never runs under it.
 */
@Composable
internal fun PaperSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(Elevation.floating, Shapes.pill, clip = false)
            .clip(Shapes.pill)
            .background(colors.ink)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Space.gutter, end = Space.xs, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = Paper.type.bodySmall,
            color = colors.paper,
            modifier = Modifier.weight(1f),
        )
        data.visuals.actionLabel?.let { action ->
            Spacer(Modifier.width(Space.sm))
            Text(
                action,
                style = Paper.type.button,
                color = colors.sealOnInk,
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(Shapes.pill)
                        .pressable(data::performAction)
                        .heightIn(min = Space.touchTarget)
                        .padding(horizontal = Space.md, vertical = Space.sm),
            )
        }
    }
}
