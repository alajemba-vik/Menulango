package com.menulango

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.menulango.core.design.MenuLangoTheme
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.FeltSurface
import com.menulango.core.ui.TipHost
import com.menulango.data.preferences.AppLanguage
import com.menulango.data.preferences.Appearance
import com.menulango.data.preferences.Preferences
import com.menulango.data.tips.Tips
import com.menulango.feature.capture.CaptureScreen
import com.menulango.feature.choose.ChooseScreen
import com.menulango.feature.home.HomeScreen
import com.menulango.feature.menu.MenuScreen
import com.menulango.feature.paywall.PaywallScreen
import com.menulango.platform.LocalAppLocale
import com.menulango.platform.SystemBarsFollow
import org.koin.compose.koinInject

/**
 * The root of MenuLango on both platforms.
 *
 * @param reduceMotion the platform's reduce-motion setting; every transition falls back to a fade.
 */
@Composable
public fun MenuLangoApp(reduceMotion: Boolean = false) {
    val preferences = koinInject<Preferences>()
    val appearance by preferences.appearance.collectAsState()
    val darkTheme =
        when (appearance) {
            Appearance.System -> isSystemInDarkTheme()
            Appearance.Light -> false
            Appearance.Dark -> true
        }
    val calmMotion by preferences.calmMotion.collectAsState()
    SystemBarsFollow(darkTheme)
    val language by preferences.language.collectAsState()
    // Arabic reads right to left whatever the phone is set to; System keeps the phone's direction.
    val direction =
        when (language) {
            AppLanguage.System -> LocalLayoutDirection.current
            AppLanguage.Arabic -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }
    // Kept above the language switch, so changing language keeps the diner where they are.
    val navigator = remember { Navigator() }
    val saveableState = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalAppLocale provides language.languageTag, LocalLayoutDirection provides direction) {
        // A new language rebuilds the tree, so every string is read again in it.
        key(language) {
            val script = language.languageTag ?: Locale.current.language
            MenuLangoTheme(
                darkTheme = darkTheme,
                reduceMotion = reduceMotion || calmMotion,
                latinScript = script.substringBefore('-') !in NON_LATIN_SCRIPTS,
            ) {
                AppContent(navigator, saveableState, reduceMotion || calmMotion)
            }
        }
    }
}

@Composable
private fun AppContent(
    navigator: Navigator,
    saveableState: SaveableStateHolder,
    reduceMotion: Boolean,
) {
    BackGesture(enabled = navigator.canGoBack) { navigator.pop() }

    val tips = koinInject<Tips>()
    val seenTips by tips.seen.collectAsState()
    FeltSurface {
        TipHost(seen = seenTips, onSeen = tips::markSeen) {
            Box(Modifier.fillMaxSize().background(Paper.colors.paper)) {
                AnimatedContent(
                    targetState = navigator.current,
                    transitionSpec = { screenTransition(initialState, targetState, navigator, reduceMotion) },
                    label = "screens",
                ) { entry ->
                    // Each page comes into focus as it arrives and softens as it leaves, like a
                    // photo developing. A plain fade under reduce motion.
                    val blur by transition.animateDp(
                        transitionSpec = { tween(Motion.SCREEN_MS, easing = Motion.standard) },
                        label = "screen-focus",
                    ) { state -> if (state == EnterExitState.Visible || reduceMotion) 0.dp else SCREEN_BLUR }
                    saveableState.SaveableStateProvider(entry.id) {
                        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                            Box(Modifier.fillMaxSize().then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)) {
                                Screen(entry.route, navigator)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Screen(
    route: Route,
    navigator: Navigator,
) {
    when (route) {
        Route.Home -> {
            HomeScreen(
                navigate = navigator::push,
                showMenus = navigator.showMenusOnReturn && navigator.current.route == Route.Home,
                onShowedMenus = { navigator.showMenusOnReturn = false },
                tabHolder = { initial ->
                    navigator.homeTab ?: mutableStateOf(initial()).also { navigator.homeTab = it }
                },
            )
        }

        is Route.AddPage -> {
            CaptureScreen(navigate = navigator::push, addPage = route, onPagesAdded = navigator::pop)
        }

        is Route.Menu -> {
            MenuScreen(
                source = route.source,
                onBack = navigator::pop,
                onRetake = navigator::pop,
                navigate = navigator::push,
            )
        }

        is Route.Choose -> {
            ChooseScreen(
                dishes = route.dishes,
                initialMode = route.mode,
                orderKey = route.orderKey,
                onBack = navigator::pop,
            )
        }

        is Route.Paywall -> {
            PaywallScreen(reason = route.reason, onClose = navigator::pop)
        }
    }
}

/**
 * Material's shared-axis Z: forward, the new page grows into place from slightly small while the
 * old one grows away and fades; back is the same in reverse. Combined with the focus blur above it
 * reads as the page developing into view. The paywall still rises from the bottom like a sheet.
 */
private fun screenTransition(
    from: BackStackEntry,
    to: BackStackEntry,
    navigator: Navigator,
    reduceMotion: Boolean,
): ContentTransform {
    val duration = Motion.SCREEN_MS
    if (reduceMotion) return fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
    val forward = navigator.entries.any { it.id == from.id } && to.id > from.id
    val sheetLike = (if (forward) to.route else from.route) is Route.Paywall
    val slide = tween<androidx.compose.ui.unit.IntOffset>(duration, easing = Motion.standard)
    val grow = tween<Float>(duration, easing = Motion.standard)
    // The outgoing page leaves quickly; the incoming one waits a beat, so they never muddle.
    val leave = tween<Float>(duration / 3, easing = Motion.standard)
    val arrive = tween<Float>(duration - duration / 4, delayMillis = duration / 4, easing = Motion.standard)
    return when {
        sheetLike && forward -> {
            slideInVertically(slide) { it } togetherWith fadeOut(tween(duration))
        }

        sheetLike -> {
            fadeIn(tween(duration)) togetherWith slideOutVertically(slide) { it }
        }

        forward -> {
            (fadeIn(arrive) + scaleIn(grow, initialScale = 0.94f)) togetherWith
                (fadeOut(leave) + scaleOut(grow, targetScale = 1.04f))
        }

        else -> {
            (fadeIn(arrive) + scaleIn(grow, initialScale = 1.04f)) togetherWith
                (fadeOut(leave) + scaleOut(grow, targetScale = 0.94f))
        }
    }
}

/** How out of focus a page is at the start of its arrival and the end of its exit. */
private val SCREEN_BLUR = 12.dp

/** Languages whose script takes no letter spacing. */
private val NON_LATIN_SCRIPTS = setOf("ar", "hi", "zh", "ja", "ko")
