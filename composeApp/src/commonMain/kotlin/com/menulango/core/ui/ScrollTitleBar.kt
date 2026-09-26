package com.menulango.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.Space

/**
 * The top of a scrolling page, as iOS and Material both do it: the big title scrolls away with
 * the content, and once it has gone a small title settles into a bar at the top, so you always
 * know where you are.
 *
 * The strip behind the status bar is always paper, so the clock and battery never sit on top of
 * a card scrolling underneath them.
 */
@Composable
internal fun ScrollTitleBar(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).felt(colors.paper))
        AnimatedVisibility(
            visible = collapsed,
            enter = fadeIn(tween(Motion.QUICK_MS)) + slideInVertically(tween(Motion.QUICK_MS)) { -it / 3 },
            exit = fadeOut(tween(Motion.QUICK_MS)) + slideOutVertically(tween(Motion.QUICK_MS)) { -it / 3 },
        ) {
            Column(Modifier.fillMaxWidth().felt(colors.paper)) {
                Box(Modifier.fillMaxWidth().height(BAR_HEIGHT), contentAlignment = Alignment.Center) {
                    // The big title's own serif, smaller: the same heading, folded away.
                    // The big title is still the page's heading for screen readers; this is its echo.
                    Text(
                        title,
                        style = Paper.type.dishName,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                Box(Modifier.fillMaxWidth().height(Space.hairline).background(colors.rule))
            }
        }
    }
}

private val BAR_HEIGHT = 44.dp
