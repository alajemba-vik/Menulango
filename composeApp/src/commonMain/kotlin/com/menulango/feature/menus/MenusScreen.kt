package com.menulango.feature.menus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.menulango.core.ui.InlineSearchRow
import com.menulango.core.ui.MenuSnapshot
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.ScrollTitleBar
import com.menulango.core.ui.SearchTag
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.bigTitleFade
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.pressable
import com.menulango.data.menu.ActiveScans
import com.menulango.data.menu.local.SavedMenuItem
import com.menulango.data.menu.model.Dish
import com.menulango.data.search.TextSearch
import com.menulango.data.tips.Tip
import com.menulango.data.tips.Tips
import com.menulango.feature.menu.FilterPill
import com.menulango.feature.menu.menuTitle
import com.menulango.resources.Res
import com.menulango.resources.list_joiner
import com.menulango.resources.menu_context_dishes
import com.menulango.resources.menu_context_one_dish
import com.menulango.resources.menu_snapshot_description
import com.menulango.resources.menus_days_ago
import com.menulango.resources.menus_delete
import com.menulango.resources.menus_deleted
import com.menulango.resources.menus_empty_action
import com.menulango.resources.menus_empty_body
import com.menulango.resources.menus_empty_title
import com.menulango.resources.menus_journal_earlier_open
import com.menulango.resources.menus_journal_open
import com.menulango.resources.menus_reading_body
import com.menulango.resources.menus_reading_page
import com.menulango.resources.menus_reading_title
import com.menulango.resources.menus_subtitle
import com.menulango.resources.menus_title
import com.menulango.resources.menus_today
import com.menulango.resources.menus_undo
import com.menulango.resources.menus_when_all
import com.menulango.resources.menus_when_month
import com.menulango.resources.menus_when_older
import com.menulango.resources.menus_when_week
import com.menulango.resources.menus_yesterday
import com.menulango.resources.search_menus_hint
import com.menulango.resources.search_menus_in_dishes
import com.menulango.resources.search_menus_none
import com.menulango.resources.search_open
import com.menulango.resources.separator_dot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
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
        // Scans still reading: before their first page is saved they get a card of their own;
        // after, their saved card says it is still being read.
        val scans =
            koinInject<ActiveScans>()
                .scans
                .collectAsState()
                .value.values
                .filter { it.pagesRead < it.pagesTotal }
        val readingKeys = scans.mapNotNull { it.cacheKey }.toSet()
        // Search is local: by each menu's title, and by the dishes inside only with the tag on.
        var searching by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        var searchDishes by rememberSaveable { mutableStateOf(false) }
        var period by rememberSaveable { mutableStateOf(MenuPeriod.All) }
        var journalFor by remember { mutableStateOf<String?>(null) }
        val dishes by viewModel.dishes.collectAsState()
        val marks by viewModel.marks.collectAsState()
        val titles =
            ready.menus.associate {
                it.cacheKey to (marks[it.cacheKey]?.name ?: menuTitle(it.language, it.venueType))
            }
        val shown =
            ready.menus.filter { menu ->
                period.contains(menu.savedAtMillis, ready.nowMillis) &&
                    TextSearch.matches(
                        query,
                        buildList {
                            add(titles[menu.cacheKey])
                            if (searchDishes) {
                                dishes[menu.cacheKey].orEmpty().forEach { dish ->
                                    add(dish.readableName)
                                    add(dish.originalName)
                                }
                            }
                        },
                    )
            }
        val list = rememberLazyListState()
        // Arriving on the page, the list is simply in its current order: changes made while the
        // diner was elsewhere don't all slide into place at once. Only what changes while they
        // watch (a delete, a scan finishing) animates.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(SETTLE_MS)
            settled = true
        }
        val titleGone = with(LocalDensity.current) { TITLE_SCROLL_AWAY.roundToPx() }
        val progress = {
            if (list.firstVisibleItemIndex >
                0
            ) {
                1f
            } else {
                (list.firstVisibleItemScrollOffset / titleGone.toFloat()).coerceIn(0f, 1f)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "title") {
                Column(Modifier.statusBarsPadding().padding(top = Space.xl, bottom = Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.menus_title),
                            style = Paper.type.hero,
                            color = colors.ink,
                            modifier = Modifier.weight(1f).bigTitleFade(progress).semantics { heading() },
                        )
                    }
                    Text(
                        stringResource(Res.string.menus_subtitle),
                        style = Paper.type.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
            // Search leads the date filters: tapped, it grows into a full-width field and the
            // filters move down beneath it, as on a menu's own page.
            if (ready.menus.isNotEmpty()) {
                item(key = "filters") {
                    InlineSearchRow(
                        open = searching,
                        onOpen = { searching = true },
                        onClose = {
                            searching = false
                            query = ""
                            searchDishes = false
                        },
                        query = query,
                        onQuery = { query = it },
                        label = stringResource(Res.string.search_open),
                        placeholder = stringResource(Res.string.search_menus_hint),
                        tags =
                            listOf(
                                SearchTag(stringResource(Res.string.search_menus_in_dishes), searchDishes) {
                                    searchDishes = !searchDishes
                                },
                            ),
                        modifier = Modifier.padding(bottom = Space.xs),
                    ) {
                        // Filtering by when, as Photos and Files do.
                        MenuPeriod.entries.forEach { option ->
                            FilterPill(
                                text = stringResource(option.label),
                                selected = option == period,
                                onClick = { period = option },
                            )
                        }
                    }
                }
            }
            if (ready.menus.isNotEmpty() && shown.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        stringResource(Res.string.search_menus_none),
                        style = Paper.type.bodySmall,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(vertical = Space.md),
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
            items(scans.filter { it.cacheKey == null }, key = { "scan-${it.id}" }) { scan ->
                ReadingMenuCard(scan, if (settled) Modifier.animateItem() else Modifier)
            }
            items(shown, key = { it.cacheKey }) { menu ->
                SwipeToDelete(
                    onDelete = { onDelete(menu.cacheKey) },
                    modifier = if (settled) Modifier.animateItem() else Modifier,
                    hint = menu == ready.menus.first() && Tip.SwipeToDelete !in seenTips,
                    onHinted = { tips.markSeen(Tip.SwipeToDelete) },
                ) {
                    SavedMenuCard(
                        menu = menu,
                        title = titles[menu.cacheKey].orEmpty(),
                        summary = dishes[menu.cacheKey]?.let { summaryOf(it) },
                        picked = marks[menu.cacheKey]?.picked?.size ?: 0,
                        pickedBefore = marks[menu.cacheKey]?.earlierPicks?.isNotEmpty() == true,
                        onJournal = { journalFor = menu.cacheKey },
                        nowMillis = ready.nowMillis,
                        stillReading = menu.cacheKey in readingKeys,
                        onOpen = { navigate(Route.Menu(MenuSource.Saved(menu.cacheKey))) },
                        onDelete = { onDelete(menu.cacheKey) },
                    )
                }
            }
        }
        ScrollTitleBar(stringResource(Res.string.menus_title), progress, Modifier.align(Alignment.TopCenter))
        journalFor?.let { key ->
            val all = dishes[key].orEmpty()
            val mark = marks[key]
            PickJournalSheet(
                title = titles[key].orEmpty(),
                picked = mark?.picked.orEmpty().mapNotNull { id -> all.firstOrNull { it.id == id } },
                earlier = mark?.earlierPicks.orEmpty().mapNotNull { id -> all.firstOrNull { it.id == id } },
                notes = mark?.notes.orEmpty(),
                onNote = { dishId, text -> viewModel.note(key, dishId, text) },
                onOpenMenu = {
                    journalFor = null
                    navigate(Route.Menu(MenuSource.Saved(key)))
                },
                onDismiss = { journalFor = null },
            )
        }
    }
}

