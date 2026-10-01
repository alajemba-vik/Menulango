package com.menulango.feature.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.DishPlate
import com.menulango.resources.Res
import com.menulango.resources.table_arrived
import com.menulango.resources.table_sent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Picks travelling between phones, shown as they go. On a guest's phone the dish's plate lifts
 * off and flies up and away; on the host's it drops in from above and settles with a small bounce
 * and a tap of haptics. Each comes with one line saying what happened. Reduce motion keeps the line.
 */
@Composable
internal fun TableActivityOverlay(modifier: Modifier = Modifier) {
    val session = koinInject<TableSession>()
    val showing = remember { mutableStateListOf<Pair<Long, TableActivity>>() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(session) {
        var next = 0L
        session.activities.collect { activity ->
            val id = next++
            showing += id to activity
            if (activity is TableActivity.Arrived) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            launch {
                delay(SHOW_MS)
                showing.removeAll { it.first == id }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val travel = with(LocalDensity.current) { (maxHeight * TRAVEL_FRACTION).toPx() }
        showing.forEach { (id, activity) ->
            key(id) { Flight(activity, travel) }
        }
        showing.lastOrNull()?.let { (id, activity) ->
            key("line-$id") { Line(activity, Modifier.align(Alignment.TopCenter)) }
        }
    }
}

@Composable
private fun Flight(
    activity: TableActivity,
    travel: Float,
) {
    if (Paper.reduceMotion) return
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        when (activity) {
            is TableActivity.Sent -> {
                progress.animateTo(1f, tween(SENT_MS, easing = FastOutLinearInEasing))
            }

            is TableActivity.Arrived -> {
                progress.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
                delay(ARRIVED_HOLD_MS)
                fade.animateTo(0f, tween(FADE_MS))
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        DishPlate(
            activity.emoji,
            size = PLATE,
            modifier =
                Modifier
                    .padding(bottom = START_ABOVE_BOTTOM)
                    .graphicsLayer {
                        val p = progress.value
                        when (activity) {
                            // Up and away, easing out of sight: gone to the other phone.
                            is TableActivity.Sent -> {
                                translationY = -travel * p
                                translationX = SWAY * (p - p * p) * size.width
                                val s = 1f - 0.45f * p
                                scaleX = s
                                scaleY = s
                                alpha = 1f - p * p
                                rotationZ = -12f * p
                            }

                            // Down from above, landing where the picks bar is.
                            is TableActivity.Arrived -> {
                                translationY = -travel * (1f - p)
                                scaleX = 0.7f + 0.3f * p
                                scaleY = 0.7f + 0.3f * p
                                alpha = fade.value * p.coerceIn(0f, 1f)
                            }
                        }
                    },
        )
    }
}

@Composable
private fun Line(
    activity: TableActivity,
    modifier: Modifier,
) {
    val colors = Paper.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(FADE_MS))
        delay(LINE_HOLD_MS)
        appear.animateTo(0f, tween(FADE_MS))
    }
    val text =
        when (activity) {
            is TableActivity.Sent -> stringResource(Res.string.table_sent, activity.hostName)
            is TableActivity.Arrived -> stringResource(Res.string.table_arrived, activity.guestName, activity.dish)
        }
    Text(
        text,
        style = Paper.type.button,
        color = colors.paper,
        fontSize = 15.sp,
        modifier =
            modifier
                .statusBarsPadding()
                .padding(top = Space.sm)
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * -12.dp.toPx()
                }.shadow(Elevation.floating, Shapes.pill, clip = false)
                .background(colors.ink, Shapes.pill)
                .padding(horizontal = Space.md, vertical = Space.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private val PLATE = 56.dp
private val START_ABOVE_BOTTOM = 96.dp
private const val TRAVEL_FRACTION = 0.6f
private const val SWAY = 0.6f
private const val SENT_MS = 750
private const val ARRIVED_HOLD_MS = 350L
private const val FADE_MS = 220
private const val LINE_HOLD_MS = 1_600L
private const val SHOW_MS = 2_200L
