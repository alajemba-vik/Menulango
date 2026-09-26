package com.menulango.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.Route
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.tipTarget
import com.menulango.data.tips.Tip
import com.menulango.di.AppConfig
import com.menulango.feature.capture.CaptureScreen
import com.menulango.feature.menus.MenusScreen
import com.menulango.feature.settings.SettingsScreen
import com.menulango.resources.Res
import com.menulango.resources.tab_menus
import com.menulango.resources.tab_scan
import com.menulango.resources.tab_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal enum class HomeTab { Scan, Menus, Settings }

/**
 * The app's three places — the camera, the menus already read, and settings — under one floating
 * tab bar. A menu itself opens full screen on top, without the bar, the way a restaurant page does.
 */
@Composable
internal fun HomeScreen(
    navigate: (Route) -> Unit,
    showMenus: Boolean = false,
    onShowedMenus: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Scan) }
    // Back from a menu just scanned: switch before the first frame so the camera never flashes.
    if (showMenus && tab != HomeTab.Menus) tab = HomeTab.Menus
    if (showMenus) SideEffect(onShowedMenus)
    val snackbar = remember { SnackbarHostState() }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Room above the floating bar: the camera adds the navigation bar itself, lists do not.
    val aboveBar = TAB_BAR_HEIGHT + Space.md * 2
    Box(Modifier.fillMaxSize().background(Paper.colors.paper)) {
        // Material's fade-through for peer tabs: the old tab is gone in a blink, the new one fades
        // up and settles from a hair smaller. Soft, and never a slide between equals.
        val reduceMotion = Paper.reduceMotion
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(Motion.QUICK_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS))
                } else {
                    (
                        fadeIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS)) +
                            scaleIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS), 0.97f)
                    ) togetherWith
                        fadeOut(tween(TAB_OUT_MS))
                }
            },
            label = "tabs",
        ) { current ->
            when (current) {
                HomeTab.Scan -> CaptureScreen(navigate = navigate, bottomInset = aboveBar)
                HomeTab.Menus -> MenusScreen(navigate, { tab = HomeTab.Scan }, snackbar, aboveBar + navigationBar)
                HomeTab.Settings -> SettingsScreen(navigate, snackbar, aboveBar + navigationBar)
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = Space.gutter)
                .padding(bottom = aboveBar),
        ) { data ->
            Snackbar(
                data,
                shape = Shapes.tile,
                containerColor = Paper.colors.ink,
                contentColor = Paper.colors.paper,
                actionColor = Paper.colors.seal,
            )
        }
        TabBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A floating capsule of destinations, after iOS 26's tab bar and Material 3's floating toolbar:
 * tabs only navigate, and the selected one sits in a soft lens. Actions — the shutter, "Help me
 * choose" — live on their own screens, never in the bar.
 */
@Composable
private fun TabBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val testBuild = koinInject<AppConfig>().showsTestTools
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = Space.md)
            .shadow(Elevation.floating, Shapes.pill, clip = false)
            .clip(Shapes.pill)
            .background(colors.raised.copy(alpha = 0.96f))
            .border(Space.hairline, colors.rule, Shapes.pill)
            .heightIn(min = TAB_BAR_HEIGHT)
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeTab.entries.forEach { tab ->
            TabItem(
                tab.icon(),
                stringResource(tab.label()),
                tab == selected,
                onClick = { onSelect(tab) },
                modifier =
                    if (testBuild &&
                        tab == HomeTab.Settings
                    ) {
                        Modifier.tipTarget(Tip.TesterSettings)
                    } else {
                        Modifier
                    },
            )
        }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val tint = if (selected) colors.sealInk else colors.inkMuted
    Row(
        modifier
            .widthIn(min = 96.dp)
            .heightIn(min = TAB_BAR_HEIGHT - 12.dp)
            .clip(Shapes.pill)
            .background(if (selected) colors.sealWash else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Space.md),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = Paper.type.chip.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
        )
    }
}

private fun HomeTab.icon(): ImageVector =
    when (this) {
        HomeTab.Scan -> PaperIcons.Camera
        HomeTab.Menus -> PaperIcons.Menu
        HomeTab.Settings -> PaperIcons.Settings
    }

private fun HomeTab.label(): StringResource =
    when (this) {
        HomeTab.Scan -> Res.string.tab_scan
        HomeTab.Menus -> Res.string.tab_menus
        HomeTab.Settings -> Res.string.tab_settings
    }

private val TAB_BAR_HEIGHT: Dp = 64.dp

private const val TAB_OUT_MS = 90
private const val TAB_IN_MS = 210
