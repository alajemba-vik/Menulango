package com.menulango.core.ui

import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
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
 */
internal fun Modifier.felt(
    base: Color,
    shape: Shape? = null,
): Modifier =
    composed {
        val density = LocalDensity.current
        val tile = remember(base, density.density) { feltTile(base, density.density) }
        val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
        val origin = remember { FloatArray(2) }
        val clipped = if (shape != null) clip(shape) else this
        clipped
            .onGloballyPositioned {
                val position = it.positionInRoot()
                origin[0] = position.x
                origin[1] = position.y
            }.drawBehind {
                // Draw the brush in screen space, then shift back: the fibres stay put as we move.
                translate(-origin[0], -origin[1]) {
                    drawRect(brush, topLeft = Offset(origin[0], origin[1]), size = size)
                }
            }
    }

/** One seamless square of felt, drawn once per colour and screen density. */
private fun feltTile(
    base: Color,
    density: Float,
): ImageBitmap {
    val size = (TILE_DP * density).roundToInt()
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { strokeCap = StrokeCap.Round }
    paint.color = base
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

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

/** The same density of fibres as the original felt, over the larger tile. */
private const val FIBRES = 12000
private const val CLUMPS = 40
private const val CLUMPED_SHARE = 0.35f
private const val FIBRE_MIN_DP = 1.2f
private const val FIBRE_SPREAD_DP = 3.6f
private const val LIGHT_SHARE = 0.45f
private const val FELT_SEED = 7
