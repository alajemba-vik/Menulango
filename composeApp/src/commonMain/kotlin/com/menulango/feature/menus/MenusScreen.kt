package com.menulango.feature.menus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.MenuSource
import com.menulango.Route
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.MenuSnapshot
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.ScrollTitleBar
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.pressable
import com.menulango.data.menu.local.SavedMenuItem
import com.menulango.data.tips.Tip
import com.menulango.data.tips.Tips
import com.menulango.feature.menu.menuTitle
import com.menulango.resources.Res
import com.menulango.resources.menu_context_dishes
import com.menulango.resources.menu_context_one_dish
import com.menulango.resources.menu_snapshot_description
import com.menulango.resources.menus_days_ago
import com.menulango.resources.menus_delete
import com.menulango.resources.menus_deleted
import com.menulango.resources.menus_empty_action
import com.menulango.resources.menus_empty_body
import com.menulango.resources.menus_empty_title
import com.menulango.resources.menus_subtitle
import com.menulango.resources.menus_title
import com.menulango.resources.menus_today
import com.menulango.resources.menus_undo
import com.menulango.resources.menus_yesterday
import com.menulango.resources.separator_dot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every menu the diner has read, newest first, each ready to reopen offline.
 *
 * @param bottomInset room for the floating tab bar under the last card.
 */
@Composable
internal fun MenusScreen(
    navigate: (Route) -> Unit,
    onScan: () -> Unit,
    snackbar: SnackbarHostState,
    bottomInset: Dp,
) {
    val viewModel = koinViewModel<MenusViewModel>()
    val tips = koinInject<Tips>()
    val seenTips by tips.seen.collectAsState()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = Paper.colors

    val onDelete: (String) -> Unit = { key ->
        viewModel.hide(key)
        scope.launch {
            val result =
                snackbar.showSnackbar(
                    message = getString(Res.string.menus_deleted),
                    actionLabel = getString(Res.string.menus_undo),
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(key) else viewModel.commit(key)
        }
    }

    Box(Modifier.fillMaxSize().felt(colors.paper)) {
        val ready = state as? MenusUiState.Ready ?: return@Box
        val list = rememberLazyListState()
        val titleGone = with(LocalDensity.current) { TITLE_SCROLL_AWAY.roundToPx() }
        val collapsed by remember {
            derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > titleGone }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "title") {
                Column(Modifier.statusBarsPadding().padding(top = Space.xl, bottom = Space.sm)) {
                    Text(
                        stringResource(Res.string.menus_title),
                        style = Paper.type.hero,
                        color = colors.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(Res.string.menus_subtitle),
                        style = Paper.type.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
            if (ready.menus.isEmpty()) {
                item(key = "empty") {
                    StateMessage(
                        stringResource(Res.string.menus_empty_title),
                        stringResource(Res.string.menus_empty_body),
                        horizontalPadding = 0.dp,
                    ) {
                        PrimaryButton(stringResource(Res.string.menus_empty_action), onScan, Modifier.fillMaxWidth())
                    }
                }
            }
            items(ready.menus, key = { it.cacheKey }) { menu ->
                SwipeToDelete(
                    onDelete = { onDelete(menu.cacheKey) },
                    modifier = Modifier.animateItem(),
                    hint = menu == ready.menus.first() && Tip.SwipeToDelete !in seenTips,
                    onHinted = { tips.markSeen(Tip.SwipeToDelete) },
                ) {
                    SavedMenuCard(
                        menu = menu,
                        nowMillis = ready.nowMillis,
                        onOpen = { navigate(Route.Menu(MenuSource.Saved(menu.cacheKey))) },
                        onDelete = { onDelete(menu.cacheKey) },
                    )
                }
            }
        }
        ScrollTitleBar(stringResource(Res.string.menus_title), collapsed, Modifier.align(Alignment.TopCenter))
    }
}

/** Swipe a card away to delete it; the coral behind it says what the swipe will do. */
@Composable
private fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    hint: Boolean = false,
    onHinted: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = Paper.colors
    val state = rememberSwipeToDismissBoxState()
    // The first time there is a menu to delete, the card slides aside by itself and springs back,
    // showing the Delete behind it: the gesture is taught by doing it, once, with no words.
    val peek = remember { Animatable(0f) }
    val peekDistance = with(LocalDensity.current) { PEEK.toPx() }
    val reduceMotion = Paper.reduceMotion
    LaunchedEffect(hint) {
        if (!hint || reduceMotion) return@LaunchedEffect
        delay(PEEK_DELAY_MS)
        peek.animateTo(-peekDistance, tween(PEEK_OUT_MS, easing = Motion.standard))
        delay(PEEK_HOLD_MS)
        peek.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        onHinted()
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(Shapes.card)
                    .background(colors.seal)
                    .padding(horizontal = Space.gutter),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(stringResource(Res.string.menus_delete), style = Paper.type.button, color = colors.onSeal)
            }
        },
    ) { Box(Modifier.graphicsLayer { translationX = peek.value }) { content() } }
}

@Composable
private fun SavedMenuCard(
    menu: SavedMenuItem,
    nowMillis: Long,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Paper.colors
    val deleteLabel = stringResource(Res.string.menus_delete)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onOpen)
            .clip(Shapes.card)
            .paper(colors.raised)
            .heightIn(min = 104.dp)
            // Swiping is invisible to screen readers, so delete is also offered as an action.
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
            }.padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = menu.photo
        if (photo != null) {
            MenuSnapshot(photo, pages = 1, description = stringResource(Res.string.menu_snapshot_description))
        } else {
            Box(Modifier.size(84.dp).background(colors.sealWash, CircleShape), contentAlignment = Alignment.Center) {
                DishPlate("🍽️", size = 60.dp)
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                menuTitle(menu.language, menu.venueType),
                style = Paper.type.dishName,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    if (menu.dishCount == 1) {
                        stringResource(Res.string.menu_context_one_dish)
                    } else {
                        stringResource(Res.string.menu_context_dishes, menu.dishCount)
                    },
                    savedAgo(menu.savedAtMillis, nowMillis),
                ).joinToString(stringResource(Res.string.separator_dot)),
                style = Paper.type.caption,
                color = colors.inkMuted,
            )
        }
        Icon(
            PaperIcons.ChevronRight,
            contentDescription = null,
            tint = colors.inkFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun savedAgo(
    savedAtMillis: Long,
    nowMillis: Long,
): String {
    val days = ((nowMillis - savedAtMillis) / DAY_MILLIS).toInt().coerceAtLeast(0)
    return when (days) {
        0 -> stringResource(Res.string.menus_today)
        1 -> stringResource(Res.string.menus_yesterday)
        else -> stringResource(Res.string.menus_days_ago, days)
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** How far the big title scrolls before the small one appears in the bar. */
private val TITLE_SCROLL_AWAY = 72.dp

private val PEEK = 88.dp
private const val PEEK_DELAY_MS = 900L
private const val PEEK_OUT_MS = 380
private const val PEEK_HOLD_MS = 450L
