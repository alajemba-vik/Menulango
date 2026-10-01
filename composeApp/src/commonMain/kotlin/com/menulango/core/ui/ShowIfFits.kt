package com.menulango.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Fills the space it is given with [content] only if all of it fits; otherwise shows nothing.
 * For extras that are welcome on a tall screen but must never be cut off or crowd a small one.
 */
@Composable
internal fun ShowIfFits(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(loose) }
        val height = placeables.sumOf { it.height }
        val fits = constraints.hasBoundedHeight && height <= constraints.maxHeight
        layout(constraints.maxWidth, constraints.minHeight) {
            if (!fits) return@layout
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}
