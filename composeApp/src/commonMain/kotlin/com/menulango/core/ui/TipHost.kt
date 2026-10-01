package com.menulango.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.clip
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
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.data.tips.Tip
import com.menulango.resources.Res
import com.menulango.resources.tip_got_it
import com.menulango.resources.tip_hide_dish
import com.menulango.resources.tip_picks
import com.menulango.resources.tip_start_on_camera
import com.menulango.resources.tip_start_on_camera_here
import com.menulango.resources.tip_stop
import com.menulango.resources.tip_tester_plus
import com.menulango.resources.tip_tester_plus_here
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Where each tip's target sits on screen, reported by [tipTarget]. */
@Stable
internal class TipAnchors(
    val onTouched: (Tip) -> Unit,
) {
    val bounds = mutableStateMapOf<Tip, Rect>()

    /** Full-screen layers open over the screen; their targets underneath are hidden. */
    var covers by mutableStateOf(0)
}

/**
 * Called by a layer that covers the whole screen (the waiter view, a dish sheet): while it is
 * open no tip appears, since the targets it hides are still laid out underneath it.
 */
@Composable
internal fun CoverTips() {
    val anchors = LocalTipAnchors.current ?: return
    DisposableEffect(anchors) {
        anchors.covers += 1
        onDispose { anchors.covers -= 1 }
    }
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
 * Hosts the app's embedded onboarding: a sticky note that springs up beside whichever unread tip's
 * target is on screen, one at a time, with a soft pulse round the thing it means. No scrim, no
 * carousel: the app stays usable, and the note goes as soon as it is tapped or its target is.
 */
@Composable
internal fun TipHost(
    seen: Set<Tip>,
    enabled: Boolean,
    onSeen: (Tip) -> Unit,
    onStopTips: () -> Unit,
    content: @Composable () -> Unit,
) {
    val anchors = remember(onSeen) { TipAnchors(onSeen) }
    CompositionLocalProvider(LocalTipAnchors provides anchors) {
        Box(Modifier.fillMaxSize()) {
            content()
            val next =
                if (!enabled || anchors.covers > 0) {
                    null
                } else {
                    Tip.entries.firstOrNull { it.isNote && it !in seen && anchors.bounds[it] != null }
                }
            var shown by remember { mutableStateOf<Tip?>(null) }
            // Let the screen settle before a note appears, and never two notes in quick
            // succession: after one is read, the next waits out a cooldown.
            var lastRead by remember { mutableStateOf<TimeMark?>(null) }
            LaunchedEffect(next) {
                shown = null
                if (next != null) {
                    val sinceLast = lastRead?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
                    delay(maxOf(TIP_DELAY_MS, TIP_COOLDOWN_MS - sinceLast))
                    shown = next
                }
            }
            val tip = shown
            val target = tip?.let { anchors.bounds[it] }
            if (tip != null && target != null) {
                TipOverlay(
                    tip,
                    target,
                    onDismiss = {
                        lastRead = TimeSource.Monotonic.markNow()
                        onSeen(tip)
                    },
                    onStop = onStopTips,
                )
            }
        }
    }
}

@Composable
private fun TipOverlay(
    tip: Tip,
    target: Rect,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
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
    val entrance = remember(tip) { Animatable(0f) }
    LaunchedEffect(tip) {
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val gap = with(density) { ARROW_GAP.toPx() }
        val margin = with(density) { Space.gutter.toPx() }
        // Notes live between the status bar (and any camera cutout) and the navigation bar, never
        // under them. If the target scrolls out of that band, the note steps aside until it's back.
        val safe = WindowInsets.safeDrawing
        val safeTop = safe.getTop(density) + margin / 2f
        val safeBottom = screenH - safe.getBottom(density) - margin / 2f
        val targetInView = target.bottom > safeTop && target.top < safeBottom
        val presence by animateFloatAsState(
            if (targetInView) 1f else 0f,
            tween(if (reduceMotion) 0 else Motion.QUICK_MS),
            label = "tip-presence",
        )
        if (presence == 0f && !targetInView) return@BoxWithConstraints
        val preferBelow = target.center.y < screenH / 2f

        // The pulse: a ring that swells out of the target and fades, like a finger's ripple.
        val grow = with(density) { (RING_GROW * (if (reduceMotion) 0.5f else ring)).toPx() }
        val inset = with(density) { 6.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = presence }) {
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
                    onStop = onStop,
                    modifier =
                        Modifier.graphicsLayer {
                            val e = entrance.value
                            scaleX = 0.7f + 0.3f * e
                            scaleY = 0.7f + 0.3f * e
                            alpha = e.coerceIn(0f, 1f) * presence
                            // Pinned at a slight angle, like a note on a board; it never jiggles.
                            rotationZ = if (reduceMotion) 0f else NOTE_TILT_DEGREES
                            transformOrigin = TransformOrigin(0.5f, if (preferBelow) 0f else 1f)
                        },
                )
            },
        ) { measurables, constraints ->
            val maxW = (screenW - 2 * margin).roundToInt().coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(maxWidth = maxW))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = (target.center.x - placeable.width / 2f).coerceIn(margin, screenW - margin - placeable.width)
                val h = placeable.height
                val underY = target.bottom + gap
                val overY = target.top - gap - h
                val fitsUnder = underY + h <= safeBottom
                val fitsOver = overY >= safeTop
                // The preferred side if there's room, else the other, and always inside the band.
                val y =
                    when {
                        preferBelow && fitsUnder -> underY
                        !preferBelow && fitsOver -> overY
                        fitsUnder -> underY
                        fitsOver -> overY
                        else -> if (preferBelow) underY else overY
                    }.coerceIn(safeTop, (safeBottom - h).coerceAtLeast(safeTop))
                placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
            }
        }
    }
}

