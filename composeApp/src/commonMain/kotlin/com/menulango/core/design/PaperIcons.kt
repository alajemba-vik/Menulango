package com.menulango.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's line icons: 24dp box, 1.5dp stroke, round caps.
 *
 * Drawn in code rather than pulled from an icon library so there are exactly as many as the
 * screens need, all with the same pen. Tinted at the call site, so they carry no colour of their own.
 */
internal object PaperIcons {
    val Back: ImageVector =
        lineIcon("back") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }

    val Close: ImageVector =
        lineIcon("close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }

    val Gallery: ImageVector =
        lineIcon("gallery") {
            moveTo(4.5f, 5.5f)
            lineTo(19.5f, 5.5f)
            lineTo(19.5f, 18.5f)
            lineTo(4.5f, 18.5f)
            close()
            moveTo(4.5f, 15.5f)
            lineTo(9f, 11f)
            lineTo(13f, 15f)
            lineTo(15.5f, 12.5f)
            lineTo(19.5f, 16.5f)
            moveTo(15f, 9f)
            arcToRelative(0.75f, 0.75f, 0f, true, true, 0.01f, 0f)
        }

    val Menu: ImageVector =
        lineIcon("menu") {
            moveTo(6.5f, 3.5f)
            lineTo(17.5f, 3.5f)
            lineTo(17.5f, 20.5f)
            lineTo(6.5f, 20.5f)
            close()
            moveTo(9f, 8f)
            lineTo(15f, 8f)
            moveTo(9f, 11.5f)
            lineTo(15f, 11.5f)
            moveTo(9f, 15f)
            lineTo(13f, 15f)
        }

    val Check: ImageVector =
        lineIcon("check") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }

    val Retake: ImageVector =
        lineIcon("retake") {
            moveTo(19f, 12f)
            arcToRelative(7f, 7f, 0f, true, true, -2.05f, -4.95f)
            moveTo(19f, 4.5f)
            lineTo(19f, 8.5f)
            lineTo(15f, 8.5f)
        }

    val Camera: ImageVector =
        lineIcon("camera") {
            moveTo(4f, 8.5f)
            lineTo(7.5f, 8.5f)
            lineTo(9f, 6f)
            lineTo(15f, 6f)
            lineTo(16.5f, 8.5f)
            lineTo(20f, 8.5f)
            lineTo(20f, 18.5f)
            lineTo(4f, 18.5f)
            close()
            moveTo(8.5f, 13.25f)
            arcToRelative(3.5f, 3.5f, 0f, true, true, 7f, 0f)
            arcToRelative(3.5f, 3.5f, 0f, true, true, -7f, 0f)
        }

    val Settings: ImageVector =
        lineIcon("settings") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            moveTo(7f, 7f)
            arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)
            arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
            moveTo(13f, 12f)
            arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)
            arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
            moveTo(6f, 17f)
            arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)
            arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
        }

    val ChevronRight: ImageVector =
        lineIcon("chevron-right") {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }

    /** The dome a dish is served under: "Help me choose" lifts it to reveal a pick. */
    val Cloche: ImageVector =
        lineIcon("cloche") {
            moveTo(3.5f, 17.5f)
            lineTo(20.5f, 17.5f)
            moveTo(5f, 17.5f)
            arcToRelative(7f, 7f, 0f, false, true, 14f, 0f)
            moveTo(12f, 10.5f)
            lineTo(12f, 8.5f)
            moveTo(10.5f, 7.5f)
            lineTo(13.5f, 7.5f)
            moveTo(8.5f, 13.5f)
            arcToRelative(4f, 4f, 0f, false, true, 2.5f, -2.3f)
        }

    val Search: ImageVector =
        lineIcon("search") {
            moveTo(16.5f, 10.5f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 4.5f, y1 = 10.5f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 16.5f, y1 = 10.5f)
            moveTo(15f, 15f)
            lineTo(20f, 20f)
        }

    val Minus: ImageVector =
        lineIcon("minus") {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }

    val Plus: ImageVector =
        lineIcon("plus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }

    val ArrowUpRight: ImageVector =
        lineIcon("arrow-up-right") {
            moveTo(7f, 17f)
            lineTo(17f, 7f)
            moveTo(9f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 15f)
        }

    private fun lineIcon(
        name: String,
        pathBuilder: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = pathBuilder,
            ).build()
}
