package com.menulango.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Remembers which items have already been dealt onto the table and in what order.
 *
 * Items that appear together (a cached menu opening) are staggered one after another; items that
 * trickle in (a menu being read) each arrive at once, because the stream is already the stagger.
 * A row scrolled back into view is never dealt twice.
 */
internal class DealtItems {
    private val seen = mutableSetOf<Any>()
    private var lastDeal: TimeSource.Monotonic.ValueTimeMark? = null
    private var positionInDeal = 0

    /** The item's place in the current deal, or null if it is already on the table. */
    fun deal(key: Any): Int? {
        if (!seen.add(key)) return null
        val previous = lastDeal
        positionInDeal = if (previous != null && previous.elapsedNow() < BATCH_WINDOW) positionInDeal + 1 else 0
        lastDeal = TimeSource.Monotonic.markNow()
        return positionInDeal
    }

    private companion object {
        val BATCH_WINDOW = 32.milliseconds
    }
}

/**
 * Deals an item in: it fades up [Motion.riseDistance] and settles, [Motion.STAGGER_MS] after the
 * one before it. With reduce-motion the rise is dropped and only the fade remains.
 */
@Composable
internal fun Modifier.dealtIn(
    key: Any,
    dealt: DealtItems,
): Modifier {
    val order = remember(key) { dealt.deal(key) }
    val progress = remember(key) { Animatable(if (order == null) 1f else 0f) }
    val reduceMotion = Paper.reduceMotion
    LaunchedEffect(key) {
        if (order != null) {
            progress.animateTo(
                1f,
                tween(
                    durationMillis = Motion.ENTRANCE_MS,
                    delayMillis = order.coerceAtMost(Motion.STAGGER_CAP) * Motion.STAGGER_MS,
                    easing = Motion.settle,
                ),
            )
        }
    }
    val rise = with(LocalDensity.current) { Motion.riseDistance.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = if (reduceMotion) 0f else (1f - progress.value) * rise
    }
}
