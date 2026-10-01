package com.menulango.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as BitmapCanvas

/**
 * Text that changes by coming apart into felt fibres and gathering into the new words, as if the
 * cloth behind it rearranged itself. At rest it is plain, crisp text; the fibres exist only while
 * it changes. The first text is simply shown, and with [animate] off every change is instant.
 *
 * Callers own the semantics: this draws on a canvas, so it says nothing to a screen reader.
 */
@Composable
internal fun FiberText(
    text: String,
    style: TextStyle,
    color: Color,
    fiber: Color,
    /** The felt's own fibre colour: what the fibres fade to at their loosest. */
    loose: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val centred = style.copy(color = color, textAlign = TextAlign.Center)
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        var resting by remember { mutableStateOf(text) }
        var leaving by remember { mutableStateOf<String?>(null) }
        var move by remember { mutableStateOf<FiberMove?>(null) }
        val progress = remember { Animatable(1f) }
        val oldLayout = measurer.measure(leaving ?: resting, centred, constraints = Constraints.fixedWidth(width))
        val newLayout = measurer.measure(resting, centred, constraints = Constraints.fixedWidth(width))
        val height = max(oldLayout.size.height, newLayout.size.height)

        LaunchedEffect(text, width) {
            if (text == resting) return@LaunchedEffect
            if (!animate || width == 0) {
                resting = text
                return@LaunchedEffect
            }
            val before = measurer.measure(resting, centred, constraints = Constraints.fixedWidth(width))
            val after = measurer.measure(text, centred, constraints = Constraints.fixedWidth(width))
            val room = max(before.size.height, after.size.height)
            val random = Random(text.hashCode())
            move =
                FiberMove(
                    from = sampleGlyphs(before, width, room, density, direction, random),
                    to = sampleGlyphs(after, width, room, density, direction, random),
                    random = random,
                    width = width.toFloat(),
                    height = room.toFloat(),
                    reach = with(density) { FIBRE_REACH.toPx() },
                )
            leaving = resting
            resting = text
            progress.snapTo(0f)
            try {
                progress.animateTo(1f, tween(CHANGE_MS, easing = LinearEasing))
            } finally {
                // Also when cut short (a touch stops the page): never leave fibres on screen.
                leaving = null
                move = null
            }
        }

        Canvas(Modifier.fillMaxWidth().height(with(density) { height.toDp() })) {
            val t = progress.value
            val fibres = move
            val oldText = leaving
            // Old words fade as the fibres lift off them; new ones sharpen as the last fibres land.
            if (oldText != null) {
                val fade = (1f - t / OLD_FADE).coerceIn(0f, 1f)
                if (fade > 0f) drawText(oldLayout, topLeft = centre(oldLayout, size.height), alpha = fade)
            }
            val arrive = if (fibres == null) 1f else ((t - NEW_FROM) / (1f - NEW_FROM)).coerceIn(0f, 1f)
            if (arrive > 0f) drawText(newLayout, topLeft = centre(newLayout, size.height), alpha = arrive)
            if (fibres != null) drawFibres(fibres, t, fiber, loose)
        }
    }
}

/**
 * One change. Every fibre travels a single curve from a point on the old words to a point on the
 * new ones, bent through a smooth flow field, so neighbouring fibres drift together like cloth in
 * a breeze and the loose cloud takes the shape of the words, never of a box.
 */