@Composable
private fun NoteBubble(
    tip: Tip,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) = TipNote(stringResource(tip.text()), onDismiss, modifier, onStop)

/**
 * A butter-yellow felt note, like the ones pinned to a café's letter board. Also used in place,
 * inside sheets, where a floating note would sit behind the sheet.
 */
@Composable
internal fun TipNote(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
) {
    val colors = Paper.colors
    val gotIt = stringResource(Res.string.tip_got_it)
    val stop = stringResource(Res.string.tip_stop)
    Row(
        modifier
            .widthIn(max = BUBBLE_MAX)
            .shadow(Elevation.floating, Shapes.card, clip = false)
            .clip(Shapes.card)
            .background(colors.food(FoodGroup.Sweet))
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
            // "Got it" closes this note; "No more tips" closes every note to come. Settings can
            // bring them back.
            Row(
                Modifier.padding(top = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(gotIt, style = Paper.type.label, color = colors.sealInk)
                onStop?.let {
                    Text(
                        stop,
                        style = Paper.type.label,
                        color = colors.inkMuted,
                        modifier =
                            Modifier
                                .clip(Shapes.chip)
                                .pressable(it)
                                .semantics { role = Role.Button }
                                // A full 44 dp tall tap target, though the words are small.
                                .heightIn(min = Space.touchTarget)
                                .wrapContentHeight(),
                    )
                }
            }
        }
    }
}

private fun Tip.text(): StringResource =
    when (this) {
        Tip.TesterSettings -> {
            Res.string.tip_tester_plus
        }

        Tip.TesterPlus -> {
            Res.string.tip_tester_plus_here
        }

        Tip.Picks -> {
            Res.string.tip_picks
        }

        Tip.StartOnCamera -> {
            Res.string.tip_start_on_camera
        }

        Tip.StartOnCameraHere -> {
            Res.string.tip_start_on_camera_here
        }

        Tip.HideDish -> {
            Res.string.tip_hide_dish
        }

        Tip.SwipeToDelete, Tip.Welcome, Tip.NoteHint, Tip.AddPageCard, Tip.GuestAdded, Tip.RestoreInfo -> {
            Res.string.tip_picks
        }
    }

private const val TIP_DELAY_MS = 700L
private const val PULSE_MS = 1400
private const val NOTE_TILT_DEGREES = -1.5f

/** At least this long between one note being read and the next appearing. */
private const val TIP_COOLDOWN_MS = 45_000L
private val ARROW_GAP = 14.dp
private val RING_GROW = 10.dp
private val BUBBLE_MAX = 320.dp
