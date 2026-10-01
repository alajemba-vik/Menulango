package com.menulango.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The logo's own colours, the same in light and dark: it is a mark, not a theme colour. */
internal val LogoAubergine = Color(0xFF6B2E83)
internal val LogoSaffron = Color(0xFFF2A93B)

/** The MenuLango mark on its own: the saffron bubble, the aubergine bubble and the fork. */
@Composable
internal fun MenuLangoMark(
    description: String?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        drawMenuLangoMark(fill = 1f)
    }
}

/**
 * Draws the mark centred in this scope, its width [fill] of the smaller side, and returns its
 * outline so callers can clip effects (a sheen) to it. The gap between the bubbles is cut out,
 * not painted, so the mark sits on any background. Geometry is the launcher icon's 108-unit
 * canvas, where the mark spans x 28..80, y 30..80.
 */
internal fun DrawScope.drawMenuLangoMark(fill: Float): Path {
    val unit = size.minDimension * fill / 52f
    val ox = (size.width - 52f * unit) / 2f - 28f * unit
    val oy = (size.height - 50f * unit) / 2f - 30f * unit

    fun bubble(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r: Float,
        sharpBottomLeft: Boolean,
    ) = Path().apply {
        val round = CornerRadius(r * unit)
        addRoundRect(
            RoundRect(
                left = ox + x0 * unit,
                top = oy + y0 * unit,
                right = ox + x1 * unit,
                bottom = oy + y1 * unit,
                topLeftCornerRadius = round,
                topRightCornerRadius = round,
                bottomRightCornerRadius = if (sharpBottomLeft) round else CornerRadius.Zero,
                bottomLeftCornerRadius = if (sharpBottomLeft) CornerRadius.Zero else round,
            ),
        )
    }
    val back = bubble(47f, 30f, 80f, 61f, 13f, sharpBottomLeft = false)
    val front = bubble(28f, 44f, 68f, 80f, 15f, sharpBottomLeft = true)
    val gap = bubble(25.4f, 41.4f, 70.6f, 82.6f, 17.6f, sharpBottomLeft = true)
    val backCut = Path.combine(PathOperation.Difference, back, gap)
    drawPath(backCut, LogoSaffron)
    drawPath(front, LogoAubergine)

    // The fork, in white.
    fun bar(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r: Float,
    ) = drawRoundRect(
        Color.White,
        topLeft = Offset(ox + x0 * unit, oy + y0 * unit),
        size = Size((x1 - x0) * unit, (y1 - y0) * unit),
        cornerRadius = CornerRadius(r * unit),
    )
    bar(42.6f, 50f, 45f, 59f, 1.2f)
    bar(46.8f, 50f, 49.2f, 59f, 1.2f)
    bar(51f, 50f, 53.4f, 59f, 1.2f)
    bar(42.6f, 57f, 53.4f, 62.5f, 2.7f)
    bar(46.5f, 60f, 49.5f, 73.5f, 1.5f)
    return Path.combine(PathOperation.Union, backCut, front)
}