private class FiberMove(
    val from: FloatArray,
    val to: FloatArray,
    random: Random,
    width: Float,
    height: Float,
    reach: Float,
) {
    /** The curve's control point: where the fibre is loosest, halfway through. */
    val bend = FloatArray(FIBRES * 2)
    val delay = FloatArray(FIBRES)
    val angle = FloatArray(FIBRES)
    val turn = FloatArray(FIBRES)

    init {
        // A different breeze each change, the same breeze for every fibre within it.
        val phaseX = random.nextFloat() * 2f * PI.toFloat()
        val phaseY = random.nextFloat() * 2f * PI.toFloat()
        for (i in 0 until FIBRES) {
            val mx = (from[i * 2] + to[i * 2]) / 2f
            val my = (from[i * 2 + 1] + to[i * 2 + 1]) / 2f
            val flow =
                sin(mx / width * FLOW_WAVES + phaseX) * PI.toFloat() +
                    cos(my / height * FLOW_WAVES + phaseY) * PI.toFloat() / 2f
            val distance = reach * (0.45f + random.nextFloat() * 0.55f)
            bend[i * 2] = mx + cos(flow) * distance
            // A little lift, as loose fibres rise before they settle.
            bend[i * 2 + 1] = my + sin(flow) * distance * 0.8f - reach * 0.2f
            delay[i] = random.nextFloat() * STAGGER
            angle[i] = random.nextFloat() * PI.toFloat()
            turn[i] = (random.nextFloat() - 0.5f) * PI.toFloat()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFibres(
    move: FiberMove,
    t: Float,
    deep: Color,
    pale: Color,
) {
    val length = FIBRE_LENGTH.toPx()
    val stroke = FIBRE_WIDTH.toPx()
    for (i in 0 until FIBRES) {
        val s = ease(((t - move.delay[i]) / (1f - STAGGER)).coerceIn(0f, 1f))
        // Nothing left to draw once a fibre has landed: the crisp new text takes over.
        if (s >= 1f) continue
        val u = 1f - s
        val fx = move.from[i * 2]
        val fy = move.from[i * 2 + 1]
        val cx = move.bend[i * 2]
        val cy = move.bend[i * 2 + 1]
        val tx = move.to[i * 2]
        val ty = move.to[i * 2 + 1]
        val x = u * u * fx + 2f * u * s * cx + s * s * tx
        val y = u * u * fy + 2f * u * s * cy + s * s * ty
        // Deep as the letters at both ends of the journey; in the middle the colour itself turns
        // to the felt's own, so loose fibres melt into the cloth instead of reading as dark specks.
        val depth = abs(2f * s - 1f)
        val deepness = depth * depth * depth
        val colour = lerp(pale, deep, deepness)
        val alpha = FIBRE_PALE + (1f - FIBRE_PALE) * deepness
        val a = move.angle[i] + move.turn[i] * s
        val dx = cos(a) * length / 2f
        val dy = sin(a) * length / 2f
        drawLine(colour, Offset(x - dx, y - dy), Offset(x + dx, y + dy), stroke, StrokeCap.Round, alpha = alpha)
    }
}

/**
 * Where the letters are: the text drawn once, off screen, and [FIBRES] points picked from its
 * inked pixels. Repeats points when the words are small, so every change moves the same fibres.
 */
private fun sampleGlyphs(
    layout: TextLayoutResult,
    width: Int,
    height: Int,
    density: Density,
    direction: LayoutDirection,
    random: Random,
): FloatArray {
    val points = FloatArray(FIBRES * 2)
    if (width <= 0 || height <= 0) return points
    val bitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, direction, BitmapCanvas(bitmap), Size(width.toFloat(), height.toFloat())) {
        drawText(layout, color = Color.Black, topLeft = centre(layout, height.toFloat()))
    }
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels)
    val inked = ArrayList<Int>()
    for (y in 0 until height step SAMPLE_STEP) {
        for (x in 0 until width step SAMPLE_STEP) {
            if ((pixels[y * width + x] ushr 24) and 0xFF > INK_ALPHA) inked += y * width + x
        }
    }
    if (inked.isEmpty()) return points
    for (i in 0 until FIBRES) {
        val at = inked[random.nextInt(inked.size)]
        points[i * 2] = (at % width).toFloat()
        points[i * 2 + 1] = (at / width).toFloat()
    }
    return points
}

private fun centre(
    layout: TextLayoutResult,
    height: Float,
) = Offset(0f, (height - layout.size.height) / 2f)

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
) = a + (b - a) * t

private fun ease(t: Float) = t * t * (3f - 2f * t)

private const val FIBRES = 1_100
private val FIBRE_LENGTH = 2.6.dp
private val FIBRE_WIDTH = 0.7.dp

/** How far a fibre strays from its letters at its loosest. */
private val FIBRE_REACH = 96.dp
private const val FIBRE_PALE = 0.35f
private const val FLOW_WAVES = 5f
private const val SAMPLE_STEP = 2
private const val INK_ALPHA = 110

/** About 1.3 s to come apart and 1.6 s to gather: unhurried, then the words sit still to be read. */
private const val CHANGE_MS = 2_900
private const val STAGGER = 0.18f
private const val OLD_FADE = 0.16f
private const val NEW_FROM = 0.8f
