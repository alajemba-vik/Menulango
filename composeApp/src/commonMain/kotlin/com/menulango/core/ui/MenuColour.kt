package com.menulango.core.ui

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The restaurant's own colour, read from the photo of its menu — usually the ink of its name or
 * headings — made dark enough to carry white text. Null when the menu has no colour worth taking
 * (black print on white card), so the app keeps its own aubergine.
 */
internal suspend fun menuColourOf(photo: ByteArray): Color? {
    val key = photo.contentHashCode()
    if (key in colours) return colours[key]
    val colour =
        withContext(Dispatchers.Default) {
            samplePixels(photo)?.let(::dominantHue)?.let(::headerSafe)
        }
    colours[key] = colour
    return colour
}

/** Colours already read this session, by photo, so a menu reopened is coloured at once. */
private val colours = HashMap<Int, Color?>()

/** The photo shrunk to a small grid of pixels: plenty to find a colour, cheap to read. */
private fun samplePixels(photo: ByteArray): IntArray? {
    val image =
        try {
            photo.decodeToImageBitmap()
        } catch (e: IllegalArgumentException) {
            return null
        }
    val small = ImageBitmap(SAMPLE, SAMPLE)
    Canvas(small).drawImageRect(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(SAMPLE, SAMPLE),
        paint = Paint(),
    )
    return IntArray(SAMPLE * SAMPLE).also { small.readPixels(it) }
}

/**
 * The most present vivid hue. Paper, ink and grey carry no brand, so pixels without enough
 * saturation, or too dark or too bright, are ignored; the rest vote by hue, weighted by how vivid
 * they are, and the winning hue's pixels are averaged.
 */
internal fun dominantHue(pixels: IntArray): Color? {
    val weights = DoubleArray(HUE_BUCKETS)
    val reds = DoubleArray(HUE_BUCKETS)
    val greens = DoubleArray(HUE_BUCKETS)
    val blues = DoubleArray(HUE_BUCKETS)
    for (argb in pixels) {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val (hue, saturation, value) = hsv(r, g, b)
        if (saturation < MIN_SATURATION || value < MIN_VALUE || value > MAX_VALUE) continue
        val bucket = ((hue / 360.0) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
        val weight = saturation * value
        weights[bucket] += weight
        reds[bucket] += r * weight
        greens[bucket] += g * weight
        blues[bucket] += b * weight
    }
    val best = weights.indices.maxByOrNull { weights[it] } ?: return null
    // A brand colour on a menu is often only a heading and a rule — well under a tenth of the page —
    // but a few stray pixels (a stain, a reflection) are not one.
    if (weights[best] < pixels.size * MIN_SHARE) return null
    val w = weights[best]
    return Color((reds[best] / w).toFloat(), (greens[best] / w).toFloat(), (blues[best] / w).toFloat())
}

/**
 * The colour tamed for a header: saturation kept between muddy and neon, then darkened step by
 * step until white text on it passes WCAG AA (4.5:1).
 */
internal fun headerSafe(colour: Color): Color {
    val (hue, saturation, lightness) = hsl(colour.red.toDouble(), colour.green.toDouble(), colour.blue.toDouble())
    val s = saturation.coerceIn(0.35, 0.75).toFloat()
    var l = min(lightness, 0.5).toFloat()
    while (l > 0.1f && contrastWithWhite(Color.hsl(hue.toFloat(), s, l)) < AA_CONTRAST) l -= 0.02f
    return Color.hsl(hue.toFloat(), s, l)
}

internal fun contrastWithWhite(colour: Color): Float = 1.05f / (colour.luminance() + 0.05f)

private fun hsv(
    r: Double,
    g: Double,
    b: Double,
): Triple<Double, Double, Double> {
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    val saturation = if (high == 0.0) 0.0 else delta / high
    return Triple(hueOf(r, g, b, high, delta), saturation, high)
}

private fun hsl(
    r: Double,
    g: Double,
    b: Double,
): Triple<Double, Double, Double> {
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    val lightness = (high + low) / 2
    val saturation = if (delta == 0.0) 0.0 else delta / (1 - abs(2 * lightness - 1))
    return Triple(hueOf(r, g, b, high, delta), saturation, lightness)
}

private fun hueOf(
    r: Double,
    g: Double,
    b: Double,
    high: Double,
    delta: Double,
): Double {
    if (delta == 0.0) return 0.0
    val hue =
        when (high) {
            r -> 60 * (((g - b) / delta) % 6)
            g -> 60 * (((b - r) / delta) + 2)
            else -> 60 * (((r - g) / delta) + 4)
        }
    return if (hue < 0) hue + 360 else hue
}

private const val SAMPLE = 96
private const val HUE_BUCKETS = 24
private const val MIN_SATURATION = 0.22
private const val MIN_VALUE = 0.25
private const val MAX_VALUE = 0.97
private const val MIN_SHARE = 0.006
private const val AA_CONTRAST = 4.5f
