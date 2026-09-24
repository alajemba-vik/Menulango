package com.menulango.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every colour in MenuLango. No hex literal appears anywhere else in the codebase.
 *
 * "Fancy paper": thick warm stock, ink sitting in the page. [seal] is the one brand colour and is
 * used like a stamp, never as a coat of paint. [alarm] is reserved for allergens — never decoration.
 */
@Immutable
internal data class PaperColors(
    val paper: Color,
    val raised: Color,
    val sunk: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val rule: Color,
    val seal: Color,
    val ember: Color,
    val alarm: Color,
    /** Behind the menu photo when it dims. Warm, like a room with the lights down. */
    val scrim: Color,
    /** Text and controls laid over the live camera, which is dark-ish in any theme. */
    val onPhoto: Color,
    val isDark: Boolean,
) {
    /** Text and icons on a [seal] fill. The page colour reads as the paper showing through the stamp. */
    val onSeal: Color get() = paper

    /** The allergen panel's wash. Derived, so the panel can never drift from [alarm]. */
    val alarmWash: Color get() = alarm.copy(alpha = if (isDark) 0.14f else 0.07f)

    val emberWash: Color get() = ember.copy(alpha = if (isDark) 0.16f else 0.10f)
}

internal val LightPaper =
    PaperColors(
        paper = Color(0xFFFAF8F3),
        raised = Color(0xFFFFFFFF),
        sunk = Color(0xFFF1EEE5),
        ink = Color(0xFF1A1D17),
        inkMuted = Color(0xFF5C6153),
        inkFaint = Color(0xFF8E9284),
        rule = Color(0xFFE4E0D6),
        seal = Color(0xFF1F5A3D),
        ember = Color(0xFFA3670F),
        alarm = Color(0xFF8E3037),
        scrim = Color(0xFF0E0D09),
        onPhoto = Color(0xFFFAF8F3),
        isDark = false,
    )

/** "The menu by candlelight." */
internal val DarkPaper =
    PaperColors(
        paper = Color(0xFF14130E),
        raised = Color(0xFF1D1B15),
        sunk = Color(0xFF23211A),
        ink = Color(0xFFEFEBE0),
        inkMuted = Color(0xFFA7A493),
        inkFaint = Color(0xFF78755F),
        rule = Color(0xFF2E2B22),
        seal = Color(0xFF6FC694),
        ember = Color(0xFFE0A458),
        alarm = Color(0xFFE08790),
        scrim = Color(0xFF0E0D09),
        onPhoto = Color(0xFFEFEBE0),
        isDark = true,
    )

/** A 4dp grid. Whitespace is what makes paper feel expensive, so be generous with it. */
internal object Space {
    val hairline: Dp = 1.dp
    val xs: Dp = 4.dp
    val related: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val gutter: Dp = 20.dp
    val cardPadding: Dp = 20.dp
    val section: Dp = 28.dp
    val xl: Dp = 40.dp

    /** The smallest touch target on either platform. */
    val touchTarget: Dp = 48.dp

    /** Explanation paragraphs stay under ~66 characters a line. */
    val readingWidth: Dp = 560.dp
}

/** Paper has corners. Heavy rounding reads as plastic. */
internal object Shapes {
    val card: RoundedCornerShape = RoundedCornerShape(4.dp)
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    val chip: RoundedCornerShape = RoundedCornerShape(2.dp)
}

/** The only shadow in the app, used by the dish sheet. Everything else separates by tone and rules. */
internal object Elevation {
    val sheet: Dp = 18.dp
}

/**
 * Paper settles; it never bounces. Every curve eases out and nothing travels past its destination.
 */
internal object Motion {
    val settle: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val QUICK_MS: Int = 180
    const val SHEET_MS: Int = 280
    const val SCREEN_MS: Int = 320
    const val ENTRANCE_MS: Int = 420

    /** Dish rows are dealt one after another, like cards laid on a table. */
    const val STAGGER_MS: Int = 40
    const val STAGGER_CAP: Int = 10
    val riseDistance: Dp = 12.dp
}
