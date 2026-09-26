package com.menulango.feature.dish

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.chips
import com.menulango.core.ui.foodGroup
import com.menulango.data.menu.model.Allergens
import com.menulango.data.menu.model.Dish
import com.menulango.resources.Res
import com.menulango.resources.action_close
import com.menulango.resources.dish_ask_restaurant
import com.menulango.resources.dish_best_guess_note
import com.menulango.resources.dish_check_before
import com.menulango.resources.dish_eaten
import com.menulango.resources.dish_eaten_done
import com.menulango.resources.dish_how_it_is_made
import com.menulango.resources.dish_ingredients
import com.menulango.resources.dish_may_contain
import com.menulango.resources.dish_often_contains
import com.menulango.resources.dish_sheet_description
import com.menulango.resources.dish_unknown_method
import com.menulango.resources.dish_unknown_what
import com.menulango.resources.dish_what_it_is
import com.menulango.resources.list_joiner
import com.menulango.resources.order_add
import com.menulango.resources.order_add_another
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The dish, explained — long-form reading on a rounded white sheet.
 *
 * Rises to most of the screen and drags up to full height. Everything a nervous diner needs is
 * here, in the order they need it: what arrives, what is in it, how it is made (given the most
 * room, because that is what earns the courage to order), and what to check first.
 *
 * @param nameModifier carries the shared-element transition that flies the name in from the list.
 * @param history null for free diners: remembering dishes is part of Plus.
 */
@Composable
internal fun DishSheet(
    dish: Dish,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    history: DishHistoryControl? = null,
    order: DishOrderControl? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        val partialHeight = maxHeight * PARTIAL_FRACTION
        var expanded by rememberSaveable(dish.id) { mutableStateOf(false) }
        val restingHeight by animateDpAsState(
            targetValue = if (expanded) fullHeight else partialHeight,
            animationSpec = tween(Motion.SHEET_MS, easing = Motion.standard),
            label = "sheet-height",
        )
        val drag = remember(dish.id) { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val threshold = with(density) { DRAG_THRESHOLD.toPx() }
        val closeLabel = stringResource(Res.string.action_close)

        val dragToResize =
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { drag.snapTo(drag.value + delta) } },
                onDragStopped = { velocity ->
                    val dragged = drag.value
                    when {
                        dragged < -threshold || velocity < -FLING_VELOCITY -> {
                            expanded = true
                        }

                        dragged > threshold || velocity > FLING_VELOCITY -> {
                            if (expanded) expanded = false else onDismiss()
                        }
                    }
                    drag.animateTo(0f, tween(Motion.QUICK_MS, easing = Motion.standard))
                },
            )
        // Dragging down moves the sheet; dragging up grows it, never past full height.
        val grow = with(density) { (-drag.value).coerceAtLeast(0f).toDp() }
        val height = (restingHeight + grow).coerceAtMost(fullHeight)
        val sheetDescription = stringResource(Res.string.dish_sheet_description, dish.readableName)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height)
                .offset { IntOffset(0, drag.value.coerceAtLeast(0f).roundToInt()) }
                .shadow(Elevation.sheet, Shapes.sheet, clip = false)
                .clip(Shapes.sheet)
                .background(Paper.colors.raised)
                .semantics {
                    paneTitle = sheetDescription
                    customActions =
                        listOf(
                            CustomAccessibilityAction(closeLabel) {
                                onDismiss()
                                true
                            },
                        )
                },
        ) {
            SheetHeader(dish, dragToResize, nameModifier, onDismiss)
            SheetBody(dish, history, order)
        }
    }
}

/** Lets the sheet add a dish to the table's order without knowing how orders are kept. */
internal data class DishOrderControl(
    val quantity: Int,
    val onAdd: () -> Unit,
)

/** Lets the sheet mark a dish as eaten without knowing where history is stored. */
internal data class DishHistoryControl(
    val eaten: Boolean,
    val onChange: (Boolean) -> Unit,
)

