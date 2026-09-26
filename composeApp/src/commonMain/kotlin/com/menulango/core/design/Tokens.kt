package com.menulango.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every colour in MenuLango. No hex literal appears anywhere else in the codebase.
 *
 * "Felt board": cream felt, coral as the brand, charcoal for the hero moments. [seal] is the brand
 * colour for the primary action and the selected state; [sealInk] is the same coral darkened so it
 * reads as text. [alarm] is reserved for allergens — never decoration.
 *
 * Soft colours have exactly one job: [food] says what kind of food a dish is — sea, garden, grill,
 * sweet, drink, hearth. Nothing else in the app uses them, so matching colours always mean the
 * same kind of food and never a false connection.
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
    /** Coral dark enough to be read as text on [paper] and [raised]. */
    val sealInk: Color,
    /** Text and icons on a [seal] fill. */
    val onSeal: Color,
    val ember: Color,
    val alarm: Color,
    /** Behind the menu photo when it dims. Warm, like a room with the lights down. */
    val scrim: Color,
    /** Text and controls laid over the live camera, which is dark-ish in any theme. */
    val onPhoto: Color,
    /** One soft fill per [FoodGroup], in declaration order. [ink] reads on every one of them. */
    private val foodColors: List<Color>,
    val isDark: Boolean,
) {
    init {
        require(foodColors.size == FoodGroup.entries.size) { "one colour per food group" }
    }

    fun food(group: FoodGroup): Color = foodColors[group.ordinal]

    /** Part-way from this palette to [to]: how a theme change fades rather than snaps. */
    fun blend(
        to: PaperColors,
        fraction: Float,
    ): PaperColors {
        if (fraction <= 0f) return this
        if (fraction >= 1f) return to

        fun mix(
            a: Color,
            b: Color,
        ) = lerp(a, b, fraction)
        return PaperColors(
            paper = mix(paper, to.paper),
            raised = mix(raised, to.raised),
            sunk = mix(sunk, to.sunk),
            ink = mix(ink, to.ink),
            inkMuted = mix(inkMuted, to.inkMuted),
            inkFaint = mix(inkFaint, to.inkFaint),
            rule = mix(rule, to.rule),
            seal = mix(seal, to.seal),
            sealInk = mix(sealInk, to.sealInk),
            onSeal = mix(onSeal, to.onSeal),
            ember = mix(ember, to.ember),
            alarm = mix(alarm, to.alarm),
            scrim = mix(scrim, to.scrim),
            onPhoto = mix(onPhoto, to.onPhoto),
            foodColors = foodColors.zip(to.foodColors) { a, b -> mix(a, b) },
            isDark = if (fraction < 0.5f) isDark else to.isDark,
        )
    }

    /** A peach tint of the brand colour: selected cards, fact tiles, the stepper. */
    val sealWash: Color get() = seal.copy(alpha = if (isDark) 0.18f else 0.12f)

    /** The allergen panel's wash. Derived, so the panel can never drift from [alarm]. */
    val alarmWash: Color get() = alarm.copy(alpha = if (isDark) 0.14f else 0.07f)

    val emberWash: Color get() = ember.copy(alpha = if (isDark) 0.16f else 0.12f)
}

internal val LightPaper =
    PaperColors(
        paper = Color(0xFFF7F3EE),
        raised = Color(0xFFFFFFFF),
        sunk = Color(0xFFF0E9E2),
        ink = Color(0xFF201917),
        inkMuted = Color(0xFF6B5F5A),
        inkFaint = Color(0xFF9A8D86),
        rule = Color(0xFFEBE2DA),
        seal = Color(0xFFE4572E),
        sealInk = Color(0xFFC24323),
        onSeal = Color(0xFFFFFFFF),
        ember = Color(0xFFA3670F),
        alarm = Color(0xFF8E3037),
        scrim = Color(0xFF14100E),
        onPhoto = Color(0xFFFFFFFF),
        foodColors =
            listOf(
                Color(0xFFD5E8F6), // sea
                Color(0xFFD7EDD3), // garden
                Color(0xFFFFDCCB), // grill
                Color(0xFFFBEBA8), // sweet
                Color(0xFFE3DEFF), // drink
                Color(0xFFEFE2CA), // hearth
            ),
        isDark = false,
    )

/** "The menu by candlelight": charcoal, like the onboarding cards of a good food app. */
internal val DarkPaper =
    PaperColors(
        paper = Color(0xFF181311),
        raised = Color(0xFF241D1A),
        sunk = Color(0xFF2D2522),
        ink = Color(0xFFF5EEE9),
        inkMuted = Color(0xFFB5A8A1),
        inkFaint = Color(0xFF85776F),
        rule = Color(0xFF362D29),
        seal = Color(0xFFFF7A59),
        sealInk = Color(0xFFFF8A6B),
        onSeal = Color(0xFF201917),
        ember = Color(0xFFF5B348),
        alarm = Color(0xFFE08790),
        scrim = Color(0xFF14100E),
        onPhoto = Color(0xFFFFFFFF),
        foodColors =
            listOf(
                Color(0xFF1F3340), // sea
                Color(0xFF243726), // garden
                Color(0xFF4A2D23), // grill
                Color(0xFF443A1C), // sweet
                Color(0xFF302B4B), // drink
                Color(0xFF3A3226), // hearth
            ),
        isDark = true,
    )

/** What kind of food a dish is. Each has its own colour; see [PaperColors.food]. */
internal enum class FoodGroup {
    /** Fish and seafood: blue, the sea. */
    Sea,

    /** Salads, vegetables and dips: green, the garden. */
    Garden,

    /** Meat, kebabs and burgers: terracotta, the fire. */
    Grill,

    /** Desserts, fruit and pastries: butter yellow, honey. */
    Sweet,

    /** Wine, beer, coffee and cocktails: lavender. */
    Drink,

    /** Everything from the kitchen's heart — cheese, eggs, bread, pasta, rice, soups: oat. */
    Hearth,
}

/** A 4dp grid. Whitespace is what makes the app feel calm, so be generous with it. */
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

/**
 * Tailored rather than bubbly, in three families, each with one job:
 *
 *  - Rounded rectangles for everything that sits in the page: cards (14), buttons (14),
 *    tiles (10), chips (8). A smaller thing gets a smaller radius, so corners look alike in scale.
 *  - Capsules ([pill]) only for what floats above the page: the tab bar, the choose button, the
 *    table bar, the page chip. The shape itself says "this is not part of the menu".
 *  - Circles only for single objects: plates, the shutter, one-icon buttons.
 */
internal object Shapes {
    val card: RoundedCornerShape = RoundedCornerShape(14.dp)
    val tile: RoundedCornerShape = RoundedCornerShape(10.dp)
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val button: RoundedCornerShape = RoundedCornerShape(14.dp)

    /** Chips are small woven labels: squared off, softly. */
    val chip: RoundedCornerShape = RoundedCornerShape(8.dp)

    /** Only for things that float: the tab bar, the choose button, segmented switches. */
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50)
}

/**
 * Four heights, and the felt does most of the separating: shadows only say how far something sits
 * off the board. Most things rest on it; very little floats.
 */
internal object Elevation {
    /** Lying on the felt: plates, pinned snapshots, the card behind. */
    val resting: Dp = 1.dp

    /** Lifted a little for emphasis: the top of a deck, a small badge. */
    val raised: Dp = 3.dp

    /** Floating above the page: the tab bar, the choose button, the picks bar. */
    val floating: Dp = 6.dp

    /** A sheet drawn up over the page. */
    val sheet: Dp = 12.dp
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
