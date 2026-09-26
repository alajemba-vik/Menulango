package com.menulango.feature.choose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SectionHeading
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.chips
import com.menulango.core.ui.felt
import com.menulango.core.ui.foodGroup
import com.menulango.core.ui.paper
import com.menulango.core.ui.pressable
import com.menulango.core.ui.rememberLastNonNull
import com.menulango.data.menu.model.Dish
import com.menulango.feature.dish.DishOrderControl
import com.menulango.feature.dish.DishSheet
import com.menulango.feature.menu.FilterPill
import com.menulango.feature.menu.emoji
import com.menulango.feature.menu.title
import com.menulango.feature.order.label
import com.menulango.resources.Res
import com.menulango.resources.action_back
import com.menulango.resources.choose_empty_menu
import com.menulango.resources.choose_empty_new
import com.menulango.resources.choose_empty_only_here
import com.menulango.resources.choose_empty_simple
import com.menulango.resources.choose_empty_special
import com.menulango.resources.choose_keep
import com.menulango.resources.choose_next_person
import com.menulango.resources.choose_pass
import com.menulango.resources.choose_picked_for
import com.menulango.resources.choose_read_more
import com.menulango.resources.choose_swipe_hint
import com.menulango.resources.choose_title
import com.menulango.resources.choose_why
import com.menulango.resources.mode_new_hint
import com.menulango.resources.mode_only_here_hint
import com.menulango.resources.mode_simple_hint
import com.menulango.resources.mode_special_hint
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

@Composable
internal fun ChooseScreen(
    dishes: List<Dish>,
    initialMode: ChoiceMode,
    orderKey: String,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ChooseViewModel> { parametersOf(dishes, initialMode, orderKey) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackGesture(enabled = (state as? ChooseUiState.Ready)?.openDish != null) { viewModel.closeDish() }
    ChooseContent(
        state = state,
        actions =
            ChooseActions(
                onBack = onBack,
                onMode = viewModel::selectMode,
                onPass = viewModel::pass,
                onKeep = viewModel::keep,
                onOpenDish = viewModel::openDish,
                onCloseDish = viewModel::closeDish,
                onNextPerson = viewModel::nextPerson,
            ),
    )
}

internal data class ChooseActions(
    val onBack: () -> Unit,
    val onMode: (ChoiceMode) -> Unit,
    val onPass: () -> Unit,
    val onKeep: (Dish) -> Unit,
    val onOpenDish: (Dish) -> Unit,
    val onCloseDish: () -> Unit,
    val onNextPerson: () -> Unit,
) {
    companion object {
        val Preview = ChooseActions({}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Suggestions as a deck of cards: the mood at the top, one dish at a time with its reason, and
 * the next ones peeking behind. Swipe right to add it to the table's picks, left to pass — or use
 * the buttons, which do the same for anyone who can't or won't swipe.
 */
@Composable
internal fun ChooseContent(
    state: ChooseUiState,
    actions: ChooseActions,
) {
    val colors = Paper.colors
    Box(Modifier.fillMaxSize().felt(colors.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Space.sm, end = Space.gutter),
            ) {
                IconAction(
                    PaperIcons.Back,
                    stringResource(Res.string.action_back),
                    actions.onBack,
                    background = colors.raised,
                    modifier = Modifier.padding(vertical = Space.xs),
                )
                SectionHeading(stringResource(Res.string.choose_title), Modifier.padding(start = Space.sm))
            }
            when (state) {
                ChooseUiState.Loading -> {
                    Spacer(Modifier.weight(1f))
                }

                ChooseUiState.Empty -> {
                    StateMessage(stringResource(Res.string.choose_title), stringResource(Res.string.choose_empty_menu))
                }

                is ChooseUiState.Ready -> {
                    MoodRow(state.mode, actions.onMode)
                    Text(
                        stringResource(state.mode.hint()),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.related),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm)) {
                        if (state.top == null) {
                            Text(
                                stringResource(state.mode.noneFits()),
                                style = Paper.type.body,
                                color = colors.inkMuted,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            Deck(state, actions)
                        }
                    }
                    state.top?.let { DeckControls(state, it, actions) }
                }
            }
        }

        val ready = state as? ChooseUiState.Ready
        AnimatedVisibility(
            visible = ready?.openDish != null,
            enter =
                slideInVertically(tween(Motion.SHEET_MS, easing = Motion.standard)) { it / 2 } +
                    fadeIn(tween(Motion.QUICK_MS)),
            exit =
                slideOutVertically(tween(Motion.SHEET_MS, easing = Motion.standard)) { it / 2 } +
                    fadeOut(tween(Motion.SHEET_MS)),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            val dish = rememberLastNonNull(ready?.openDish)
            dish?.let {
                DishSheet(
                    dish = it,
                    onDismiss = actions.onCloseDish,
                    order = DishOrderControl(ready?.order?.quantityOf(it.id) ?: 0) { actions.onKeep(it) },
                )
            }
        }
    }
}

/** The four moods as chips, like the menu's filters: the same control means the same kind of thing. */
@Composable
private fun MoodRow(
    selected: ChoiceMode,
    onMode: (ChoiceMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.sm)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        ChoiceMode.entries.forEach { mode ->
            FilterPill(
                text = "${mode.emoji()}  ${stringResource(mode.title())}",
                selected = mode == selected,
                onClick = { onMode(mode) },
            )
        }
    }
}

