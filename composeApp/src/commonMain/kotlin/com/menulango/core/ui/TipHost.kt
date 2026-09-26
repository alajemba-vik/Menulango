package com.menulango.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.FoodGroup
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.data.tips.Tip
import com.menulango.resources.Res
import com.menulango.resources.tip_add_dish
import com.menulango.resources.tip_got_it
import com.menulango.resources.tip_help_choose
import com.menulango.resources.tip_picks
import com.menulango.resources.tip_scan
import com.menulango.resources.tip_start_on_camera
import com.menulango.resources.tip_start_on_camera_here
import com.menulango.resources.tip_tap_dish
import com.menulango.resources.tip_tester_plus
import com.menulango.resources.tip_tester_plus_here
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Where each tip's target sits on screen, reported by [tipTarget]. */
@Stable
internal class TipAnchors(
    val onTouched: (Tip) -> Unit,
) {
    val bounds = mutableStateMapOf<Tip, Rect>()
}

internal val LocalTipAnchors = staticCompositionLocalOf<TipAnchors?> { null }

/**
 * Marks the thing a tip points at. While it is on screen, its tip may appear; touching it counts as
 * having read the tip, so nobody is told twice how to do what they have just done.
 */
@Composable
internal fun Modifier.tipTarget(tip: Tip): Modifier {
    val anchors = LocalTipAnchors.current ?: return this
    DisposableEffect(anchors, tip) { onDispose { anchors.bounds.remove(tip) } }
    return onGloballyPositioned {
        val rect = it.boundsInRoot()
        if (anchors.bounds[tip] != rect) anchors.bounds[tip] = rect
    }.pointerInput(anchors, tip) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            anchors.onTouched(tip)
        }
    }
}

/**
 * Hosts the app's embedded onboarding: a sticky note that wobbles up beside whichever unread tip's
 * target is on screen, one at a time, with a soft pulse round the thing it means. No scrim, no
 * carousel: the app stays usable, and the note goes as soon as it is tapped or its target is.
 */
@Composable
internal fun TipHost(
    seen: Set<Tip>,
    onSeen: (Tip) -> Unit,
    content: @Composable () -> Unit,
) {
    val anchors = remember(onSeen) { TipAnchors(onSeen) }
    CompositionLocalProvider(LocalTipAnchors provides anchors) {
        Box(Modifier.fillMaxSize()) {
            content()
            val next = Tip.entries.firstOrNull { it.isNote && it !in seen && anchors.bounds[it] != null }
            var shown by remember { mutableStateOf<Tip?>(null) }
            // Let the screen settle before a note appears, and leave a breath between notes.
            LaunchedEffect(next) {
                shown = null
                if (next != null) {
                    delay(TIP_DELAY_MS)
                    shown = next
                }
            }
            val tip = shown
            val target = tip?.let { anchors.bounds[it] }
            if (tip != null && target != null) {
                TipOverlay(tip, target, onDismiss = { onSeen(tip) })
            }
        }
    }
}

@Composable
private fun TipOverlay(
    tip: Tip,
    target: Rect,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    val density = LocalDensity.current
    val reduceMotion = Paper.reduceMotion
    val pulse = rememberInfiniteTransition(label = "tip-pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing)),
        label = "ring",
    )
    val wobble by pulse.animateFloat(
        initialValue = -WOBBLE_DEGREES,
        targetValue = WOBBLE_DEGREES,
        animationSpec = infiniteRepeatable(tween(WOBBLE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble",
    )
    val entrance = remember(tip) { Animatable(0f) }
    LaunchedEffect(tip) {
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val below = target.center.y < screenH / 2f
        val gap = with(density) { ARROW_GAP.toPx() }
        val margin = with(density) { Space.gutter.toPx() }

        // The pulse: a ring that swells out of the target and fades, like a finger's ripple.
        val grow = with(density) { (RING_GROW * (if (reduceMotion) 0.5f else ring)).toPx() }
        val inset = with(density) { 6.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val r = target.inflate(inset + grow)
            drawRoundRect(
                color = colors.seal.copy(alpha = if (reduceMotion) 0.6f else 0.7f * (1f - ring)),
                topLeft = r.topLeft,
                size = Size(r.width, r.height),
                cornerRadius = CornerRadius(r.minDimension / 2f),
                style = Stroke(width = with(density) { 2.dp.toPx() }),
            )
        }

        Layout(
            content = {
                NoteBubble(
                    tip = tip,
                    onDismiss = onDismiss,
                    modifier =
                        Modifier.graphicsLayer {
                            val e = entrance.value
                            scaleX = 0.7f + 0.3f * e
                            scaleY = 0.7f + 0.3f * e
                            alpha = e.coerceIn(0f, 1f)
                            rotationZ = if (reduceMotion) 0f else wobble
                            transformOrigin = TransformOrigin(0.5f, if (below) 0f else 1f)
                        },
                )
            },
        ) { measurables, constraints ->
            val maxW = (screenW - 2 * margin).roundToInt().coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(maxWidth = maxW))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = (target.center.x - placeable.width / 2f).coerceIn(margin, screenW - margin - placeable.width)
                val y = if (below) target.bottom + gap else target.top - gap - placeable.height
                placeable.place(IntOffset(x.roundToInt(), y.coerceIn(0f, screenH - placeable.height).roundToInt()))
            }
        }
    }
}

/** A butter-yellow felt note, like the ones pinned to a café's letter board. */
@Composable
private fun NoteBubble(
    tip: Tip,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val text = stringResource(tip.text())
    val gotIt = stringResource(Res.string.tip_got_it)
    Row(
        modifier
            .widthIn(max = BUBBLE_MAX)
            .shadow(Elevation.floating, Shapes.card, clip = false)
            .felt(colors.food(FoodGroup.Sweet), Shapes.card)
            .pressable(onDismiss, pressedScale = 0.95f)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                role = Role.Button
                contentDescription = text
                onClick(label = gotIt) {
                    onDismiss()
                    true
                }
            }.padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(text, style = Paper.type.bodySmall, color = colors.ink)
            Text(
                gotIt,
                style = Paper.type.label,
                color = colors.sealInk,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

private fun Tip.text(): StringResource =
    when (this) {
        Tip.TesterSettings -> Res.string.tip_tester_plus
        Tip.TesterPlus -> Res.string.tip_tester_plus_here
        Tip.Scan -> Res.string.tip_scan
        Tip.TapDish -> Res.string.tip_tap_dish
        Tip.AddDish -> Res.string.tip_add_dish
        Tip.HelpChoose -> Res.string.tip_help_choose
        Tip.Picks -> Res.string.tip_picks
        Tip.StartOnCamera -> Res.string.tip_start_on_camera
        Tip.StartOnCameraHere -> Res.string.tip_start_on_camera_here
        Tip.SwipeToDelete, Tip.Welcome, Tip.NoteHint, Tip.AddPageCard -> Res.string.tip_picks
    }

private const val TIP_DELAY_MS = 700L
private const val PULSE_MS = 1400
private const val WOBBLE_MS = 900
private const val WOBBLE_DEGREES = 1.8f
private val ARROW_GAP = 14.dp
private val RING_GROW = 10.dp
private val BUBBLE_MAX = 320.dp
