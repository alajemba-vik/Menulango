package com.menulango.feature.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.MenuSource
import com.menulango.Route
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.CollapsingHeader
import com.menulango.core.ui.DealtItems
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.DishRowPlaceholder
import com.menulango.core.ui.ErrorMessage
import com.menulango.core.ui.FlagChip
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.MenuSnapshot
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SectionHeading
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.chips
import com.menulango.core.ui.dealtIn
import com.menulango.core.ui.emoji
import com.menulango.core.ui.felt
import com.menulango.core.ui.foodGroup
import com.menulango.core.ui.paper
import com.menulango.core.ui.pressable
import com.menulango.core.ui.rememberLastNonNull
import com.menulango.core.ui.tipTarget
import com.menulango.core.ui.weave
import com.menulango.data.menu.model.Dish
import com.menulango.data.quota.ScanQuota
import com.menulango.data.tips.Tip
import com.menulango.feature.choose.ChoiceMode
import com.menulango.feature.choose.MoodDeck
import com.menulango.feature.choose.dishHistoryKey
import com.menulango.feature.choose.hint
import com.menulango.feature.dish.DishHistoryControl
import com.menulango.feature.dish.DishOrderControl
import com.menulango.feature.dish.DishSheet
import com.menulango.feature.order.AddToOrderBadge
import com.menulango.feature.order.NoteEditor
import com.menulango.feature.order.OrderBar
import com.menulango.feature.order.OrderSheet
import com.menulango.feature.order.TableOrder
import com.menulango.feature.order.WaiterView
import com.menulango.resources.Res
import com.menulango.resources.action_back
import com.menulango.resources.action_retake
import com.menulango.resources.action_try_again
import com.menulango.resources.capture_plus
import com.menulango.resources.choose_prompt
import com.menulango.resources.filter_avoid
import com.menulango.resources.filter_budget
import com.menulango.resources.filter_clear
import com.menulango.resources.filter_count
import com.menulango.resources.filter_local
import com.menulango.resources.filter_no_offal
import com.menulango.resources.filter_no_pork
import com.menulango.resources.filter_none_body
import com.menulango.resources.filter_none_title
import com.menulango.resources.filter_not_spicy
import com.menulango.resources.filter_nothing_raw
import com.menulango.resources.filter_to_share
import com.menulango.resources.filter_vegan
import com.menulango.resources.filter_vegetarian
import com.menulango.resources.list_joiner
import com.menulango.resources.menu_add_page
import com.menulango.resources.menu_add_page_description
import com.menulango.resources.menu_choose_title
import com.menulango.resources.menu_context_average
import com.menulango.resources.menu_context_dishes
import com.menulango.resources.menu_context_one_dish
import com.menulango.resources.menu_context_pages
import com.menulango.resources.menu_empty_body
import com.menulango.resources.menu_empty_title
import com.menulango.resources.menu_featured_title
import com.menulango.resources.menu_featured_why
import com.menulango.resources.menu_known
import com.menulango.resources.menu_last_free_scan
import com.menulango.resources.menu_more_pages_action
import com.menulango.resources.menu_more_pages_body
import com.menulango.resources.menu_more_pages_plus
import com.menulango.resources.menu_more_pages_title
import com.menulango.resources.menu_page_jump
import com.menulango.resources.menu_page_marker
import com.menulango.resources.menu_page_position
import com.menulango.resources.menu_page_unreadable
import com.menulango.resources.menu_partial
import com.menulango.resources.menu_plus_badge
import com.menulango.resources.menu_reading
import com.menulango.resources.menu_reading_more
import com.menulango.resources.menu_reading_page
import com.menulango.resources.menu_reading_waiting
import com.menulango.resources.menu_snapshot_description
import com.menulango.resources.menu_title_language
import com.menulango.resources.menu_title_unknown
import com.menulango.resources.menu_title_venue
import com.menulango.resources.menu_truncated
import com.menulango.resources.menus_undo
import com.menulango.resources.mode_new
import com.menulango.resources.mode_only_here
import com.menulango.resources.mode_simple
import com.menulango.resources.mode_special
import com.menulango.resources.note_add
import com.menulango.resources.note_added
import com.menulango.resources.order_cleared
import com.menulango.resources.separator_dot
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun MenuScreen(
    source: MenuSource,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    navigate: (Route) -> Unit,
) {
    val viewModel = koinViewModel<MenuViewModel> { parametersOf(source) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val order by viewModel.order.collectAsStateWithLifecycle()
    val tone by viewModel.headerTone.collectAsStateWithLifecycle()
    val ready = state as? MenuUiState.Ready

    BackGesture(enabled = ready?.selectedDishId != null) { viewModel.dismissDish() }

    MenuContent(
        state = state,
        tone = tone,
        order = order,
        onOrderChange = viewModel::updateOrder,
        actions =
            MenuActions(
                onBack = onBack,
                onRetake = onRetake,
                onRetry = viewModel::retry,
                onSelectDish = viewModel::selectDish,
                onDismissDish = viewModel::dismissDish,
                onEaten = viewModel::setEaten,
                onChoose = { mode -> viewModel.routeForMode(mode)?.let(navigate) },
                onToggleFilter = viewModel::toggleFilter,
                onClearFilters = viewModel::clearFilters,
                onDropAvoid = viewModel::dropAvoid,
                onAddPage = { navigate(viewModel.routeForAddPage()) },
            ),
    )
}

internal data class MenuActions(
    val onBack: () -> Unit,
    val onRetake: () -> Unit,
    val onRetry: () -> Unit,
    val onSelectDish: (String) -> Unit,
    val onDismissDish: () -> Unit,
    val onEaten: (Dish, Boolean) -> Unit,
    val onChoose: (ChoiceMode) -> Unit,
    val onToggleFilter: (DishFilter) -> Unit,
    val onClearFilters: () -> Unit,
    val onDropAvoid: (String) -> Unit,
    val onAddPage: () -> Unit,
) {
    companion object {
        val Preview = MenuActions({}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})
    }
}