/**
 * The top card follows the finger, tilting as it goes; let go past a third of the width and it
 * flies off that way. The cards behind sit slightly smaller and askew, so the deck reads as a
 * deck. Under reduce-motion the cards neither tilt nor fly: they simply change.
 */
@Composable
private fun Deck(
    state: ChooseUiState.Ready,
    actions: ChooseActions,
) {
    val reduceMotion = Paper.reduceMotion
    val scope = rememberCoroutineScope()
    val top = state.top ?: return
    val drag = remember(top.id, state.mode) { Animatable(0f) }
    val keepLabel = stringResource(Res.string.choose_keep)
    val passLabel = stringResource(Res.string.choose_pass)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val threshold = width / 3f
        // Behind: deepest first, so the top card is drawn last.
        for (depth in state.deck.lastIndex downTo 1) {
            val dish = state.deck[depth]
            DeckCard(
                dish,
                Modifier.fillMaxSize().graphicsLayer {
                    // The card behind rises to meet the finger as the top one leaves.
                    val lift = if (depth == 1) (abs(drag.value) / width).coerceIn(0f, 1f) else 0f
                    val step = depth - lift
                    scaleX = 1f - step * 0.05f
                    scaleY = 1f - step * 0.05f
                    translationY = step * 14.dp.toPx()
                    rotationZ = if (reduceMotion) 0f else (if (depth % 2 == 0) -3f else 3f) * step
                },
                onRead = {},
                interactive = false,
            )
        }
        DeckCard(
            top,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = drag.value
                    rotationZ = if (reduceMotion) 0f else drag.value / width * 12f
                }.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(keepLabel) {
                                actions.onKeep(top)
                                true
                            },
                            CustomAccessibilityAction(passLabel) {
                                actions.onPass()
                                true
                            },
                        )
                }.pointerInput(top.id, state.mode) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    drag.value > threshold -> {
                                        if (!reduceMotion) drag.animateTo(width * 1.5f, tween(Motion.QUICK_MS))
                                        actions.onKeep(top)
                                    }

                                    drag.value < -threshold -> {
                                        if (!reduceMotion) drag.animateTo(-width * 1.5f, tween(Motion.QUICK_MS))
                                        actions.onPass()
                                    }

                                    else -> {
                                        drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + amount.x) }
                    }
                },
            onRead = { actions.onOpenDish(top) },
            interactive = true,
            verdict = { (drag.value / threshold).coerceIn(-1f, 1f) },
        )
    }
}

/**
 * One suggestion: the dish on its stoneware, what it's called here and there, and — set as large
 * as the name, because it is what earns trust — why it was chosen.
 *
 * @param verdict how far the card has been dragged towards keep (1) or pass (-1), shown as a stamp.
 */
