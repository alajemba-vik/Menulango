package com.menulango.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The Plus card's small performance, played each time Settings opens: the title is written out
 * as if by hand, then a pen line runs from it down to "See Plus" and loops round it, the way a
 * friend would circle something on a menu. With reduce-motion everything is simply there.
 */
@Stable
internal class PlusCardInk {
    val writing = Animatable(0f)
    val drawing = Animatable(0f)
    val sheen = Animatable(-1f)
    var titleEnd by mutableStateOf<Offset?>(null)
    var button by mutableStateOf<Rect?>(null)
}

@Composable
internal fun rememberPlusCardInk(
    circleTarget: Boolean,
    perform: Boolean,
): PlusCardInk {
    val ink = remember { PlusCardInk() }
    val reduceMotion = Paper.reduceMotion
    LaunchedEffect(reduceMotion, circleTarget, perform) {
        if (reduceMotion || !perform) {
            ink.writing.snapTo(1f)
            ink.drawing.snapTo(if (circleTarget) 1f else 0f)
            return@LaunchedEffect
        }
        ink.writing.snapTo(0f)
        ink.drawing.snapTo(0f)
        delay(WRITE_DELAY_MS)
        ink.writing.animateTo(1f, tween(WRITE_MS, easing = LinearEasing))
        if (circleTarget) ink.drawing.animateTo(1f, tween(DRAW_MS, easing = FastOutSlowInEasing))
    }
    // Now and then a slow band of light crosses the card, like a lamp caught in felt. Never
    // more than once every minute and a half, so it stays a surprise.
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        delay(FIRST_SHEEN_MS)
        while (true) {
            ink.sheen.snapTo(-1f)
            ink.sheen.animateTo(2f, tween(SHEEN_MS, easing = FastOutSlowInEasing))
            delay(Random.nextLong(SHEEN_GAP_MIN_MS, SHEEN_GAP_MAX_MS))
        }
    }
    return ink
}

/** Text that appears line by line, left to right, behind a moving pen nib. */
@Composable
internal fun HandwrittenText(
    text: String,
    style: TextStyle,
    color: Color,
    nib: Color,
    ink: PlusCardInk,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        color = color,
        onTextLayout = { result ->
            layout = result
            val last = result.lineCount - 1
            ink.titleEnd = Offset(result.getLineRight(last), result.getLineBottom(last))
        },
        modifier =
            modifier.drawWithContent {
                val result = layout
                val progress = ink.writing.value
                if (result == null || progress >= 1f) {
                    drawContent()
                    return@drawWithContent
                }
                val widths = (0 until result.lineCount).map { result.getLineRight(it) - result.getLineLeft(it) }
                var remaining = widths.sum() * progress
                for (line in 0 until result.lineCount) {
                    val left = result.getLineLeft(line)
                    val shown = remaining.coerceIn(0f, widths[line])
                    remaining -= shown
                    if (shown <= 0f) break
                    val top = result.getLineTop(line)
                    val bottom = result.getLineBottom(line)
                    clipRect(left = left, top = top, right = left + shown, bottom = bottom) {
                        this@drawWithContent.drawContent()
                    }
                    if (remaining <= 0f) {
                        drawCircle(nib, radius = 2.5.dp.toPx(), center = Offset(left + shown, bottom - 6.dp.toPx()))
                    }
                }
            },
    )
}

/**
 * Draws the pen line and the loop round the button over the whole card, and the passing sheen.
 * Coordinates are the card's own: [PlusCardInk.titleEnd] and [PlusCardInk.button] are reported in them.
 */
internal fun Modifier.plusCardInk(
    ink: PlusCardInk,
    pen: Color,
    titleOrigin: () -> Offset,
): Modifier =
    drawWithContent {
        drawContent()
        val sheen = ink.sheen.value
        if (sheen > -1f && sheen < 2f) {
            val x = size.width * sheen
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                    start = Offset(x - size.width * 0.35f, 0f),
                    end = Offset(x, size.height),
                ),
            )
        }
        val drawing = ink.drawing.value
        val end = ink.titleEnd
        val button = ink.button
        if (drawing <= 0f || end == null || button == null) return@drawWithContent
        val origin = titleOrigin()
        val start = Offset(origin.x + end.x + 6.dp.toPx(), origin.y + end.y - 10.dp.toPx())
        val loop = button.inflate(7.dp.toPx())
        val path =
            Path().apply {
                moveTo(start.x, start.y)
                // A flourish out to the card's right margin, down the empty lane between the text
                // and the stitching (so it never crosses a word), then in to the button...
                val lane = size.width - LANE_FROM_EDGE.toPx()
                cubicTo(
                    start.x + 12.dp.toPx(),
                    start.y + 2.dp.toPx(),
                    lane,
                    start.y + 4.dp.toPx(),
                    lane,
                    start.y + 22.dp.toPx(),
                )
                cubicTo(
                    lane + 3.dp.toPx(),
                    start.y + (loop.top - start.y) * 0.45f,
                    lane - 3.dp.toPx(),
                    loop.top - 30.dp.toPx(),
                    lane,
                    loop.top - 16.dp.toPx(),
                )
                cubicTo(
                    lane,
                    loop.top - 4.dp.toPx(),
                    loop.right + 10.dp.toPx(),
                    loop.top - 2.dp.toPx(),
                    loop.right - 10.dp.toPx(),
                    loop.top,
                )
                // ...then once round it, a hand's loop that doesn't quite close where it began.
                cubicTo(
                    loop.right - loop.width * 0.4f,
                    loop.top - 6.dp.toPx(),
                    loop.left,
                    loop.top,
                    loop.left,
                    loop.center.y,
                )
                cubicTo(
                    loop.left,
                    loop.bottom + 4.dp.toPx(),
                    loop.right,
                    loop.bottom + 2.dp.toPx(),
                    loop.right,
                    loop.center.y,
                )
                cubicTo(
                    loop.right,
                    loop.top + 2.dp.toPx(),
                    loop.right - loop.width * 0.25f,
                    loop.top - 3.dp.toPx(),
                    loop.center.x + loop.width * 0.1f,
                    loop.top - 4.dp.toPx(),
                )
            }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * drawing, partial, true)
        drawPath(
            partial,
            pen,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

private const val WRITE_DELAY_MS = 350L
private const val WRITE_MS = 1300
private const val DRAW_MS = 1100
private const val FIRST_SHEEN_MS = 6_000L
private const val SHEEN_MS = 1_600
private const val SHEEN_GAP_MIN_MS = 90_000L
private const val SHEEN_GAP_MAX_MS = 180_000L

/** The pen runs down this far in from the card's right edge: inside the stitching, outside the text. */
private val LANE_FROM_EDGE = 14.dp
