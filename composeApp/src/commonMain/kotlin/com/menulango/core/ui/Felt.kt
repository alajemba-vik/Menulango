package com.menulango.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A felt background: [base] with a fine, soft scatter of fibres, like the felt letter boards cafés
 * write their menus on. Smooth cards sit on it the way letters sit on a board.
 *
 * The texture is pinned to the screen rather than to the element, so two felt surfaces that meet —
 * a pinned filter bar over the list, a header over the backdrop — continue into each other with no
 * seam, however they move.
 *
 * The fibres are not quite still, though: when anything scrolls, the whole felt drifts a little
 * the same way, slower than the content (see [FeltDrift]). Cards glide and the cloth under them
 * gives, so scrolling feels like sliding paper across a felt table rather than across glass.
 */
internal fun Modifier.felt(
    base: Color,
    shape: Shape? = null,
): Modifier =
    if (!SURFACE_TEXTURE) {
        // Clipped like the textured felt, so content inside keeps the shape's corners.
        (if (shape != null) clip(shape) else this).background(base)
    } else {
        feltTextured(base, shape)
    }

/**
 * The single switch for every textured surface in the app (felt, paper, weave, suede). Off, each
 * becomes a plain fill of the same colour: the flat look some prefer.
 */
internal const val SURFACE_TEXTURE: Boolean = true

private fun Modifier.feltTextured(
    base: Color,
    shape: Shape?,
): Modifier =
    composed {
        val density = LocalDensity.current
        // The fibres are the same on every colour, so they are drawn once, transparently, and laid
        // over a plain fill: a colour can then change every frame (a theme fading) at no cost.
        val tile = remember(density.density) { feltTile(density.density) }
        val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
        val origin = remember { FloatArray(2) }
        val drift = LocalFeltDrift.current
        val clipped = if (shape != null) clip(shape) else this
        clipped
            .onGloballyPositioned {
                val position = it.positionInRoot()
                origin[0] = position.x
                origin[1] = position.y
            }.drawBehind {
                // Draw the brush in screen space, then shift back: the fibres stay put as we move.
                // Reading the drift here, in the draw phase, means scrolling redraws the felt
                // without ever recomposing the screen.
                val dx = drift.x
                val dy = drift.y
                drawRect(base)
                translate(dx - origin[0], dy - origin[1]) {
                    drawRect(brush, topLeft = Offset(origin[0] - dx, origin[1] - dy), size = size)
                }
            }
    }

/**
 * How far the felt has been dragged by scrolling, shared by every felt surface so neighbouring
 * surfaces keep continuing into each other. Wrapped to one tile, so it never loses precision.
 */
@Stable
internal class FeltDrift {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set
    private var travelled = 0f

    fun drag(
        dx: Float,
        dy: Float,
        tilePx: Float,
    ): Boolean {
        x = (x + dx * DRIFT_RATIO).mod(tilePx)
        y = (y + dy * DRIFT_RATIO).mod(tilePx)
        travelled += abs(dx) + abs(dy)
        if (travelled < grainPx) return false
        travelled = 0f
        return true
    }

    internal var grainPx = 0f
}

internal val LocalFeltDrift = staticCompositionLocalOf { FeltDrift() }

/**
 * Makes everything scrolled inside [content] drag the felt. While a finger is on the glass, a
 * faint tick every [GRAIN_DP] reads as the grain of the cloth; flings glide silently, as a card
 * thrown across felt would. With reduce-motion the felt stays still and silent.
 */
@Composable
internal fun FeltSurface(content: @Composable () -> Unit) {
    val drift = remember { FeltDrift() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val reduceMotion = Paper.reduceMotion
    val connection =
        remember(haptics, density, reduceMotion) {
            drift.grainPx = GRAIN_DP * density
            feltConnection(drift, haptics, TILE_DP * density, reduceMotion)
        }
    CompositionLocalProvider(LocalFeltDrift provides drift) {
        Box(Modifier.nestedScroll(connection)) { content() }
    }
}

private fun feltConnection(
    drift: FeltDrift,
    haptics: HapticFeedback,
    tilePx: Float,
    reduceMotion: Boolean,
): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (reduceMotion || consumed == Offset.Zero) return Offset.Zero
            val grain = drift.drag(consumed.x, consumed.y, tilePx)
            if (grain && source == NestedScrollSource.UserInput) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            return Offset.Zero
        }
    }

/** One seamless square of felt fibres on transparency, drawn once per screen density. */
private fun feltTile(density: Float): ImageBitmap {
    val size = (TILE_DP * density).roundToInt()
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { strokeCap = StrokeCap.Round }

    // Real felt is clumpy: fibres bunch in some places and thin out in others. Most fibres gather
    // round random clumps of different sizes; the rest are scattered evenly between them. The tile
    // is large, so the eye never catches the repeat.
    val random = Random(FELT_SEED)
    val clumps =
        List(CLUMPS) {
            Triple(random.nextFloat() * size, random.nextFloat() * size, (14f + random.nextFloat() * 36f) * density)
        }
    repeat(FIBRES) {
        val (x, y) =
            if (random.nextFloat() < CLUMPED_SHARE) {
                val (cx, cy, spread) = clumps[random.nextInt(clumps.size)]
                val angle = random.nextFloat() * 2f * PI.toFloat()
                // An even spread across each clump: denser patches, never a dense core.
                val distance = kotlin.math.sqrt(random.nextFloat()) * spread
                (cx + cos(angle) * distance).mod(size.toFloat()) to (cy + sin(angle) * distance).mod(size.toFloat())
            } else {
                random.nextFloat() * size to random.nextFloat() * size
            }
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val length = (FIBRE_MIN_DP + random.nextFloat() * FIBRE_SPREAD_DP) * density
        val dx = cos(angle) * length / 2f
        val dy = sin(angle) * length / 2f
        paint.color =
            (if (random.nextFloat() < LIGHT_SHARE) Color.White else Color.Black)
                .copy(alpha = 0.03f + random.nextFloat() * 0.07f)
        paint.strokeWidth = (0.35f + random.nextFloat() * 0.5f) * density
        // A fibre crossing an edge is drawn again on the far side, so the tile wraps without a seam.
        for (ox in wrapOffsets(x, length, size)) {
            for (oy in wrapOffsets(y, length, size)) {
                canvas.drawLine(Offset(x + ox - dx, y + oy - dy), Offset(x + ox + dx, y + oy + dy), paint)
            }
        }
    }
    return bitmap
}

/** Shifts at which a fibre near an edge must also be drawn: none, or one tile the other way. */
private fun wrapOffsets(
    position: Float,
    reach: Float,
    size: Int,
): List<Float> =
    when {
        position < reach -> listOf(0f, size.toFloat())
        position > size - reach -> listOf(0f, -size.toFloat())
        else -> listOf(0f)
    }

private val TILE_DP = 224.dp.value

/** The felt moves at a third of the content's speed: enough to feel, never enough to watch. */
private const val DRIFT_RATIO = 0.35f

/** Scroll distance between two grain ticks while dragging. */
private const val GRAIN_DP = 120f

/** The same density of fibres as the original felt, over the larger tile. */
private const val FIBRES = 12000
private const val CLUMPS = 40
private const val CLUMPED_SHARE = 0.35f
private const val FIBRE_MIN_DP = 1.2f
private const val FIBRE_SPREAD_DP = 3.6f
private const val LIGHT_SHARE = 0.45f
private const val FELT_SEED = 7
