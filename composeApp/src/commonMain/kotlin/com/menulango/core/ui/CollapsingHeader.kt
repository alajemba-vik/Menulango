package com.menulango.core.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * A header that gives its height to the list below it: scrolling up first shrinks the header down
 * to [collapsedPx], then scrolls the list; scrolling down past the top of the list grows it back.
 *
 * Attach [connection] with `Modifier.nestedScroll` on an ancestor of the scrolling list, report the
 * header's natural height in [expandedPx], and lay the header out at [heightPx].
 */
@Stable
internal class CollapsingHeader(
    private val collapsedPx: Float,
) {
    /** The header's full, natural height. */
    var expandedPx by mutableFloatStateOf(0f)

    /** How far the header has shrunk, from 0 (full) to [range] (collapsed). */
    var collapsedBy by mutableFloatStateOf(0f)
        private set

    private val range: Float get() = (expandedPx - collapsedPx).coerceAtLeast(0f)

    /** 0 when the header is full, 1 when it is a compact bar. */
    val progress: Float get() = if (range == 0f) 0f else (collapsedBy / range).coerceIn(0f, 1f)

    val heightPx: Float get() = expandedPx - collapsedBy.coerceAtMost(range)

    val connection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y >= 0f) return Offset.Zero
                val next = (collapsedBy - available.y).coerceAtMost(range)
                val used = next - collapsedBy
                collapsedBy = next
                return Offset(0f, -used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val next = (collapsedBy - available.y).coerceAtLeast(0f)
                val used = collapsedBy - next
                collapsedBy = next
                return Offset(0f, used)
            }
        }
}