@Composable
private fun SheetHeader(
    dish: Dish,
    dragToResize: Modifier,
    nameModifier: Modifier,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    val type = Paper.type
    Column(dragToResize.fillMaxWidth().padding(horizontal = Space.gutter)) {
        Box(
            Modifier
                .padding(top = Space.sm)
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(5.dp)
                .background(colors.rule, Shapes.pill),
        )
        // No close button: the handle, a tap outside, the back gesture and the screen reader's
        // "close" action all dismiss the sheet, and a button here crowded the plate.
        Text(
            text = dish.originalName,
            style = type.original,
            color = colors.inkFaint,
            modifier = Modifier.padding(top = Space.md),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
            Column(Modifier.weight(1f)) {
                Text(dish.readableName, style = type.dishTitle, color = colors.ink, modifier = nameModifier)
                dish.price?.let {
                    Text(
                        it.asPrinted,
                        style = type.price.copy(fontWeight = FontWeight.Bold),
                        color = colors.sealInk,
                        modifier =
                            Modifier
                                .padding(top = Space.sm)
                                .background(colors.sealWash, Shapes.chip)
                                .padding(horizontal = Space.sm, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.width(Space.sm))
            DishPlate(dish, size = 88.dp)
        }
        Spacer(Modifier.height(Space.md))
    }
}

@Composable
private fun SheetBody(
    dish: Dish,
    history: DishHistoryControl?,
    order: DishOrderControl?,
) {
    val colors = Paper.colors
    val type = Paper.type
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .navigationBarsPadding()
            .padding(bottom = Space.xl),
    ) {
        Column(Modifier.widthIn(max = Space.readingWidth), verticalArrangement = Arrangement.spacedBy(Space.related)) {
            dish.pitch?.let { Text(it, style = type.bodySmall, color = colors.inkMuted) }
            FlagChips(dish.chips(limit = 6), Modifier.padding(top = Space.xs))

            Section(stringResource(Res.string.dish_what_it_is)) {
                Text(
                    dish.whatItIs ?: stringResource(Res.string.dish_unknown_what),
                    style = type.body,
                    color = colors.ink,
                )
                if (dish.isBestGuess) {
                    Text(
                        stringResource(Res.string.dish_best_guess_note),
                        style = type.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }

            if (dish.ingredients.isNotEmpty()) {
                Section(stringResource(Res.string.dish_ingredients)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.related),
                        verticalArrangement = Arrangement.spacedBy(Space.related),
                    ) {
                        dish.ingredients.forEach { ingredient ->
                            Text(
                                ingredient,
                                style = type.bodySmall,
                                color = colors.ink,
                                modifier =
                                    Modifier
                                        .background(colors.sunk, Shapes.chip)
                                        .padding(horizontal = Space.sm, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Section(stringResource(Res.string.dish_how_it_is_made)) {
                HowItIsMade(dish.howItIsMade, Paper.colors.food(dish.foodGroup()))
            }

            Section(null) { AllergenPanel(dish.allergens) }

            order?.let { control ->
                Spacer(Modifier.height(Space.md))
                PrimaryButton(
                    text =
                        if (control.quantity == 0) {
                            stringResource(Res.string.order_add)
                        } else {
                            stringResource(Res.string.order_add_another, control.quantity)
                        },
                    onClick = control.onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            history?.let { control ->
                Spacer(Modifier.height(Space.md))
                SecondaryButton(
                    text = stringResource(if (control.eaten) Res.string.dish_eaten_done else Res.string.dish_eaten),
                    icon = if (control.eaten) PaperIcons.Check else null,
                    onClick = { control.onChange(!control.eaten) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Section(
    label: String?,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(Space.section - Space.related))
    if (label != null) SectionLabel(label, Modifier.padding(bottom = Space.xs))
    content()
}

/**
 * Set in Petrona italic beside a coral bar, on a tile the colour of the dish's kind of food: the
 * paragraph the whole app exists for.
 */
@Composable
private fun HowItIsMade(
    method: String?,
    tint: Color,
) {
    val colors = Paper.colors
    Row(
        Modifier
            .padding(top = Space.xs)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (method == null) colors.sunk else tint, Shapes.tile)
            .padding(Space.md),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (method == null) colors.rule else colors.seal, Shapes.pill),
        )
        Text(
            text = method ?: stringResource(Res.string.dish_unknown_method),
            style = if (method == null) Paper.type.bodySmall else Paper.type.method,
            color = if (method == null) colors.inkMuted else colors.ink,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}

/**
 * What the dish likely contains — never what it is free from — always ending with the instruction
 * to ask the restaurant. Shown to everyone: allergen information is never behind the paywall.
 */
@Composable
internal fun AllergenPanel(
    allergens: Allergens,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val type = Paper.type
    val joiner = stringResource(Res.string.list_joiner)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.alarmWash, Shapes.tile)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        SectionLabel(stringResource(Res.string.dish_check_before), color = colors.alarm)
        if (allergens.likelyContains.isNotEmpty()) {
            Text(
                stringResource(Res.string.dish_often_contains, allergens.likelyContains.joinToString(joiner)),
                style = type.bodySmall,
                color = colors.ink,
            )
        }
        if (allergens.mayContain.isNotEmpty()) {
            Text(
                stringResource(Res.string.dish_may_contain, allergens.mayContain.joinToString(joiner)),
                style = type.bodySmall,
                color = colors.ink,
            )
        }
        allergens.note?.let { Text(it, style = type.bodySmall, color = colors.inkMuted) }
        Text(
            stringResource(Res.string.dish_ask_restaurant),
            style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.alarm,
        )
    }
}

private const val PARTIAL_FRACTION = 0.82f
private const val FLING_VELOCITY = 1_200f
private val DRAG_THRESHOLD = 96.dp
