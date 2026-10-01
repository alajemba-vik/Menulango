package com.menulango.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.Route
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.PaperSnackbar
import com.menulango.core.ui.tipTarget
import com.menulango.data.menu.ActiveScans
import com.menulango.data.preferences.Preferences
import com.menulango.data.preferences.StartPage
import com.menulango.data.tips.Tip
import com.menulango.data.tips.Tips
import com.menulango.di.AppConfig
import com.menulango.feature.capture.CaptureScreen
import com.menulango.feature.menus.MenusScreen
import com.menulango.feature.settings.SettingsScreen
import com.menulango.resources.Res
import com.menulango.resources.scan_failed_background
import com.menulango.resources.scan_failed_nothing_found
import com.menulango.resources.tab_menus
import com.menulango.resources.tab_scan
import com.menulango.resources.tab_settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
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
    tabHolder: (initial: () -> HomeTab) -> MutableState<HomeTab> = { mutableStateOf(it()) },
) {
    val preferences = koinInject<Preferences>()
    val tips = koinInject<Tips>()
    // Menus by default; the camera if the diner chose it, or while they have never used it (the
    // welcome page lives there, and a first-time Menus list would only be empty).
    // Held by the navigator, above the language switch, so changing language keeps the tab.
    var tab by remember {
        tabHolder {
            when {
                Tip.Welcome !in tips.seen.value -> HomeTab.Scan
                preferences.startPage.value == StartPage.Camera -> HomeTab.Scan
                else -> HomeTab.Menus
            }
        }
    }
    // A launch that opens on Menus and goes straight to the camera counts towards suggesting
    // "open on Camera". Counted once per launch; three such launches bring the suggestion.
    LaunchedEffect(Unit) {
        if (!LaunchCount.recorded && tab == HomeTab.Menus) {
            snapshotFlow { tab }.first { it != HomeTab.Menus }.let { next ->
                if (!LaunchCount.recorded && next == HomeTab.Scan) preferences.cameraFirstLaunches += 1
                LaunchCount.recorded = true
            }
        }
    }
    val suggestCameraStart =
        preferences.startPage.collectAsState().value == StartPage.Menus &&
            preferences.cameraFirstLaunches >= CAMERA_FIRST_LAUNCHES
    // Back from a menu just scanned: switch before the first frame so the camera never flashes.
    if (showMenus && tab != HomeTab.Menus) tab = HomeTab.Menus
    if (showMenus) SideEffect(onShowedMenus)
    // The bar answers a tap at once; the screen follows a couple of frames later. Building the
    // camera or Settings takes a moment, and done in the tap's own frame it held the lens still
    // until it finished, so the bar felt a beat late.
    var barTab by remember { mutableStateOf(tab) }
    LaunchedEffect(tab) { barTab = tab }
    LaunchedEffect(barTab) {
        if (barTab == tab) return@LaunchedEffect
        repeat(TAB_SWAP_FRAMES) { withFrameNanos { } }
        tab = barTab
    }
    val snackbar = remember { SnackbarHostState() }
    // A menu read after the diner left it, that ended with nothing saved: say so here, once.
    // Collected rather than keyed on the value, so marking it told can't cancel the snackbar
    // that is saying it (which is how these messages used to vanish unseen).
    val activeScans = koinInject<ActiveScans>()
    LaunchedEffect(activeScans) {
        activeScans.failures.collect { pending ->
            val failed = pending.firstOrNull() ?: return@collect
            activeScans.told(failed.id)
            snackbar.showSnackbar(
                getString(if (failed.nothingFound) Res.string.scan_failed_nothing_found else Res.string.scan_failed_background),
                duration = SnackbarDuration.Long,
            )
        }
    }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Room above the floating bar: the camera adds the navigation bar itself, lists do not.
    val aboveBar = TAB_BAR_HEIGHT + Space.md * 2
    // First launch: the welcome pages stand alone, with no tab bar under them.
    val welcoming = tab == HomeTab.Scan && Tip.Welcome !in tips.seen.collectAsState().value
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
                HomeTab.Scan -> CaptureScreen(navigate = navigate, bottomInset = if (welcoming) 0.dp else aboveBar)
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
            PaperSnackbar(data)
        }
        AnimatedVisibility(
            visible = !welcoming,
            enter = fadeIn(tween(Motion.SHEET_MS)),
            exit = fadeOut(tween(Motion.QUICK_MS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TabBar(
                barTab,
                onSelect = { barTab = it },
                suggestStart = suggestCameraStart,
            )
        }
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
    suggestStart: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val testBuild = koinInject<AppConfig>().showsTestTools
    val reduceMotion = Paper.reduceMotion
    // One lens for the whole bar, sliding to the chosen tab rather than each tab lighting up on
    // its own. Its two edges travel separately, the leading one first, so it stretches like a
    // drop of liquid and settles with a small bounce.
    val bounds = remember { mutableStateMapOf<HomeTab, Rect>() }
    val start = remember { Animatable(0f) }
    val end = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds[selected]
    LaunchedEffect(target) {
        val to = target ?: return@LaunchedEffect
        if (!placed || reduceMotion) {
            start.snapTo(to.left)
            end.snapTo(to.right)
            placed = true
            return@LaunchedEffect
        }
        val rightward = to.left > start.value
        val lead = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        val trail = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
        launch { start.animateTo(to.left, if (rightward) trail else lead) }
        launch { end.animateTo(to.right, if (rightward) lead else trail) }
    }
    val lens = colors.sealWash
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
            // Inside the padding, where the tabs report their bounds.
            .drawBehind {
                val at = target ?: return@drawBehind
                if (!placed) return@drawBehind
                drawRoundRect(
                    lens,
                    topLeft = Offset(start.value, at.top),
                    size = Size(end.value - start.value, at.height),
                    cornerRadius = CornerRadius(at.height / 2f),
                )
            }.selectableGroup(),
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
                    (
                        if (testBuild && tab == HomeTab.Settings) {
                            Modifier.tipTarget(Tip.TesterSettings)
                        } else {
                            Modifier
                        }
                    ).onGloballyPositioned { bounds[tab] = it.boundsInParent() },
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
    val tint by animateColorAsState(
        if (selected) colors.sealInk else colors.inkMuted,
        tween(Motion.QUICK_MS),
        label = "tab-tint",
    )
    Row(
        modifier
            .widthIn(min = 96.dp)
            .heightIn(min = TAB_BAR_HEIGHT - 12.dp)
            .clip(Shapes.pill)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private const val TAB_SWAP_FRAMES = 2
private const val TAB_IN_MS = 210

/** Once per app launch: whether this launch's first move has been counted. */
private object LaunchCount {
    var recorded = false
}

/** Launches that went straight to the camera before we suggest opening on it. */
private const val CAMERA_FIRST_LAUNCHES = 3
