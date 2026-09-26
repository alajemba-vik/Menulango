package com.menulango.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.menulango.resources.Res
import com.menulango.resources.figtree_medium
import com.menulango.resources.figtree_regular
import com.menulango.resources.figtree_semibold
import com.menulango.resources.petrona_bold
import com.menulango.resources.petrona_italic
import com.menulango.resources.petrona_semibold
import org.jetbrains.compose.resources.Font

/**
 * MenuLango's type roles.
 *
 * Dish names and headings are Petrona Bold and large — they are the content, so they are set like
 * headlines. Everything else is Figtree and quiet. Scale: 40 / 30 / 24 / 20 / 17 / 15 / 13 / 11.
 */
@Immutable
internal data class PaperType(
    val hero: TextStyle,
    val dishTitle: TextStyle,
    val headline: TextStyle,
    val dishName: TextStyle,
    /** "How it is made" — the best feature, so it gets the most typographic weight. */
    val method: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    /** The name as printed, so the diner can point at the menu. */
    val original: TextStyle,
    /** Tabular figures so prices align down the column. */
    val price: TextStyle,
    val button: TextStyle,
    /** Pill chips: sentence case, never shouted. */
    val chip: TextStyle,
)

private val tightLines =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

@Composable
private fun rememberPaperType(latinScript: Boolean): PaperType {
    // Tracking is tuned for Latin and Cyrillic; Arabic, Devanagari and CJK set it to zero, or
    // joined scripts come apart and lines break in the middle of words.
    fun tracking(value: Float) = if (latinScript) value.em else 0.em
    val petrona =
        FontFamily(
            Font(Res.font.petrona_semibold, FontWeight.SemiBold),
            Font(Res.font.petrona_bold, FontWeight.Bold),
            Font(Res.font.petrona_italic, FontWeight.Normal, FontStyle.Italic),
        )
    val figtree =
        FontFamily(
            Font(Res.font.figtree_regular, FontWeight.Normal),
            Font(Res.font.figtree_medium, FontWeight.Medium),
            Font(Res.font.figtree_semibold, FontWeight.SemiBold),
        )
    return remember(petrona, figtree) {
        val base = TextStyle(lineHeightStyle = tightLines)
        val serif = base.copy(fontFamily = petrona, fontWeight = FontWeight.SemiBold)
        val sans = base.copy(fontFamily = figtree, fontWeight = FontWeight.Normal)
        PaperType(
            hero =
                serif.copy(
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = tracking(-0.01f),
                ),
            dishTitle =
                serif.copy(
                    fontSize = 30.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = tracking(-0.01f),
                ),
            headline = serif.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
            dishName = serif.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
            method =
                serif.copy(
                    fontSize = 20.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                ),
            title = sans.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            body = sans.copy(fontSize = 17.sp, lineHeight = 26.sp),
            bodySmall = sans.copy(fontSize = 15.sp, lineHeight = 23.sp),
            caption = sans.copy(fontSize = 13.sp, lineHeight = 18.sp),
            label =
                sans.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = tracking(0.12f),
                ),
            original = base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
            price =
                sans.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
            button =
                sans.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = tracking(0.01f),
                ),
            chip = sans.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        )
    }
}

private val LocalPaperColors = staticCompositionLocalOf { LightPaper }
private val LocalPaperType = staticCompositionLocalOf<PaperType> { error("PaperType read outside MenuLangoTheme") }

/** True when the diner has asked their OS for less motion. Animations fall back to plain fades. */
internal val LocalReduceMotion = staticCompositionLocalOf { false }

/** Accessors for the current theme, so call sites read `Paper.colors.ink`, never a hex value. */
internal object Paper {
    val colors: PaperColors
        @Composable @ReadOnlyComposable
        get() = LocalPaperColors.current

    val type: PaperType
        @Composable @ReadOnlyComposable
        get() = LocalPaperType.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReduceMotion.current
}

/**
 * Wraps the app in the paper system.
 *
 * Material 3 is kept only for its text, ripple and accessibility plumbing. Its tonal elevation
 * overlay is neutralised by tinting surfaces with their own colour — otherwise every raised
 * surface would pick up a coral cast and the app would look like a stock Android app.
 */
@Composable
internal fun MenuLangoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    /** False for Arabic, Devanagari, Chinese, Japanese and Korean, which take no letter spacing. */
    latinScript: Boolean = true,
    content: @Composable () -> Unit,
) {
    // The whole palette fades between light and dark instead of snapping, as the system does.
    val darkness by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else THEME_FADE_MS, easing = Motion.standard),
        label = "theme",
    )
    val colors = LightPaper.blend(DarkPaper, darkness)
    val type = rememberPaperType(latinScript)
    val material =
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.seal,
                onPrimary = colors.onSeal,
                secondary = colors.ember,
                background = colors.paper,
                onBackground = colors.ink,
                surface = colors.raised,
                onSurface = colors.ink,
                surfaceVariant = colors.sunk,
                onSurfaceVariant = colors.inkMuted,
                surfaceTint = colors.raised,
                outline = colors.rule,
                outlineVariant = colors.rule,
                error = colors.alarm,
                scrim = colors.scrim,
            )
        } else {
            lightColorScheme(
                primary = colors.seal,
                onPrimary = colors.onSeal,
                secondary = colors.ember,
                background = colors.paper,
                onBackground = colors.ink,
                surface = colors.raised,
                onSurface = colors.ink,
                surfaceVariant = colors.sunk,
                onSurfaceVariant = colors.inkMuted,
                surfaceTint = colors.raised,
                outline = colors.rule,
                outlineVariant = colors.rule,
                error = colors.alarm,
                scrim = colors.scrim,
            )
        }
    CompositionLocalProvider(
        LocalPaperColors provides colors,
        LocalPaperType provides type,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = material) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

private const val THEME_FADE_MS = 450
