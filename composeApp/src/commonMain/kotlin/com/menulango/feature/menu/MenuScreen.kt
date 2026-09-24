package com.menulango.feature.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.MenuSource
import com.menulango.Route
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.DealtItems
import com.menulango.core.ui.DishRowPlaceholder
import com.menulango.core.ui.ErrorMessage
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.Hairline
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PhotoBackdrop
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.chips
import com.menulango.core.ui.dealtIn
import com.menulango.core.ui.rememberLastNonNull
import com.menulango.data.menu.model.Dish
import com.menulango.feature.choose.ChoiceMode
import com.menulango.feature.choose.dishHistoryKey
import com.menulango.feature.dish.DishHistoryControl
import com.menulango.feature.dish.DishSheet
import com.menulango.resources.Res
import com.menulango.resources.action_back
import com.menulango.resources.action_retake
import com.menulango.resources.action_try_again
import com.menulango.resources.capture_plus
import com.menulango.resources.menu_choose_title
import com.menulango.resources.menu_context_average
import com.menulango.resources.menu_context_dishes
import com.menulango.resources.menu_context_one_dish
import com.menulango.resources.menu_empty_body
import com.menulango.resources.menu_empty_title
import com.menulango.resources.menu_known
import com.menulango.resources.menu_last_free_scan
import com.menulango.resources.menu_partial
import com.menulango.resources.menu_reading
import com.menulango.resources.menu_reading_more
import com.menulango.resources.menu_truncated
import com.menulango.resources.mode_new
import com.menulango.resources.mode_only_here
import com.menulango.resources.mode_simple
import com.menulango.resources.mode_special
import com.menulango.resources.separator_dot
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun MenuScreen(
    source: MenuSource,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    navigate: (Route) -> Unit,
) {
    val viewModel = koinViewModel<MenuViewModel> { parametersOf(source) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? MenuUiState.Ready

    BackGesture(enabled = ready?.selectedDishId != null) { viewModel.dismissDish() }

    MenuContent(
        state = state,
        actions =
            MenuActions(
                onBack = onBack,
                onRetake = onRetake,
                onRetry = viewModel::retry,
                onSelectDish = viewModel::selectDish,
                onDismissDish = viewModel::dismissDish,
                onEaten = viewModel::setEaten,
                onChoose = { mode -> viewModel.routeForMode(mode)?.let(navigate) },
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
) {
    companion object {
        val Preview = MenuActions({}, {}, {}, {}, {}, { _, _ -> }, {})
    }
}

/**
 * The workhorse screen. The photograph sits behind as context; an opaque paper sheet carries the
 * list, which is the interface. Nothing is ever floated over the photo — a menu is dense text with
 * nowhere to put a card.
 */
@Composable
internal fun MenuContent(
    state: MenuUiState,
    actions: MenuActions,
) {
    val ready = state as? MenuUiState.Ready
    val selected = ready?.selectedDish
    val reduceMotion = Paper.reduceMotion

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = if (reduceMotion) null else this
        Box(Modifier.fillMaxSize()) {
            PhotoBackdrop(state.photo, focused = selected != null)

            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                IconAction(
                    icon = PaperIcons.Back,
                    label = stringResource(Res.string.action_back),
                    onClick = actions.onBack,
                    tint = Paper.colors.ink,
                    background = Paper.colors.paper.copy(alpha = 0.86f),
                    modifier = Modifier.padding(start = Space.sm, top = Space.xs),
                )
                Spacer(Modifier.height(PHOTO_PEEK))
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Paper.colors.paper, Shapes.sheet),
                ) {
                    when (state) {
                        is MenuUiState.Loading -> {
                            ReadingPlaceholder()
                        }

                        is MenuUiState.Ready -> {
                            DishList(state, actions, sharedScope)
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

            if (ready != null && !ready.isReading) {
                ChooseBar(
                    isPlus = ready.isPlus,
                    onChoose = actions.onChoose,
                    modifier =
                        Modifier.align(Alignment.BottomCenter).graphicsLayer {
                            alpha =
                                if (selected != null) 0f else 1f
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
        }
    }
}

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
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gutter)) {
        SectionLabel(stringResource(Res.string.menu_reading))
        Spacer(Modifier.height(Space.related))
        listOf(190.dp, 150.dp, 210.dp, 130.dp, 170.dp).forEach { width ->
            DishRowPlaceholder(nameWidth = width)
            Hairline()
        }
    }
}

@Composable
private fun DishList(
    state: MenuUiState.Ready,
    actions: MenuActions,
    sharedScope: SharedTransitionScope?,
) {
    val dealt = remember { DealtItems() }
    val listState = rememberLazyListState()
    val sections = remember(state.dishes) { state.dishes.map { it.section } }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = Space.gutter,
                end = Space.gutter,
                top = Space.gutter,
                bottom = CHOOSE_BAR_CLEARANCE,
            ),
    ) {
        item(key = "context") {
            ContextStrip(state)
        }
        itemsIndexed(state.dishes, key = { _, dish -> dish.id }) { index, dish ->
            val section = sections[index]
            Column(Modifier.dealtIn(dish.id, dealt)) {
                if (section != null && section != sections.getOrNull(index - 1)) {
                    SectionLabel(section, Modifier.padding(top = Space.section, bottom = Space.xs))
                }
                DishRow(
                    dish = dish,
                    selectedDishId = state.selectedDishId,
                    onClick = { actions.onSelectDish(dish.id) },
                    sharedScope = sharedScope,
                )
                Hairline()
            }
        }
        if (state.isReading) {
            item(key = "reading") {
                Column {
                    DishRowPlaceholder()
                    Text(
                        stringResource(Res.string.menu_reading_more),
                        style = Paper.type.caption,
                        color = Paper.colors.inkFaint,
                        modifier = Modifier.padding(vertical = Space.related),
                    )
                }
            }
        }
        state.notice?.let { notice ->
            item(key = "notice") {
                Text(
                    stringResource(notice.text()),
                    style = Paper.type.caption,
                    color = Paper.colors.inkMuted,
                    modifier = Modifier.padding(top = Space.gutter),
                )
            }
        }
    }
}

private fun MenuNotice.text(): StringResource =
    when (this) {
        MenuNotice.Partial -> Res.string.menu_partial
        MenuNotice.Truncated -> Res.string.menu_truncated
        MenuNotice.KnownMenu -> Res.string.menu_known
        MenuNotice.LastFreeScan -> Res.string.menu_last_free_scan
    }

/** "23 dishes · Greek · avg €12" */
@Composable
private fun ContextStrip(state: MenuUiState.Ready) {
    val facts = menuContextFacts(state.dishes, state.meta.language, state.meta.currency)
    val parts =
        listOfNotNull(
            if (facts.dishCount == 1) {
                stringResource(Res.string.menu_context_one_dish)
            } else {
                stringResource(Res.string.menu_context_dishes, facts.dishCount)
            },
            facts.languageName,
            facts.averagePrice?.let { stringResource(Res.string.menu_context_average, it) },
        )
    SectionLabel(
        parts.joinToString(stringResource(Res.string.separator_dot)),
        Modifier.padding(bottom = Space.related),
    )
}

@Composable
private fun DishRow(
    dish: Dish,
    selectedDishId: String?,
    onClick: () -> Unit,
    sharedScope: SharedTransitionScope?,
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
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Space.touchTarget)
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DishName(dish, isSelected, sharedScope)
            Text(dish.originalName, style = type.original, color = colors.inkFaint)
            FlagChips(dish.chips(), Modifier.padding(top = Space.related - 2.dp))
        }
        dish.price?.let {
            Spacer(Modifier.width(Space.md))
            Text(
                it.asPrinted,
                style = type.price,
                color = colors.inkMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 3.dp),
            )
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
 * The four choosing modes, floating in the lower half where the thumb already is.
 */
