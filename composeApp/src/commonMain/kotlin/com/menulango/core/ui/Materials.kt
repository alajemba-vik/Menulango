package com.menulango.core.ui

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * The app's materials, dressed like one outfit: felt is the statement (see Felt.kt), and
 * everything laid on it is a quieter material that only shows up close. One loud texture per view.
 *
 *   paper  — cards: heavy cotton paper, a fine tooth and the odd fibre
 *   weave  — chips: woven labels, a close warp and weft
 *   suede  — coral buttons: a soft nap
 */

/** A card's surface: [base] with the fine tooth of heavy cotton paper. */
internal fun Modifier.paper(
    base: Color,
    shape: Shape = RectangleShape,
): Modifier = material(base, shape, Material.Paper)

/** A chip's surface: [base] woven, like a label stitched into a coat. */
internal fun Modifier.weave(
    base: Color,
    shape: Shape = RectangleShape,
): Modifier = material(base, shape, Material.Weave)

/** A button's surface: [base] with the soft nap of suede. */
internal fun Modifier.suede(
    base: Color,
    shape: Shape = RectangleShape,
): Modifier = material(base, shape, Material.Suede)

private enum class Material { Paper, Weave, Suede }

private fun Modifier.material(
    base: Color,
    shape: Shape,
    material: Material,
): Modifier =
    composed {
        val density = LocalDensity.current.density
        // Colour and grain are separate layers: the grain tile is the same on every colour, so a
        // colour can animate (a theme fading) without a single tile being redrawn.
        background(base, shape)
            .background(ShaderBrush(ImageShader(tile(material, density), TileMode.Repeated, TileMode.Repeated)), shape)
    }

/**
 * Tiles are drawn once per material and density and then shared: a menu of sixty cards
 * paints one bitmap sixty times, it does not draw sixty. Composition runs on one thread, so a
 * plain map is enough.
 */
private val tiles = HashMap<Pair<Material, Float>, ImageBitmap>()

private fun tile(
    material: Material,
    density: Float,
): ImageBitmap =
    tiles.getOrPut(material to density) {
        when (material) {
            Material.Paper -> paperTile(density)
            Material.Weave -> weaveTile(density)
            Material.Suede -> suedeTile(density)
        }
    }

private fun blank(
    sizeDp: Float,
    density: Float,
): Triple<ImageBitmap, Canvas, Int> {
    val size = (sizeDp * density).roundToInt()
    val bitmap = ImageBitmap(size, size)
    return Triple(bitmap, Canvas(bitmap), size)
}

/** A fine tooth of light and dark specks, and a few short paper fibres. */
private fun paperTile(density: Float): ImageBitmap {
    val (bitmap, canvas, size) = blank(PAPER_TILE_DP, density)
    val random = Random(PAPER_SEED)
    val paint = Paint()
    repeat(PAPER_SPECKS) {
        paint.color =
            (if (random.nextBoolean()) Color.White else Color.Black).copy(alpha = 0.015f + random.nextFloat() * 0.03f)
        canvas.drawCircle(
            Offset(random.nextFloat() * size, random.nextFloat() * size),
            (0.25f + random.nextFloat() * 0.35f) * density,
            paint,
        )
    }
    paint.strokeCap = StrokeCap.Round
    repeat(PAPER_FIBRES) {
        val x = random.nextFloat() * size
        val y = random.nextFloat() * size
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val half = (0.6f + random.nextFloat() * 1.6f) * density
        paint.color = Color.Black.copy(alpha = 0.03f + random.nextFloat() * 0.03f)
        paint.strokeWidth = 0.3f * density
        canvas.drawLine(
            Offset(x - cos(angle) * half, y - sin(angle) * half),
            Offset(
                x + cos(angle) * half,
                y + sin(angle) * half,
            ),
            paint,
        )
    }
    return bitmap
}

/** Warp and weft: close threads each way, each a shade apart, so the cloth reads up close only. */
private fun weaveTile(density: Float): ImageBitmap {
    val (bitmap, canvas, size) = blank(WEAVE_TILE_DP, density)
    val random = Random(WEAVE_SEED)
    val paint = Paint().apply { strokeWidth = 0.6f * density }
    val pitch = WEAVE_PITCH_DP * density
    var at = 0f
    while (at < size) {
        paint.color = Color.Black.copy(alpha = 0.03f + random.nextFloat() * 0.03f)
        canvas.drawLine(Offset(0f, at), Offset(size.toFloat(), at), paint)
        paint.color = Color.White.copy(alpha = 0.05f + random.nextFloat() * 0.05f)
        canvas.drawLine(Offset(at, 0f), Offset(at, size.toFloat()), paint)
        at += pitch
    }
    return bitmap
}

/** A soft nap: short strokes mostly one way, as suede lies when brushed. */
private fun suedeTile(density: Float): ImageBitmap {
    val (bitmap, canvas, size) = blank(SUEDE_TILE_DP, density)
    val random = Random(SUEDE_SEED)
    val paint = Paint().apply { strokeCap = StrokeCap.Round }
    repeat(SUEDE_STROKES) {
        val x = random.nextFloat() * size
        val y = random.nextFloat() * size
        val angle = (NAP_ANGLE + (random.nextFloat() - 0.5f) * 0.6f)
        val half = (0.8f + random.nextFloat() * 1.4f) * density
        paint.color =
            (
                if (random.nextFloat() <
                    0.5f
                ) {
                    Color.White
                } else {
                    Color.Black
                }
            ).copy(alpha = 0.03f + random.nextFloat() * 0.03f)
        paint.strokeWidth = (0.4f + random.nextFloat() * 0.4f) * density
        canvas.drawLine(
            Offset(x - cos(angle) * half, y - sin(angle) * half),
            Offset(
                x + cos(angle) * half,
                y + sin(angle) * half,
            ),
            paint,
        )
    }
    return bitmap
}

private const val PAPER_TILE_DP = 128f
private const val PAPER_SPECKS = 2600
private const val PAPER_FIBRES = 90
private const val PAPER_SEED = 11

private const val WEAVE_TILE_DP = 48f
private const val WEAVE_PITCH_DP = 1.5f
private const val WEAVE_SEED = 13

private const val SUEDE_TILE_DP = 96f
private const val SUEDE_STROKES = 1800
private const val SUEDE_SEED = 17
private const val NAP_ANGLE = 0.7f