/**
 * The workhorse screen: a coral felt header with what the menu is, and a cream felt sheet carrying
 * the dishes as cards. The table's order gathers at the bottom, like a basket.
 */
@Composable
internal fun MenuContent(
    state: MenuUiState,
    actions: MenuActions,
    tone: HeaderTone = HeaderTone.Brand,
    order: TableOrder = TableOrder(),
    onOrderChange: ((TableOrder) -> TableOrder) -> Unit = {},
) {
    val ready = state as? MenuUiState.Ready
    val selected = ready?.selectedDish
    val reduceMotion = Paper.reduceMotion
    val density = LocalDensity.current
    val collapsedPx = WindowInsets.statusBars.getTop(density) + with(density) { COLLAPSED_HEADER.toPx() }
    val header = remember(collapsedPx) { CollapsingHeader(collapsedPx) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var noting by remember { mutableStateOf<Dish?>(null) }
    // Adding a pick is the moment to say how you want it, so the message offers exactly that.
    val addPick: (Dish) -> Unit = { dish ->
        onOrderChange { it.add(dish) }
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            val result =
                snackbar.showSnackbar(
                    getString(Res.string.note_added),
                    getString(Res.string.note_add),
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) noting = dish
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = if (reduceMotion) null else this
        Box(Modifier.fillMaxSize()) {
            // The screen sits on felt in the restaurant's own colour, read from the photo of its menu
            // before the menu is shown, or MenuLango coral for menus printed without colour. The
            // menu's sheet of cream felt rises over it.
            val colors = Paper.colors
            val (headerFelt, onHeader) =
                when (tone) {
                    HeaderTone.Pending -> colors.sunk to colors.ink

                    HeaderTone.Brand -> colors.seal to colors.onSeal

                    // White always reads on the menu's colour: it was darkened until it does.
                    is HeaderTone.Menu -> tone.colour to Color.White
                }
            Box(Modifier.fillMaxSize().felt(headerFelt))

            Column(Modifier.fillMaxSize()) {
                MenuHeader(state, actions, header, onHeader)
                Column(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(header.connection)
                        .felt(Paper.colors.paper, Shapes.sheet),
                ) {
                    when (state) {
                        is MenuUiState.Loading -> {
                            ReadingPlaceholder()
                        }

                        is MenuUiState.Ready -> {
                            DishList(state, actions, sharedScope, order, addPick)
                        }

                        is MenuUiState.Empty -> {
                            StateMessage(
                                stringResource(Res.string.menu_empty_title),
                                stringResource(Res.string.menu_empty_body),
                            ) {
                                PrimaryButton(
                                    stringResource(Res.string.action_retake),
                                    actions.onRetake,
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        is MenuUiState.Failed -> {
                            ErrorMessage(state.error) {
                                PrimaryButton(
                                    stringResource(Res.string.action_try_again),
                                    actions.onRetry,
                                    Modifier.fillMaxWidth(),
                                )
                                SecondaryButton(
                                    stringResource(Res.string.action_retake),
                                    actions.onRetake,
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            var choosing by remember { mutableStateOf(false) }
            var showingOrder by remember { mutableStateOf(false) }
            var showingWaiter by remember { mutableStateOf(false) }
            val currency = currencyPrefix(ready?.meta?.currency)
            AnimatedVisibility(
                visible = ready != null && !ready.isReading && selected == null,
                enter =
                    fadeIn(tween(Motion.QUICK_MS)) +
                        slideInVertically(tween(Motion.SHEET_MS, easing = Motion.settle)) { it },
                exit = fadeOut(tween(Motion.QUICK_MS)) + slideOutVertically(tween(Motion.QUICK_MS)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // An empty order leaves just "Help me choose"; once something is ordered, the basket leads.
                if (order.isEmpty) {
                    ChooseButton(isPlus = ready?.isPlus == true, onClick = { choosing = true })
                } else {
                    OrderBar(
                        modifier = Modifier.tipTarget(Tip.Picks),
                        order = order,
                        currencyPrefix = currency,
                        onOpen = { showingOrder = true },
                        onChoose = { choosing = true },
                        onClear = {
                            val before = order
                            onOrderChange { it.cleared() }
                            scope.launch {
                                val undo =
                                    snackbar.showSnackbar(
                                        getString(Res.string.order_cleared),
                                        getString(Res.string.menus_undo),
                                        duration = SnackbarDuration.Short,
                                    )
                                if (undo == SnackbarResult.ActionPerformed) onOrderChange { before }
                            }
                        },
                    )
                }
            }
            if (showingOrder) {
                OrderSheet(
                    order = order,
                    currencyPrefix = currency,
                    onChange = onOrderChange,
                    onShowWaiter = {
                        showingOrder = false
                        showingWaiter = true
                    },
                    onDismiss = { showingOrder = false },
                )
            }
            if (choosing) {
                MoodSheet(
                    onDismiss = { choosing = false },
                    onChoose = { mode ->
                        choosing = false
                        actions.onChoose(mode)
                    },
                )
            }

            // Tapping the dimmed list above the sheet closes it.
            if (selected != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource =
                                remember {
                                    MutableInteractionSource()
                                },
                            indication = null,
                            onClick = actions.onDismissDish,
                        ),
                )
            }

            val sheetDish = rememberLastNonNull(selected)
            AnimatedVisibility(
                visible = selected != null,
                enter =
                    if (reduceMotion) {
                        fadeIn(tween(Motion.SHEET_MS))
                    } else {
                        slideInVertically(tween(Motion.SHEET_MS, easing = Motion.standard)) { it / 2 } +
                            fadeIn(tween(Motion.QUICK_MS))
                    },
                exit =
                    if (reduceMotion) {
                        fadeOut(tween(Motion.QUICK_MS))
                    } else {
                        slideOutVertically(tween(Motion.SHEET_MS, easing = Motion.standard)) { it / 2 } +
                            fadeOut(tween(Motion.SHEET_MS))
                    },
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            ) {
                sheetDish?.let { dish ->
                    DishSheet(
                        dish = dish,
                        onDismiss = actions.onDismissDish,
                        nameModifier = Modifier.sharedDishName(sharedScope, this, dish.id),
                        order = DishOrderControl(order.quantityOf(dish.id)) { addPick(dish) },
                        history =
                            if (ready?.isPlus == true) {
                                DishHistoryControl(
                                    eaten = dishHistoryKey(dish) in ready.eatenKeys,
                                ) { actions.onEaten(dish, it) }
                            } else {
                                null
                            },
                    )
                }
            }

            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
            ) { data ->
                Snackbar(
                    data,
                    shape = Shapes.tile,
                    containerColor = Paper.colors.ink,
                    contentColor = Paper.colors.paper,
                    actionColor = Paper.colors.seal,
                )
            }

            noting?.let { dish ->
                NoteEditor(
                    dish = dish,
                    initial = order.lineFor(dish.id, order.activeDinerId)?.note.orEmpty(),
                    onSave = { note ->
                        onOrderChange { it.setNote(dish.id, it.activeDinerId, note) }
                        noting = null
                    },
                    onDismiss = { noting = null },
                )
            }

            BackGesture(enabled = showingWaiter) { showingWaiter = false }
            AnimatedVisibility(
                visible = showingWaiter,
                enter = fadeIn(tween(Motion.SHEET_MS)),
                exit = fadeOut(tween(Motion.QUICK_MS)),
            ) {
                WaiterView(
                    order = order,
                    // Older saved menus may have only a two-letter value in `language`; fresh
                    // scans carry a proper tag. Never guess from a human-readable label.
                    restaurantLanguageTag = ready?.meta?.languageTag ?: ready?.meta?.language?.takeIf { it.isLanguageTag() },
                    onTranslationsReady = { translations -> onOrderChange { it.saveWaiterTranslations(translations) } },
                    onClose = { showingWaiter = false },
                )
            }
        }
    }
}

private fun String.isLanguageTag(): Boolean = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$").matches(this)

@Composable
private fun Modifier.sharedDishName(
    sharedScope: SharedTransitionScope?,
    visibilityScope: AnimatedVisibilityScope,
    dishId: String,
): Modifier {
    if (sharedScope == null) return this
    return with(sharedScope) {
        this@sharedDishName.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "dish-name-$dishId"),
            animatedVisibilityScope = visibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            boundsTransform = { _, _ -> tween(Motion.SHEET_MS, easing = Motion.standard) },
        )
    }
}

@Composable
private fun ReadingPlaceholder() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SectionLabel(stringResource(Res.string.menu_reading))
        listOf(190.dp, 150.dp, 210.dp, 130.dp, 170.dp).forEach { width ->
            DishRowPlaceholder(nameWidth = width)
        }
    }
}

@Composable
private fun DishList(
    state: MenuUiState.Ready,
    actions: MenuActions,
    sharedScope: SharedTransitionScope?,
    order: TableOrder,
    onAdd: (Dish) -> Unit,
) {
    val dealt = remember { DealtItems() }
    val listState = rememberLazyListState()
    val dishes = state.visibleDishes
    val sections = remember(dishes) { dishes.map { it.section } }
    val gutter = Modifier.padding(horizontal = Space.gutter)
    val hasFilters = state.filterOptions.isNotEmpty() || state.avoid.isNotEmpty()
    val featured = remember(state.dishes) { state.dishes.filter { it.flags.localSpecialty }.take(FEATURED_MAX) }
    // The picks are only fair once the whole menu is read, so they arrive once, at the end, rather
    // than jumping in above dishes the diner is already reading.
    val showFeatured = state.showFeatured && featured.isNotEmpty() && !state.isFiltering && !state.isReading
    // A new set of filters is a new list: start it from the top rather than halfway down.
    LaunchedEffect(state.filters) { listState.scrollToItem(0) }
    LaunchedEffect(showFeatured) {
        if (showFeatured && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }
    val pageOf = state.pages?.pageOfDish.orEmpty()
    val pageCount = state.pages?.total ?: 1
    // Items before the first dish, so a dish's place in the list can be found without scrolling to it.
    val leadingItems = listOf(hasFilters, dishes.isEmpty() && state.isFiltering, showFeatured).count { it }
    Box(Modifier.fillMaxSize()) {
        // No horizontal content padding: the filter pills scroll edge to edge, so each item insets itself.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The pinned filter bar carries its own top space, so pills never sit on the sheet's edge.
            contentPadding = PaddingValues(top = if (hasFilters) 0.dp else Space.gutter, bottom = CHOOSE_BAR_CLEARANCE),
        ) {
            if (hasFilters) {
                stickyHeader(key = "filters") {
                    FilterBar(state, actions)
                }
            }
            if (dishes.isEmpty() && state.isFiltering) {
                item(key = "filtered-out") {
                    StateMessage(
                        stringResource(Res.string.filter_none_title),
                        stringResource(Res.string.filter_none_body),
                    ) {
                        SecondaryButton(
                            stringResource(Res.string.filter_clear),
                            actions.onClearFilters,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (showFeatured) {
                item(key = "featured") {
                    FeaturedDishes(featured, actions.onSelectDish, Modifier.animateItem())
                }
            }
            itemsIndexed(dishes, key = { _, dish -> dish.id }) { index, dish ->
                val section = sections[index]
                Column(gutter.dealtIn(dish.id, dealt)) {
                    val page = pageOf[dish.id]
                    if (pageCount > 1 && page != null && page != dishes.getOrNull(index - 1)?.let { pageOf[it.id] }) {
                        PageDivider(page, Modifier.padding(top = if (index == 0) Space.xs else Space.section))
                    }
                    if (section != null && section != sections.getOrNull(index - 1)) {
                        SectionHeading(section, Modifier.padding(top = Space.section - Space.sm, bottom = Space.sm))
                    }
                    DishRow(
                        isFirst = index == 0,
                        dish = dish,
                        tint = Paper.colors.food(dish.foodGroup()),
                        selectedDishId = state.selectedDishId,
                        onClick = { actions.onSelectDish(dish.id) },
                        sharedScope = sharedScope,
                        quantity = order.quantityOf(dish.id),
                        onAdd = { onAdd(dish) },
                    )
                    Spacer(Modifier.height(Space.sm))
                }
            }
            if (state.isReading) {
                item(key = "reading") {
                    val pages = state.pages
                    Column(gutter) {
                        DishRowPlaceholder()
                        Text(
                            when {
                                pages?.waiting == true -> {
                                    stringResource(Res.string.menu_reading_waiting)
                                }

                                pages?.reading != null && pages.total > 1 -> {
                                    stringResource(Res.string.menu_reading_page, pages.reading, pages.total)
                                }

                                else -> {
                                    stringResource(Res.string.menu_reading_more)
                                }
                            },
                            style = Paper.type.caption,
                            color = Paper.colors.inkFaint,
                            modifier = Modifier.padding(vertical = Space.related),
                        )
                    }
                }
            }
            state.pages?.unreadable?.takeIf { it.isNotEmpty() }?.let { unreadable ->
                item(key = "unreadable") {
                    Text(
                        stringResource(
                            Res.string.menu_page_unreadable,
                            unreadable.joinToString(stringResource(Res.string.list_joiner)),
                        ),
                        style = Paper.type.caption,
                        color = Paper.colors.inkMuted,
                        modifier = gutter.padding(top = Space.gutter),
                    )
                }
            }
            state.pages?.takeIf { !state.isReading && !state.isFiltering }?.let { pages ->
                item(key = "more-pages") {
                    MorePagesCard(pages, actions.onAddPage, gutter.padding(top = Space.gutter))
                }
            }
            state.notice?.let { notice ->
                item(key = "notice") {
                    Text(
                        stringResource(notice.text()),
                        style = Paper.type.caption,
                        color = Paper.colors.inkMuted,
                        modifier = gutter.padding(top = Space.gutter),
                    )
                }
            }
        }
        if (pageCount > 1) {
            PageChip(
                listState = listState,
                pageOf = pageOf,
                pageCount = pageCount,
                dishes = dishes,
                leadingItems = leadingItems,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (hasFilters) 84.dp else Space.sm),
            )
        }
    }
}

/**
 * The diner's own filters, pinned above the dishes as they scroll: pills for the facts that would
 * actually narrow this menu, and — once any is on — how many dishes are left and a way back.
 */
@Composable
private fun FilterBar(
    state: MenuUiState.Ready,
    actions: MenuActions,
) {
    val colors = Paper.colors
    Column(Modifier.fillMaxWidth().felt(colors.paper).padding(top = Space.gutter, bottom = Space.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.related),
        ) {
            // The diner's own words come first: they chose them, so they should see them working.
            state.avoid.sorted().forEach { word ->
                FilterPill(
                    text = stringResource(Res.string.filter_avoid, word),
                    selected = true,
                    onClick = { actions.onDropAvoid(word) },
                )
            }
            state.filterOptions.forEach { filter ->
                FilterPill(
                    text = filter.label(state),
                    selected = filter in state.filters,
                    onClick = { actions.onToggleFilter(filter) },
                )
            }
        }
        if (state.isFiltering) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.filter_count, state.visibleDishes.size, state.dishes.size),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                QuietButton(stringResource(Res.string.filter_clear), actions.onClearFilters, color = colors.sealInk)
            }
        }
    }
}

@Composable
internal fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    unselected: Color = Paper.colors.raised,
) {
    val colors = Paper.colors
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(Shapes.chip)
            .weave(if (selected) colors.seal else unselected)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) {
            Icon(PaperIcons.Check, contentDescription = null, tint = colors.onSeal, modifier = Modifier.size(16.dp))
        }
        Text(
            text,
            style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onSeal else colors.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun DishFilter.label(state: MenuUiState.Ready): String =
    if (this == DishFilter.Budget) {
        val limit =
            state.budgetLimit
                ?.toInt()
                ?.toString()
                .orEmpty()
        stringResource(Res.string.filter_budget, currencyPrefix(state.meta.currency) + limit)
    } else {
        label()
    }

/** The filter's name; the budget pill names its price, which only a menu can supply. */
@Composable
internal fun DishFilter.label(): String =
    when (this) {
        DishFilter.Vegetarian -> {
            stringResource(Res.string.filter_vegetarian)
        }

        DishFilter.Vegan -> {
            stringResource(Res.string.filter_vegan)
        }

        DishFilter.NoPork -> {
            stringResource(Res.string.filter_no_pork)
        }

        DishFilter.NotSpicy -> {
            stringResource(Res.string.filter_not_spicy)
        }

        DishFilter.NothingRaw -> {
            stringResource(Res.string.filter_nothing_raw)
        }

        DishFilter.NoOffal -> {
            stringResource(Res.string.filter_no_offal)
        }

        DishFilter.Local -> {
            stringResource(Res.string.filter_local)
        }

        DishFilter.ToShare -> {
            stringResource(Res.string.filter_to_share)
        }

        DishFilter.Budget -> {
            stringResource(Res.string.filter_budget, "")
        }
    }

private fun MenuNotice.text(): StringResource =
    when (this) {
        MenuNotice.Partial -> Res.string.menu_partial
        MenuNotice.Truncated -> Res.string.menu_truncated
        MenuNotice.KnownMenu -> Res.string.menu_known
        MenuNotice.LastFreeScan -> Res.string.menu_last_free_scan
    }

/**
 * The top of the menu, on coral felt: what the menu is — "Greek taverna" — set large, the plain
 * facts, a few plates from it, and the diner's own photo pinned beside them as a snapshot.
 */
@Composable
private fun MenuHeader(
    state: MenuUiState,
    actions: MenuActions,
    header: CollapsingHeader,
    onHeader: Color,
) {
    val colors = Paper.colors
    val type = Paper.type
    val ready = state as? MenuUiState.Ready
    val title = if (ready == null) stringResource(headerFallback(state)) else menuTitle(ready)
    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            // Measured at its natural height, laid out at whatever height the list has left it.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                // Until the first measurement lands, show the header whole rather than flashing it shut.
                val target = if (header.expandedPx == 0f) placeable.height else header.heightPx.roundToInt()
                val height = target.coerceIn(0, placeable.height)
                layout(placeable.width, height) { placeable.place(0, 0) }
            },
    ) {
        Column(
            Modifier
                .onSizeChanged { header.expandedPx = it.height.toFloat() }
                .statusBarsPadding()
                .padding(start = Space.sm, end = Space.gutter, top = Space.xs, bottom = Space.section + Space.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    icon = PaperIcons.Back,
                    label = stringResource(Res.string.action_back),
                    onClick = actions.onBack,
                    tint = colors.ink,
                    background = colors.raised.copy(alpha = 0.92f),
                )
                // The compact title takes over as the big one scrolls away; screen readers hear the big one.
                Text(
                    title,
                    style = type.dishName,
                    color = onHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = Space.sm)
                            .graphicsLayer { alpha = ((header.progress - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                            .clearAndSetSemantics { },
                )
                if (ready?.pages != null) AddPageButton(actions.onAddPage)
            }
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.graphicsLayer { alpha = (1f - header.progress * 1.6f).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    Modifier.weight(1f).padding(start = Space.related),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text(
                        text = title,
                        style = type.hero,
                        color = onHeader,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    ready?.let {
                        MenuFacts(it, onHeader)
                        FloatingPlates(it.dishes, Modifier.padding(top = Space.sm))
                    }
                }
                state.photo?.let { photo ->
                    MenuSnapshot(
                        photo = photo,
                        pages = ready?.pages?.total ?: 1,
                        description = stringResource(Res.string.menu_snapshot_description),
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
            }
        }
    }
}

/** "+ Add page": photograph the next page of this menu without leaving it. */
@Composable
private fun AddPageButton(onClick: () -> Unit) {
    val colors = Paper.colors
    val description = stringResource(Res.string.menu_add_page_description)
    Row(
        Modifier
            .padding(end = Space.sm)
            .heightIn(min = Space.touchTarget)
            .pressable(onClick)
            .clip(Shapes.button)
            .background(colors.raised.copy(alpha = 0.92f))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = Space.sm, end = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(PaperIcons.Plus, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.menu_add_page),
            style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Where one photographed page ends and the next begins: a stitched seam with the page number. */
@Composable
private fun PageDivider(
    page: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val stitch = colors.inkFaint
    Row(modifier.fillMaxWidth().padding(bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.menu_page_marker, page).uppercase(),
            style = Paper.type.label,
            color = colors.inkMuted,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.width(Space.sm))
        Box(
            Modifier.weight(1f).height(2.dp).drawBehind {
                drawLine(
                    stitch,
                    Offset(0f, size.height / 2),
                    Offset(size.width, size.height / 2),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            },
        )
    }
}

/**
 * "Page 2 of 3": where the diner is in a long menu, floating over it. Tapping jumps to the start of
 * the next page, and from the last page back to the first.
 */
@Composable
private fun PageChip(
    listState: LazyListState,
    pageOf: Map<String, Int>,
    pageCount: Int,
    dishes: List<Dish>,
    leadingItems: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val scope = rememberCoroutineScope()
    val current by remember(pageOf) {
        derivedStateOf {
            // The page being read is the one a third of the way down the screen, where the eye
            // rests, not a sliver of card half hidden under the filters.
            val info = listState.layoutInfo
            val reading = info.viewportStartOffset + info.viewportSize.height * READING_LINE
            info.visibleItemsInfo
                .filter { it.offset + it.size > reading }
                .firstNotNullOfOrNull { item -> (item.key as? String)?.let(pageOf::get) }
                ?: info.visibleItemsInfo.lastOrNull()?.let { (it.key as? String)?.let(pageOf::get) }
                ?: 1
        }
    }
    val next = if (current >= pageCount) 1 else current + 1
    val target = dishes.indexOfFirst { pageOf[it.id] == next }
    val description = stringResource(Res.string.menu_page_jump, next)
    Row(
        modifier
            .pressable(onClick = {
                if (target >=
                    0
                ) {
                    scope.launch { listState.animateScrollToItem(leadingItems + target) }
                }
            })
            .shadow(Elevation.floating, Shapes.pill, clip = false)
            .clip(Shapes.pill)
            .background(colors.ink)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = Space.md, end = Space.sm, top = Space.related, bottom = Space.related),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            stringResource(Res.string.menu_page_position, current, pageCount),
            style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
            color = colors.paper,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Icon(
            PaperIcons.ChevronRight,
            contentDescription = null,
            tint = colors.paper,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (next == 1) -90f else 90f },
        )
    }
}

/**
 * At the end of a photographed menu: long menus run to several pages, so the next one is offered
 * where the diner runs out of dishes. Free menus say plainly how many pages they may have.
 */
@Composable
private fun MorePagesCard(
    pages: PageStatus,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(
        modifier
            .fillMaxWidth()
            .paper(colors.raised, Shapes.card)
            .padding(Space.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        Text(stringResource(Res.string.menu_more_pages_title), style = Paper.type.dishName, color = colors.ink)
        Text(
            if (pages.freePagesLeft == 0) {
                stringResource(Res.string.menu_more_pages_plus, ScanQuota.FREE_PAGES_PER_MENU)
            } else {
                stringResource(Res.string.menu_more_pages_body)
            },
            style = Paper.type.bodySmall,
            color = colors.inkMuted,
        )
        SecondaryButton(
            stringResource(Res.string.menu_more_pages_action),
            onAddPage,
            Modifier.fillMaxWidth().padding(top = Space.xs),
            icon = PaperIcons.Plus,
        )
    }
}

/** Three dishes from this menu under its name: a taste of what is on it before a word is read. */
@Composable
private fun FloatingPlates(
    dishes: List<Dish>,
    modifier: Modifier = Modifier,
) {
    val emojis = remember(dishes) { dishes.map { it.emoji() }.distinct().take(3) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        emojis.forEachIndexed { index, emoji ->
            DishPlate(
                emoji,
                size = 44.dp,
                // Laid down slightly askew, as dishes are on a table; still, not bobbing.
                modifier = Modifier.graphicsLayer { rotationZ = (index - 1) * 6f },
            )
        }
    }
}

private fun headerFallback(state: MenuUiState): StringResource =
    if (state is MenuUiState.Loading) Res.string.menu_reading else Res.string.menu_title_unknown

@Composable
private fun menuTitle(state: MenuUiState.Ready): String = menuTitle(state.meta.language, state.meta.venueType)

/** "Greek taverna", "Greek menu" or "Taverna" — whatever the model could tell about the menu. */
@Composable
internal fun menuTitle(
    language: String?,
    venueType: String?,
): String {
    val cuisine = language?.let(::languageName)
    val venue =
        venueType
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    return when {
        cuisine != null && venue != null -> stringResource(Res.string.menu_title_venue, cuisine, venue)
        cuisine != null -> stringResource(Res.string.menu_title_language, cuisine)
        venue != null -> venue.replaceFirstChar { it.uppercase() }
        else -> stringResource(Res.string.menu_title_unknown)
    }
}

/** "16 dishes · avg €10" under the header title. */
@Composable
private fun MenuFacts(
    state: MenuUiState.Ready,
    color: Color,
) {
    val facts = menuContextFacts(state.dishes, state.meta.language, state.meta.currency)
    val parts =
        listOfNotNull(
            if (facts.dishCount == 1) {
                stringResource(Res.string.menu_context_one_dish)
            } else {
                stringResource(Res.string.menu_context_dishes, facts.dishCount)
            },
            facts.averagePrice?.let { stringResource(Res.string.menu_context_average, it) },
            state.pages
                ?.total
                ?.takeIf { it > 1 }
                ?.let { stringResource(Res.string.menu_context_pages, it) },
        )
    Text(
        parts.joinToString(stringResource(Res.string.separator_dot)),
        style = Paper.type.bodySmall,
        color = color.copy(alpha = 0.85f),
    )
}

/**
 * One dish as a coloured card: the colour of its kind of food, the name and the name as printed, the price and
 * the facts as white pills, and its plate on the right.
 */
@Composable
private fun DishRow(
    isFirst: Boolean,
    dish: Dish,
    tint: Color,
    selectedDishId: String?,
    onClick: () -> Unit,
    sharedScope: SharedTransitionScope?,
    quantity: Int,
    onAdd: () -> Unit,
) {
    val colors = Paper.colors
    val type = Paper.type
    val isSelected = dish.id == selectedDishId
    // While a dish is open, the rest of the menu recedes and the tapped row stays lit.
    val alpha by animateFloatAsState(
        targetValue = if (selectedDishId == null || isSelected) 1f else 0.35f,
        animationSpec = tween(Motion.SHEET_MS),
        label = "row-dim",
    )
    val pill = colors.raised.copy(alpha = if (colors.isDark) 0.12f else 0.7f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isFirst) Modifier.tipTarget(Tip.TapDish) else Modifier)
            .graphicsLayer { this.alpha = alpha }
            .pressable(onClick)
            .clip(Shapes.card)
            .paper(tint)
            .heightIn(min = Space.touchTarget)
            .padding(Space.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DishName(dish, isSelected, sharedScope)
            Text(dish.originalName, style = type.original, color = colors.inkMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = Space.sm),
            ) {
                dish.price?.let {
                    Text(
                        it.asPrinted,
                        style = type.chip.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                        color = colors.ink,
                        modifier =
                            Modifier
                                .background(
                                    colors.raised,
                                    Shapes.chip,
                                ).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                dish.chips().forEach { FlagChip(it, container = pill) }
            }
        }
        Spacer(Modifier.width(Space.sm))
        Box {
            DishPlate(dish, size = 72.dp)
            AddToOrderBadge(
                quantity,
                onAdd,
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .then(if (isFirst) Modifier.tipTarget(Tip.AddDish) else Modifier),
            )
        }
    }
}

/**
 * "Don't leave without trying": the local specialities as big swipeable cards above the menu —
 * the page's hero, and the dishes a traveller most regrets missing.
 */
@Composable
private fun FeaturedDishes(
    dishes: List<Dish>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = Space.sm)) {
        SectionHeading(
            stringResource(Res.string.menu_featured_title),
            Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xs),
        )
        // Say how the picks are made: a recommendation nobody can question is one nobody trusts.
        Text(
            stringResource(Res.string.menu_featured_why),
            style = Paper.type.caption,
            color = Paper.colors.inkMuted,
            modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = 2.dp, bottom = Space.sm),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(dishes, key = { dish -> "featured-${dish.id}" }) { dish ->
                FeaturedCard(dish, onClick = { onOpen(dish.id) })
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    dish: Dish,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    val type = Paper.type
    // Every featured card is charcoal: the hero style, never mistaken for a kind of food.
    val container = colors.ink
    val content = colors.paper
    Box(
        Modifier
            .width(FEATURED_WIDTH)
            .height(FEATURED_HEIGHT)
            .pressable(onClick)
            .clip(Shapes.card)
            .paper(container),
    ) {
        DishPlate(dish, Modifier.align(Alignment.TopEnd).padding(Space.md), size = 84.dp)
        Text(
            stringResource(Res.string.mode_only_here),
            style = type.chip.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(Space.md)
                    .background(content.copy(alpha = 0.14f), Shapes.chip)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Space.cardPadding, bottom = Space.cardPadding, end = 64.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                dish.readableName,
                style = type.dishName,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            dish.pitch?.let {
                Text(
                    it,
                    style = type.caption,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.md)
                .size(44.dp)
                .background(colors.raised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PaperIcons.ArrowUpRight, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        }
    }
}

/** The dish name, which flies into the sheet header when the dish is opened. */
@Composable
private fun DishName(
    dish: Dish,
    isSelected: Boolean,
    sharedScope: SharedTransitionScope?,
) {
    val type = Paper.type
    Box {
        // Reserves the name's space while the name itself is flying to the sheet.
        Text(dish.readableName, style = type.dishName, modifier = Modifier.graphicsLayer { alpha = 0f })
        AnimatedVisibility(
            visible = !isSelected,
            enter = fadeIn(tween(Motion.QUICK_MS)),
            exit = fadeOut(tween(Motion.QUICK_MS)),
        ) {
            Text(
                dish.readableName,
                style = type.dishName,
                color = Paper.colors.ink,
                modifier = Modifier.sharedDishName(sharedScope, this, dish.id).semantics { heading() },
            )
        }
    }
}

/**
 * "Help me choose", floating over the menu like a basket button: always in reach, never taking
 * a row of its own. It opens the moods in a sheet.
 */
@Composable
private fun ChooseButton(
    isPlus: Boolean,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = Space.md)
            .tipTarget(Tip.HelpChoose)
            .pressable(onClick)
            .shadow(Elevation.floating, Shapes.pill, clip = false)
            .clip(Shapes.pill)
            .background(colors.ink)
            .heightIn(min = 56.dp)
            .padding(start = Space.gutter, end = if (isPlus) Space.gutter else Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        Icon(PaperIcons.Cloche, contentDescription = null, tint = colors.seal, modifier = Modifier.size(24.dp))
        Text(stringResource(Res.string.menu_choose_title), style = Paper.type.button, color = colors.paper)
        if (!isPlus) {
            Text(
                stringResource(Res.string.menu_plus_badge),
                style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSeal,
                modifier = Modifier.background(colors.seal, Shapes.chip).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** The four moods, each with what it means, in a sheet over the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodSheet(
    onDismiss: () -> Unit,
    onChoose: (ChoiceMode) -> Unit,
) {
    val colors = Paper.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Shapes.sheet,
        containerColor = colors.raised,
        scrimColor = colors.scrim.copy(alpha = 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.rule) },
    ) {
        Column(
            Modifier.padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                stringResource(Res.string.menu_choose_title),
                style = Paper.type.headline,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(Res.string.choose_prompt), style = Paper.type.bodySmall, color = colors.inkMuted)
            Spacer(Modifier.height(Space.sm))
        }
        MoodDeck(onChoose = onChoose, modifier = Modifier.padding(bottom = Space.gutter).navigationBarsPadding())
    }
}

/** Each mood's own emoji, shared by the menu's choose bar and the choose screen. */
internal fun ChoiceMode.emoji(): String =
    when (this) {
        ChoiceMode.SomethingNew -> "🧭"
        ChoiceMode.SomethingSpecial -> "👨‍🍳"
        ChoiceMode.OnlyHere -> "📍"
        ChoiceMode.KeepItSimple -> "🍲"
    }

internal fun ChoiceMode.title(): StringResource =
    when (this) {
        ChoiceMode.SomethingNew -> Res.string.mode_new
        ChoiceMode.SomethingSpecial -> Res.string.mode_special
        ChoiceMode.OnlyHere -> Res.string.mode_only_here
        ChoiceMode.KeepItSimple -> Res.string.mode_simple
    }

/** Room under the last dish so the floating choose button never covers it. */
private val CHOOSE_BAR_CLEARANCE = 112.dp

/** The header folded down to a bar: the back button row and a little breathing room. */
private val COLLAPSED_HEADER = Space.xs + Space.touchTarget + Space.sm

/** How far down the screen, as a fraction, the diner is taken to be reading. */
private const val READING_LINE = 0.3f

private const val FEATURED_MAX = 6
private val FEATURED_WIDTH = 260.dp
private val FEATURED_HEIGHT = 220.dp