@Composable
private fun DeckCard(
    dish: Dish,
    modifier: Modifier,
    onRead: () -> Unit,
    interactive: Boolean,
    verdict: () -> Float = { 0f },
) {
    val colors = Paper.colors
    val type = Paper.type
    Box(
        modifier
            .shadow(if (interactive) Elevation.raised else Elevation.resting, Shapes.card)
            .paper(colors.food(dish.foodGroup()), Shapes.card),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(Space.cardPadding + Space.xs)
                .semantics { if (interactive) liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Space.related),
        ) {
            DishPlate(dish, size = 104.dp)
            Spacer(Modifier.height(Space.xs))
            Text(
                dish.readableName,
                style = type.dishTitle,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                dish.price?.let {
                    Text(it.asPrinted, style = type.price.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                }
            }
            FlagChips(dish.chips(), container = colors.raised.copy(alpha = if (colors.isDark) 0.12f else 0.7f))
            Spacer(Modifier.weight(1f))
            // On a short phone the reason gives way first, a line at a time, so "Read about it"
            // always keeps its full height at the bottom of the card.
            dish.pitch?.let {
                SectionLabel(stringResource(Res.string.choose_why))
                Text(
                    it,
                    style = type.method,
                    color = colors.ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (interactive) {
                QuietButton(
                    stringResource(Res.string.choose_read_more),
                    onRead,
                    color = colors.sealInk,
                    singleLine = true,
                )
            }
        }
        if (interactive) {
            // The stamps that answer the swipe: coral "add" on the left, ink "pass" on the right.
            SwipeStamp(Res.string.choose_keep, colors.seal, colors.onSeal, -8f, Modifier.align(Alignment.TopStart)) {
                verdict().coerceAtLeast(0f)
            }
            SwipeStamp(Res.string.choose_pass, colors.ink, colors.paper, 8f, Modifier.align(Alignment.TopEnd)) {
                (-verdict()).coerceAtLeast(0f)
            }
        }
    }
}

@Composable
private fun SwipeStamp(
    text: StringResource,
    container: Color,
    content: Color,
    tilt: Float,
    modifier: Modifier,
    strength: () -> Float,
) {
    Text(
        stringResource(text),
        style = Paper.type.button,
        color = content,
        modifier =
            modifier
                .padding(Space.gutter)
                .graphicsLayer {
                    alpha = strength()
                    rotationZ = tilt
                }.background(container, Shapes.chip)
                .padding(horizontal = Space.md, vertical = Space.related),
    )
}

/** Pass and keep — the swipe's two answers as buttons — and who the next pick is for. */
@Composable
private fun DeckControls(
    state: ChooseUiState.Ready,
    top: Dish,
    actions: ChooseActions,
) {
    val colors = Paper.colors
    val order = state.order
    val forWhom = order.label(order.diners.first { it.id == order.activeDinerId })
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(
                PaperIcons.Close,
                stringResource(Res.string.choose_pass),
                colors.raised,
                colors.ink,
                actions.onPass,
            )
            RoundAction(
                PaperIcons.Plus,
                stringResource(Res.string.choose_keep),
                colors.seal,
                colors.onSeal,
                { actions.onKeep(top) },
                large = true,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (order.isEmpty) {
                    stringResource(Res.string.choose_swipe_hint)
                } else {
                    stringResource(Res.string.choose_picked_for, order.dishCount, forWhom)
                },
                style = Paper.type.caption,
                color = colors.inkMuted,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            if (!order.isEmpty) {
                Spacer(Modifier.width(Space.xs))
                QuietButton(stringResource(Res.string.choose_next_person), actions.onNextPerson, color = colors.sealInk)
            }
        }
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    large: Boolean = false,
) {
    val size = if (large) 72.dp else 60.dp
    Box(
        Modifier
            .size(size)
            .pressable(onClick)
            .shadow(Elevation.raised, CircleShape, clip = false)
            .background(container, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (large) 30.dp else 24.dp))
    }
}

internal fun ChoiceMode.hint(): StringResource =
    when (this) {
        ChoiceMode.SomethingNew -> Res.string.mode_new_hint
        ChoiceMode.SomethingSpecial -> Res.string.mode_special_hint
        ChoiceMode.OnlyHere -> Res.string.mode_only_here_hint
        ChoiceMode.KeepItSimple -> Res.string.mode_simple_hint
    }

private fun ChoiceMode.noneFits(): StringResource =
    when (this) {
        ChoiceMode.SomethingNew -> Res.string.choose_empty_new
        ChoiceMode.SomethingSpecial -> Res.string.choose_empty_special
        ChoiceMode.OnlyHere -> Res.string.choose_empty_only_here
        ChoiceMode.KeepItSimple -> Res.string.choose_empty_simple
    }
