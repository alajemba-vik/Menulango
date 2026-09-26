package com.menulango.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Space

/**
 * The top of a scrolling page, driven by the scroll itself, as on the menu page and in iOS large
 * titles: as the big title scrolls up under the bar, the bar's paper fills in and the small title
 * rises into it, frame by frame with the finger. Nothing slides in on its own.
 *
 * @param progress 0 with the big title fully shown, 1 once it has scrolled under the bar.
 */
@Composable
internal fun ScrollTitleBar(
    title: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth()) {
        // Always paper behind the status bar, so the clock never sits on a card.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).felt(colors.paper))
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .graphicsLayer { alpha = progress() }
                .felt(colors.paper),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = Paper.type.dishName,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .graphicsLayer {
                            val shown = ((progress() - 0.5f) / 0.5f).coerceIn(0f, 1f)
                            alpha = shown
                            translationY = (1f - shown) * 10.dp.toPx()
                        }
                        // The big title stays the heading for screen readers; this is its echo.
                        .clearAndSetSemantics { },
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(Space.hairline)
                    .background(colors.rule),
            )
        }
    }
}

/** How far the big title fades as the page scrolls, matching [ScrollTitleBar]'s progress. */
internal fun Modifier.bigTitleFade(progress: () -> Float): Modifier =
    graphicsLayer { alpha = (1f - progress() * 1.4f).coerceIn(0f, 1f) }

private val BAR_HEIGHT = 44.dp
