package com.menulango

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.menulango.core.design.MenuLangoTheme
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.FeltSurface
import com.menulango.data.preferences.Appearance
import com.menulango.data.preferences.Preferences
import com.menulango.feature.capture.CaptureScreen
import com.menulango.feature.choose.ChooseScreen
import com.menulango.feature.home.HomeScreen
import com.menulango.feature.menu.MenuScreen
import com.menulango.feature.paywall.PaywallScreen
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
    MenuLangoTheme(darkTheme = darkTheme, reduceMotion = reduceMotion) {
        val navigator = remember { Navigator() }
        val saveableState = rememberSaveableStateHolder()

        BackGesture(enabled = navigator.canGoBack) { navigator.pop() }

        FeltSurface {
            Box(Modifier.fillMaxSize().background(Paper.colors.paper)) {
                AnimatedContent(
                    targetState = navigator.current,
                    transitionSpec = { screenTransition(initialState, targetState, navigator, reduceMotion) },
                    label = "screens",
                ) { entry ->
                    saveableState.SaveableStateProvider(entry.id) {
                        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                            Screen(entry.route, navigator)
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
            HomeScreen(navigate = navigator::push)
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
 * Pushes slide in from the trailing edge with the previous screen easing a third of the way out,
 * as iOS navigation does; the paywall rises from the bottom like a sheet. Nothing overshoots.
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
    val spec = tween<androidx.compose.ui.unit.IntOffset>(duration, easing = Motion.standard)
    return when {
        sheetLike && forward -> {
            slideInVertically(spec) { it } togetherWith fadeOut(tween(duration))
        }

        sheetLike -> {
            fadeIn(tween(duration)) togetherWith slideOutVertically(spec) { it }
        }

        forward -> {
            slideInHorizontally(spec) { it } togetherWith
                slideOutHorizontally(spec) { -it / 3 } + fadeOut(tween(duration))
        }

        else -> {
            slideInHorizontally(spec) { -it / 3 } + fadeIn(tween(duration)) togetherWith
                slideOutHorizontally(spec) { it }
        }
    }.apply { targetContentZIndex = if (forward) 1f else -1f }
}