/** Swipe a card away to delete it; the aubergine behind it says what the swipe will do. */
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
    // The list saves this state under the menu's key, so a card brought back by Undo would return
    // still swiped away; it always comes back settled.
    LaunchedEffect(Unit) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) state.snapTo(SwipeToDismissBoxValue.Settled)
    }
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
    title: String,
    summary: String?,
    picked: Int,
    pickedBefore: Boolean,
    onJournal: () -> Unit,
    nowMillis: Long,
    stillReading: Boolean = false,
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
                title,
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
            // What the menu was mostly made of, so a long list of menus stays recognisable.
            summary?.let {
                Text(
                    it,
                    style = Paper.type.caption,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stillReading) ReadingNote(Modifier.padding(top = Space.xs))
            // The dishes picked here, and the diner's notes on them: a quiet way back to "what was
            // that great thing we had?".
            if (picked > 0 || pickedBefore) {
                Text(
                    if (picked > 0) {
                        stringResource(Res.string.menus_journal_open, picked)
                    } else {
                        stringResource(Res.string.menus_journal_earlier_open)
                    },
                    style = Paper.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.sealInk,
                    modifier = Modifier.clip(Shapes.chip).pressable(onJournal).padding(vertical = Space.xs),
                )
            }
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

/** A menu left while its first page was still being read: its photo, and what is happening. */
@Composable
private fun ReadingMenuCard(
    scan: ActiveScans.Scan,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .paper(colors.raised)
            .heightIn(min = 104.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        scan.cover?.let {
            MenuSnapshot(
                it,
                pages = scan.pagesTotal,
                description = stringResource(Res.string.menu_snapshot_description),
            )
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.menus_reading_title),
                style = Paper.type.dishName,
                color = colors.ink,
            )
            ReadingNote(Modifier.padding(top = Space.xs), scan)
        }
    }
}

