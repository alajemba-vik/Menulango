package com.menulango.feature.choose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.BackGesture
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.chips
import com.menulango.core.ui.rememberLastNonNull
import com.menulango.data.menu.model.Dish
import com.menulango.feature.dish.DishHistoryControl
import com.menulango.feature.dish.DishSheet
import com.menulango.feature.menu.title
import com.menulango.resources.Res
import com.menulango.resources.action_back
import com.menulango.resources.choose_another
import com.menulango.resources.choose_empty_menu
import com.menulango.resources.choose_empty_new
import com.menulango.resources.choose_empty_only_here
import com.menulango.resources.choose_empty_simple
import com.menulango.resources.choose_empty_special
import com.menulango.resources.choose_enjoy
import com.menulango.resources.choose_have_this
import com.menulango.resources.choose_prompt
import com.menulango.resources.choose_read_more
import com.menulango.resources.choose_title
import com.menulango.resources.choose_why
import com.menulango.resources.mode_new_hint
import com.menulango.resources.mode_only_here_hint
import com.menulango.resources.mode_simple_hint
import com.menulango.resources.mode_special_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun ChooseScreen(
    dishes: List<Dish>,
    initialMode: ChoiceMode,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<ChooseViewModel> { parametersOf(dishes, initialMode) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackGesture(enabled = (state as? ChooseUiState.Ready)?.openDish != null) { viewModel.closeDish() }
    ChooseContent(
        state = state,
        actions =
            ChooseActions(
                onBack = onBack,
                onMode = viewModel::selectMode,
                onAnother = viewModel::showAnother,
                onOpenDish = viewModel::openDish,
                onCloseDish = viewModel::closeDish,
                onOrder = viewModel::order,
            ),
    )
}

internal data class ChooseActions(
    val onBack: () -> Unit,
    val onMode: (ChoiceMode) -> Unit,
    val onAnother: () -> Unit,
    val onOpenDish: (Dish) -> Unit,
    val onCloseDish: () -> Unit,
    val onOrder: (Dish) -> Unit,
) {
    companion object {
        val Preview = ChooseActions({}, {}, {}, {}, {}, {})
    }
}

/**
 * One dish, one reason. The reason is what creates trust, so it is never left out and it is set
 * as large as the name. The four moods sit at the bottom, under the thumb.
 */
@Composable
internal fun ChooseContent(
    state: ChooseUiState,
    actions: ChooseActions,
) {
    val colors = Paper.colors
    Box(Modifier.fillMaxSize().background(colors.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Space.sm, end = Space.gutter),
            ) {
                IconAction(PaperIcons.Back, stringResource(Res.string.action_back), actions.onBack)
                Text(
                    stringResource(Res.string.choose_title),
                    style = Paper.type.headline,
                    color = colors.ink,
                    modifier = Modifier.padding(start = Space.xs).semantics { heading() },
                )
            }
            when (state) {
                ChooseUiState.Loading -> {
                    Spacer(Modifier.weight(1f))
                }

                ChooseUiState.Empty -> {
                    Box(Modifier.weight(1f)) {
                        StateMessage(
                            stringResource(Res.string.choose_title),
                            stringResource(Res.string.choose_empty_menu),
                        )
                    }
                }

                is ChooseUiState.Ready -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Reveal(state, actions)
                    }
                    ModeGrid(state.mode, actions.onMode)
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
                    history =
                        if (ready?.isPlus == true) {
                            DishHistoryControl(eaten = (ready.pick as? Pick.Found)?.ordered == true) { eaten ->
                                if (eaten) actions.onOrder(it)
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun Reveal(
    state: ChooseUiState.Ready,
    actions: ChooseActions,
) {
    val reduceMotion = Paper.reduceMotion
    AnimatedContent(
        targetState = state.pick,
        contentKey = { pick -> (pick as? Pick.Found)?.dish?.id ?: state.mode.name },
        transitionSpec = {
            val enter =
                if (reduceMotion) {
                    fadeIn(tween(Motion.ENTRANCE_MS))
                } else {
                    fadeIn(tween(Motion.ENTRANCE_MS, easing = Motion.settle)) +
                        slideInVertically(tween(Motion.ENTRANCE_MS, easing = Motion.settle)) { it / 12 }
                }
            enter togetherWith fadeOut(tween(Motion.QUICK_MS))
        },
        label = "reveal",
        modifier = Modifier.fillMaxSize(),
    ) { pick ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter, vertical = Space.gutter)
                .widthIn(max = Space.readingWidth)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Space.related),
        ) {
            SectionLabel(stringResource(state.mode.title()), color = Paper.colors.ember)
            when (pick) {
                is Pick.Found -> {
                    FoundDish(pick, actions)
                }

                Pick.NoneFits -> {
                    Text(stringResource(state.mode.noneFits()), style = Paper.type.body, color = Paper.colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun FoundDish(
    pick: Pick.Found,
    actions: ChooseActions,
) {
    val colors = Paper.colors
    val type = Paper.type
    val dish = pick.dish
    Text(dish.readableName, style = type.dishTitle, color = colors.ink, modifier = Modifier.semantics { heading() })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dish.originalName, style = type.original, color = colors.inkFaint, modifier = Modifier.weight(1f))
        dish.price?.let { Text(it.asPrinted, style = type.price, color = colors.inkMuted) }
    }
    FlagChips(dish.chips())
    Spacer(Modifier.height(Space.md))
    SectionLabel(stringResource(Res.string.choose_why))
    // Pick.Found is only ever built from dishes that have a pitch.
    Text(dish.pitch.orEmpty(), style = type.method, color = colors.ink)
    Spacer(Modifier.height(Space.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.related), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton(stringResource(Res.string.choose_read_more), { actions.onOpenDish(dish) })
        if (pick.hasAnother) QuietButton(stringResource(Res.string.choose_another), actions.onAnother)
    }
    Spacer(Modifier.height(Space.related))
    if (pick.ordered) {
        Text(stringResource(Res.string.choose_enjoy), style = type.bodySmall, color = colors.seal)
    } else {
        PrimaryButton(stringResource(Res.string.choose_have_this), { actions.onOrder(dish) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ModeGrid(
    selected: ChoiceMode,
    onMode: (ChoiceMode) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Paper.colors.raised).padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        Text(stringResource(Res.string.choose_prompt), style = Paper.type.caption, color = Paper.colors.inkMuted)
        ChoiceMode.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.related)) {
                pair.forEach { mode ->
                    ModeCard(
                        mode,
                        isSelected = mode == selected,
                        onClick = { onMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: ChoiceMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    Column(
        modifier
            .heightIn(min = 72.dp)
            .background(if (isSelected) colors.paper else colors.raised, Shapes.card)
            .border(
                if (isSelected) 1.5.dp else Space.hairline,
                if (isSelected) colors.seal else colors.rule,
                Shapes.card,
            ).selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(mode.title()),
            style = Paper.type.title.copy(fontSize = Paper.type.bodySmall.fontSize),
            color = if (isSelected) colors.seal else colors.ink,
        )
        Text(stringResource(mode.hint()), style = Paper.type.caption, color = colors.inkMuted)
    }
}

private fun ChoiceMode.hint(): StringResource =
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