@Composable
private fun ChooseBar(
    isPlus: Boolean,
    onChoose: (ChoiceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.raised)
            .navigationBarsPadding()
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
        ) {
            SectionLabel(stringResource(Res.string.menu_choose_title), Modifier.weight(1f))
            if (!isPlus) SectionLabel(stringResource(Res.string.capture_plus), color = colors.ember)
        }
        Spacer(Modifier.height(Space.related))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.related)) {
            ChoiceMode.entries.forEach { mode ->
                ModeButton(mode, onClick = { onChoose(mode) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeButton(
    mode: ChoiceMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Box(
        modifier
            .heightIn(min = 56.dp)
            .border(Space.hairline, colors.rule, Shapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xs, vertical = Space.related),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(mode.title()),
            style = Paper.type.caption.copy(fontWeight = Paper.type.button.fontWeight),
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun ChoiceMode.title(): StringResource =
    when (this) {
        ChoiceMode.SomethingNew -> Res.string.mode_new
        ChoiceMode.SomethingSpecial -> Res.string.mode_special
        ChoiceMode.OnlyHere -> Res.string.mode_only_here
        ChoiceMode.KeepItSimple -> Res.string.mode_simple
    }

/** How much of the photograph shows above the paper sheet. */
private val PHOTO_PEEK = 96.dp

/** Room under the last dish so the choose bar never covers it. */
private val CHOOSE_BAR_CLEARANCE = 150.dp