/** "Reading the menu…", or which page it is on for longer menus. */
@Composable
private fun ReadingNote(
    modifier: Modifier = Modifier,
    scan: ActiveScans.Scan? = null,
) {
    val colors = Paper.colors
    Text(
        if (scan != null && scan.pagesTotal > 1) {
            stringResource(Res.string.menus_reading_page, scan.pagesRead + 1, scan.pagesTotal)
        } else {
            stringResource(Res.string.menus_reading_body)
        },
        style = Paper.type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colors.sealInk,
        modifier =
            modifier
                .clip(Shapes.chip)
                .background(colors.sealWash)
                .padding(horizontal = Space.sm, vertical = 4.dp),
    )
}

/** When a menu was read, for the filter above the list. */
internal enum class MenuPeriod(
    val label: StringResource,
) {
    All(Res.string.menus_when_all),
    Week(Res.string.menus_when_week),
    Month(Res.string.menus_when_month),
    Older(Res.string.menus_when_older),
    ;

    fun contains(
        savedAt: Long,
        now: Long,
    ): Boolean {
        val age = now - savedAt
        return when (this) {
            All -> true
            Week -> age <= 7 * DAY_MS
            Month -> age <= 30 * DAY_MS
            Older -> age > 30 * DAY_MS
        }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val SETTLE_MS = 400L

/**
 * The few ingredients that run through a menu, in the words the menu was explained in:
 * "yoghurt, rice and lamb". Only ingredients in at least two dishes count; a menu without
 * repeats gets no summary rather than a misleading one.
 */
@Composable
private fun summaryOf(dishes: List<Dish>): String? {
    val common =
        dishes
            .flatMap { dish -> dish.ingredients.map { it.trim().lowercase() }.distinct() }
            .filter { it.isNotEmpty() && it.length <= SUMMARY_WORD_MAX }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    return common.takeIf { it.isNotEmpty() }?.joinToString(stringResource(Res.string.list_joiner))
}

private const val SUMMARY_WORD_MAX = 24
