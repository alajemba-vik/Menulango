package com.menulango.feature.choose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menulango.core.design.Elevation
import com.menulango.core.design.FoodGroup
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.felt
import com.menulango.core.ui.pressable
import com.menulango.feature.menu.emoji
import com.menulango.feature.menu.title
import com.menulango.resources.Res
import com.menulango.resources.mode_new_line
import com.menulango.resources.mode_only_here_line
import com.menulango.resources.mode_simple_line
import com.menulango.resources.mode_special_line
import com.menulango.resources.mood_pick
import com.menulango.resources.mood_position
import com.menulango.resources.mood_swipe_hint
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

/**
 * The moods as a deck of postcards: one big card at a time, the next ones peeking behind it,
 * tilted like cards fanned on a table. Swipe to flip through, tap to pick.
 *
 * Every card is the same size whatever the text, so a long title on a narrow phone never makes
 * one mood look bigger than another.
 */
@Composable
internal fun MoodDeck(
    onChoose: (ChoiceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val moods = ChoiceMode.entries
    val pager = rememberPagerState { moods.size }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // A soft tick as each card settles, like a card landing on felt.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = DECK_PEEK),
            pageSpacing = Space.sm,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth().height(CARD_HEIGHT + DECK_ROOM),
        ) { page ->
            val mode = moods[page]
            MoodCard(
                mode = mode,
                position = page + 1,
                total = moods.size,
                onPick = {
                    if (pager.currentPage == page) {
                        onChoose(mode)
                    } else {
                        scope.launch { pager.animateScrollToPage(page) }
                    }
                },
                modifier = Modifier.deckTransform(pager, page),
            )
        }
        Spacer(Modifier.height(Space.sm))
        DeckDots(pager, moods.size, onDot = { scope.launch { pager.animateScrollToPage(it) } })
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(Res.string.mood_swipe_hint),
            style = Paper.type.caption,
            color = Paper.colors.inkFaint,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * Cards away from the centre shrink, drop a little and tilt away, so the deck reads as a physical
 * fan. With reduce-motion the tilt goes and only the size step remains.
 */
@Composable
private fun Modifier.deckTransform(
    pager: PagerState,
    page: Int,
): Modifier {
    val reduceMotion = Paper.reduceMotion
    val drop = with(LocalDensity.current) { DECK_DROP.toPx() }
    return graphicsLayer {
        val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
        val distance = offset.absoluteValue.coerceAtMost(1.5f)
        val scale = 1f - 0.08f * distance
        scaleX = scale
        scaleY = scale
        alpha = 1f - 0.25f * distance
        if (!reduceMotion) {
            rotationZ = offset.coerceIn(-1.5f, 1.5f) * TILT_DEGREES
            translationY = distance * drop
        }
    }
}

@Composable
private fun MoodCard(
    mode: ChoiceMode,
    position: Int,
    total: Int,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val title = stringResource(mode.title())
    val line = stringResource(mode.line())
    val pick = stringResource(Res.string.mood_pick)
    val where = stringResource(Res.string.mood_position, position, total)
    Box(modifier.padding(top = Space.xs), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT)
                .shadow(Elevation.raised, Shapes.card, clip = false)
                .felt(colors.food(mode.tint()), Shapes.card)
                .pressable(onPick, pressedScale = 0.97f)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "$title. $line"
                    stateDescription = where
                    onClick(label = pick) {
                        onPick()
                        true
                    }
                }.padding(Space.cardPadding),
        ) {
            Box(
                Modifier.size(EMOJI_PLATE).clip(Shapes.pill).background(colors.raised.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mode.emoji(), fontSize = 34.sp, modifier = Modifier.clearAndSetSemantics { })
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = Paper.type.headline, color = colors.ink, maxLines = 2)
            Spacer(Modifier.height(Space.xs))
            Text(line, style = Paper.type.bodySmall, color = colors.inkMuted, maxLines = 3)
            Spacer(Modifier.height(Space.md))
            Text(
                pick,
                style = Paper.type.button,
                color = colors.paper,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .clip(Shapes.pill)
                        .background(colors.ink)
                        .padding(horizontal = Space.gutter, vertical = Space.sm),
            )
        }
    }
}

/** Where you are in the deck: the current dot stretches into a pill. Tapping a dot jumps there. */
@Composable
private fun DeckDots(
    pager: PagerState,
    count: Int,
    onDot: (Int) -> Unit,
) {
    val colors = Paper.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        repeat(count) { index ->
            val current = pager.currentPage == index
            val width by animateDpAsState(if (current) 22.dp else 8.dp, label = "dot")
            val tint by animateColorAsState(if (current) colors.seal else colors.rule, label = "dotColor")
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .width(width)
                    .height(8.dp)
                    .clip(Shapes.pill)
                    .background(tint)
                    .pressable({ onDot(index) }),
            )
        }
    }
}

/** Each mood's card colour, borrowed from the food palette so the deck belongs to the menu. */
private fun ChoiceMode.tint(): FoodGroup =
    when (this) {
        ChoiceMode.SomethingNew -> FoodGroup.Drink
        ChoiceMode.SomethingSpecial -> FoodGroup.Grill
        ChoiceMode.OnlyHere -> FoodGroup.Garden
        ChoiceMode.KeepItSimple -> FoodGroup.Sweet
    }

/** One line of invitation per mood, written for the card rather than the list. */
private fun ChoiceMode.line(): StringResource =
    when (this) {
        ChoiceMode.SomethingNew -> Res.string.mode_new_line
        ChoiceMode.SomethingSpecial -> Res.string.mode_special_line
        ChoiceMode.OnlyHere -> Res.string.mode_only_here_line
        ChoiceMode.KeepItSimple -> Res.string.mode_simple_line
    }

private val CARD_HEIGHT = 300.dp
private val DECK_PEEK = 44.dp
private val DECK_DROP = 18.dp
private val DECK_ROOM = 28.dp
private val EMOJI_PLATE = 64.dp
private const val TILT_DEGREES = -6f
