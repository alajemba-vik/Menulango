package com.menulango.feature.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SegmentedControl
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.pressable
import com.menulango.core.ui.suede
import com.menulango.data.menu.model.Dish
import com.menulango.feature.menu.FilterPill
import com.menulango.resources.Res
import com.menulango.resources.action_close
import com.menulango.resources.menu_choose_title
import com.menulango.resources.note_add
import com.menulango.resources.note_allergic
import com.menulango.resources.note_hint
import com.menulango.resources.note_no
import com.menulango.resources.note_title
import com.menulango.resources.note_waiter
import com.menulango.resources.order_add
import com.menulango.resources.order_add_person
import com.menulango.resources.order_clear
import com.menulango.resources.order_count
import com.menulango.resources.order_empty_person
import com.menulango.resources.order_for
import com.menulango.resources.order_guest
import com.menulango.resources.order_less
import com.menulango.resources.order_more
import com.menulango.resources.order_remove_person
import com.menulango.resources.order_rename
import com.menulango.resources.order_rename_hint
import com.menulango.resources.order_save
import com.menulango.resources.order_show_waiter
import com.menulango.resources.order_title
import com.menulango.resources.order_total
import com.menulango.resources.order_total_note
import com.menulango.resources.order_view
import com.menulango.resources.order_view_table
import com.menulango.resources.order_waiter_for_me
import com.menulango.resources.order_waiter_for_restaurant
import com.menulango.resources.order_waiter_hint
import com.menulango.resources.order_waiter_preparing
import com.menulango.resources.order_waiter_translated_by_google
import com.menulango.resources.order_waiter_unavailable
import com.menulango.resources.order_you
import com.menulango.resources.settings_cancel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToLong

/** "You", a name the diner gave, or "Guest 2" by seat. */
@Composable
internal fun TableOrder.label(diner: Diner): String =
    when {
        diner.id == TableOrder.OWNER -> stringResource(Res.string.order_you)
        diner.name != null -> diner.name
        else -> stringResource(Res.string.order_guest, diners.indexOf(diner) + 1)
    }

/** Prices as the menu prints them: a currency sign and two decimals. */
internal fun formatMoney(
    amount: Double,
    currencyPrefix: String,
): String {
    val cents = (amount * 100).roundToLong()
    return "$currencyPrefix${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

/**
 * The add button on a dish's plate: a plus, or — once the dish is in the order — how many.
 * Tapping always adds one more for whoever is being ordered for.
 */
@Composable
internal fun AddToOrderBadge(
    quantity: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val description = stringResource(Res.string.order_add)
    Box(
        modifier
            .size(34.dp)
            .pressable(onAdd)
            .shadow(Elevation.raised, CircleShape)
            .background(if (quantity > 0) colors.seal else colors.ink, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (quantity > 0) {
            Text(
                quantity.toString(),
                style = Paper.type.chip.copy(fontWeight = FontWeight.Bold),
                color = colors.onSeal,
                modifier = Modifier.clearAndSetSemantics { },
            )
        } else {
            Icon(PaperIcons.Plus, contentDescription = null, tint = colors.paper, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Once something is ordered, the basket bar: the brand's coral, what is in the order and roughly
 * what it costs. "Help me choose" rides alongside it as a round button.
 */
@Composable
internal fun OrderBar(
    order: TableOrder,
    currencyPrefix: String,
    onOpen: () -> Unit,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val chooseLabel = stringResource(Res.string.menu_choose_title)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .pressable(onChoose)
                .shadow(Elevation.floating, CircleShape, clip = false)
                .background(colors.ink, CircleShape)
                .semantics { contentDescription = chooseLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(PaperIcons.Cloche, contentDescription = null, tint = colors.seal, modifier = Modifier.size(24.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .pressable(onOpen)
                .shadow(Elevation.floating, Shapes.pill, clip = false)
                .clip(Shapes.pill)
                .suede(colors.seal)
                .padding(start = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // One person: "Your picks". Several: "Table picks", and who has picked how many, so the
            // bar never hides that it holds other people's choices too.
            val pickers = order.diners.filter { order.linesFor(it.id).isNotEmpty() }
            Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
                Text(
                    stringResource(if (pickers.size > 1) Res.string.order_view_table else Res.string.order_view),
                    style = Paper.type.button,
                    color = colors.onSeal,
                    maxLines = 1,
                )
                if (pickers.size > 1) {
                    Text(
                        pickers
                            .map { diner -> "${order.label(diner)} ${order.linesFor(diner.id).sumOf { it.quantity }}" }
                            .joinToString("  ·  "),
                        style = Paper.type.caption,
                        color = colors.onSeal.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                listOfNotNull(
                    stringResource(Res.string.order_count, order.dishCount),
                    order.total?.let { formatMoney(it, currencyPrefix) },
                ).joinToString("  ·  "),
                style = Paper.type.price.copy(fontWeight = FontWeight.Bold),
                color = colors.onSeal,
            )
            // Clearing is one tap, and undoable from the message that follows.
            IconAction(
                PaperIcons.Close,
                stringResource(Res.string.order_clear),
                onClear,
                tint = colors.onSeal,
                modifier = Modifier.padding(start = Space.xs),
            )
        }
    }
}

/**
 * "Your table": each person's dishes, with steppers, and the way to the waiter. People are tabs
 * across the top; the selected one is whoever the next dish is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderSheet(
    order: TableOrder,
    currencyPrefix: String,
    onChange: ((TableOrder) -> TableOrder) -> Unit,
    onShowWaiter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    var renaming by remember { mutableStateOf<Diner?>(null) }
    var noting by remember { mutableStateOf<OrderLine?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Shapes.sheet,
        containerColor = colors.raised,
        scrimColor = colors.scrim.copy(alpha = 0.4f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.rule) },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.gutter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                stringResource(Res.string.order_title),
                style = Paper.type.headline,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.order_for, order.label(order.diners.first { it.id == order.activeDinerId })),
                style = Paper.type.bodySmall,
                color = colors.inkMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.related),
                verticalArrangement = Arrangement.spacedBy(Space.related),
            ) {
                order.diners.forEach { diner ->
                    val active = diner.id == order.activeDinerId
                    FilterPill(
                        text = order.label(diner),
                        selected = active,
                        // Tapping the person already selected renames them.
                        onClick = { if (active) renaming = diner else onChange { it.activate(diner.id) } },
                        unselected = colors.sunk,
                    )
                }
                FilterPill(
                    text = stringResource(Res.string.order_add_person),
                    selected = false,
                    onClick = { onChange { it.addDiner(null) } },
                    unselected = colors.sunk,
                )
            }
            Text(stringResource(Res.string.order_rename_hint), style = Paper.type.caption, color = colors.inkFaint)

            order.diners.forEach { diner ->
                val lines = order.linesFor(diner.id)
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        order.label(diner),
                        style = Paper.type.dishName,
                        color = colors.ink,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (diner.id != TableOrder.OWNER) {
                        QuietButton(
                            stringResource(Res.string.order_remove_person),
                            { onChange { it.removeDiner(diner.id) } },
                            color = colors.inkMuted,
                        )
                    }
                }
                if (lines.isEmpty()) {
                    Text(
                        stringResource(Res.string.order_empty_person),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                    )
                }
                lines.forEach { line ->
                    OrderLineRow(
                        line,
                        currencyPrefix,
                        onLess = { onChange { it.change(line.dish, diner.id, -1) } },
                        onMore = { onChange { it.change(line.dish, diner.id, +1) } },
                        onNote = { noting = line },
                    )
                }
            }

            order.total?.let {
                Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.order_total),
                        style = Paper.type.bodySmall,
                        color = colors.inkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatMoney(it, currencyPrefix),
                        style = Paper.type.price.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                    )
                }
            }
            // An estimate, not a bill, and not an order either: say so where the total is read.
            Text(stringResource(Res.string.order_total_note), style = Paper.type.caption, color = colors.inkMuted)
            PrimaryButton(
                stringResource(Res.string.order_show_waiter),
                onShowWaiter,
                Modifier.fillMaxWidth().padding(top = Space.sm),
                enabled = !order.isEmpty,
            )
        }
    }

    noting?.let { line ->
        NoteEditor(
            dish = line.dish,
            initial = line.note.orEmpty(),
            onSave = { note ->
                onChange { it.setNote(line.dish.id, line.dinerId, note) }
                noting = null
            },
            onDismiss = { noting = null },
        )
    }

    renaming?.let { diner ->
        RenameDialog(
            initial = diner.name.orEmpty(),
            onSave = { name ->
                onChange { it.rename(diner.id, name) }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun OrderLineRow(
    line: OrderLine,
    currencyPrefix: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onNote: () -> Unit,
) {
    val colors = Paper.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DishPlate(line.dish, size = 44.dp)
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                line.dish.readableName,
                style = Paper.type.title,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            line.dish.price?.let {
                Text(
                    formatMoney(it.amount * line.quantity, currencyPrefix),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                )
            }
            // The note, or the way to add one: always in reach while the pick is fresh.
            Text(
                line.note?.let { "“$it”" } ?: stringResource(Res.string.note_add),
                style =
                    Paper.type.caption.copy(
                        fontWeight =
                            if (line.note ==
                                null
                            ) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                    ),
                color = colors.sealInk,
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .clip(Shapes.chip)
                        .pressable(onNote)
                        .padding(vertical = Space.xs),
            )
        }
        // A stepper, as in every basket: fewer, how many, more. Kept a clear gap from the name.
        Spacer(Modifier.width(Space.md))
        Row(
            Modifier.clip(Shapes.chip).background(colors.sunk),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(PaperIcons.Minus, stringResource(Res.string.order_less), onLess, tint = colors.ink)
            Text(
                line.quantity.toString(),
                style = Paper.type.price.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.widthIn(min = 16.dp),
            )
            IconAction(PaperIcons.Plus, stringResource(Res.string.order_more), onMore, tint = colors.ink)
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(stringResource(Res.string.order_rename), style = Paper.type.dishName) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true) },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
            ) { Text(stringResource(Res.string.order_save), color = colors.sealInk) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel), color = colors.ink) }
        },
    )
}

/**
 * The order turned round for the waiter: each dish in the menu's own words and script, large,
 * with how many. The diner's language stays small underneath, for the diner.
 */
@Composable
internal fun WaiterView(
    order: TableOrder,
    restaurantLanguageTag: String?,
    onTranslationsReady: (Map<String, String>) -> Unit,
    onClose: () -> Unit,
) {
    val colors = Paper.colors
    val type = Paper.type
    val translator = koinInject<NoteTranslator>()
    val dinerLanguageTag = Locale.current.toLanguageTag()
    val targetLanguageTag = restaurantLanguageTag?.takeIf(::isLanguageTag)
    val notes =
        remember(order.lines) {
            order.lines
                .mapNotNull { line ->
                    line.note?.let { TableOrder.noteKey(line.dish.id, line.dinerId) to it }
                }.toMap()
        }
    val missingTranslations =
        remember(notes, order.waiterTranslations) {
            notes.filterKeys { it !in order.waiterTranslations }
        }
    var translationState by remember { mutableStateOf<WaiterTranslationState>(WaiterTranslationState.NotNeeded) }
    var restaurantCopy by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(missingTranslations, dinerLanguageTag, targetLanguageTag) {
        when {
            missingTranslations.isEmpty() || notes.isEmpty() -> {
                translationState = WaiterTranslationState.Ready
            }

            targetLanguageTag == null -> {
                translationState = WaiterTranslationState.Unavailable
            }

            sameLanguage(dinerLanguageTag, targetLanguageTag) -> {
                onTranslationsReady(missingTranslations)
                translationState = WaiterTranslationState.Ready
            }

            else -> {
                translationState = WaiterTranslationState.Preparing
                translationState =
                    when (val result = translator.translate(missingTranslations, dinerLanguageTag, targetLanguageTag)) {
                        is NoteTranslationResult.Ready -> {
                            onTranslationsReady(result.translations)
                            WaiterTranslationState.Ready
                        }

                        NoteTranslationResult.Unavailable -> {
                            WaiterTranslationState.Unavailable
                        }
                    }
            }
        }
    }

    val restaurantCopyAvailable = notes.isEmpty() || translationState == WaiterTranslationState.Ready
    if (!restaurantCopyAvailable && restaurantCopy) restaurantCopy = false
    Column(
        Modifier
            .fillMaxSize()
            .felt(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(PaperIcons.Close, stringResource(Res.string.action_close), onClose, background = colors.raised)
            Text(
                stringResource(Res.string.order_show_waiter),
                style = type.dishName,
                color = colors.ink,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.gutter),
        ) {
            Text(
                stringResource(Res.string.order_waiter_hint),
                style = type.bodySmall,
                color = colors.inkMuted,
            )
            // Two views of the same order, not a filter: a segmented control, like iOS uses.
            SegmentedControl(
                options =
                    listOf(
                        stringResource(Res.string.order_waiter_for_me),
                        stringResource(Res.string.order_waiter_for_restaurant),
                    ),
                selected = if (restaurantCopy) 1 else 0,
                onSelect = { restaurantCopy = it == 1 },
                enabled = { it == 0 || restaurantCopyAvailable },
            )
            when (translationState) {
                WaiterTranslationState.Preparing -> {
                    Text(
                        stringResource(Res.string.order_waiter_preparing),
                        style = type.caption,
                        color = colors.inkMuted,
                    )
                }

                WaiterTranslationState.Unavailable -> {
                    Text(
                        stringResource(Res.string.order_waiter_unavailable),
                        style = type.caption,
                        color = colors.sealInk,
                    )
                }

                WaiterTranslationState.Ready -> {
                    if (notes.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.order_waiter_translated_by_google),
                            style = type.caption,
                            color = colors.inkFaint,
                        )
                    }
                }

                WaiterTranslationState.NotNeeded -> {}
            }
            order.diners.filter { order.linesFor(it.id).isNotEmpty() }.forEach { diner ->
                Column(
                    Modifier.fillMaxWidth().paper(colors.raised, Shapes.card).padding(Space.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    if (order.diners.size > 1) {
                        Text(order.label(diner), style = type.label, color = colors.inkFaint)
                    }
                    order.linesFor(diner.id).forEach { line ->
                        WaiterLine(
                            dish = line.dish,
                            quantity = line.quantity,
                            dinerNote = line.note,
                            restaurantNote = order.waiterNote(line.dish.id, line.dinerId),
                            restaurantCopy = restaurantCopy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaiterLine(
    dish: Dish,
    quantity: Int,
    dinerNote: String?,
    restaurantNote: String?,
    restaurantCopy: Boolean,
) {
    val colors = Paper.colors
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$quantity ×",
            style = Paper.type.dishTitle,
            color = colors.sealInk,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Column(Modifier.weight(1f)) {
            // One name: the printed one for the waiter to find on their menu, yours for you.
            Text(
                if (restaurantCopy) dish.originalName else dish.readableName,
                style = Paper.type.dishTitle,
                color = colors.ink,
            )
            (if (restaurantCopy) restaurantNote else dinerNote)?.let {
                Text(
                    stringResource(Res.string.note_waiter, it),
                    style = Paper.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.sealInk,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

private enum class WaiterTranslationState { NotNeeded, Preparing, Ready, Unavailable }

private fun isLanguageTag(tag: String): Boolean = LANGUAGE_TAG.matches(tag)

private fun sameLanguage(
    first: String,
    second: String,
): Boolean = first.substringBefore('-').equals(second.substringBefore('-'), ignoreCase = true)

private val LANGUAGE_TAG = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")

/**
 * How someone wants their dish, written while the choice is fresh. Suggestions come from the dish
 * itself — "No cucumber", "Allergic: milk" — so most notes are a tap or two, and anything else can
 * be typed.
 */
@Composable
internal fun NoteEditor(
    dish: Dish,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    var note by remember { mutableStateOf(initial) }
    val noPrefix = stringResource(Res.string.note_no)
    val allergyPrefix = stringResource(Res.string.note_allergic)
    val suggestions =
        remember(dish) {
            dish.ingredients.take(MAX_INGREDIENT_SUGGESTIONS).map { "$noPrefix $it" } +
                dish.allergens.likelyContains
                    .take(MAX_ALLERGY_SUGGESTIONS)
                    .map { "$allergyPrefix $it" }
        }
    val parts = note.split(NOTE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(stringResource(Res.string.note_title, dish.readableName), style = Paper.type.dishName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.related),
                        verticalArrangement = Arrangement.spacedBy(Space.related),
                    ) {
                        suggestions.forEach { suggestion ->
                            val on = parts.any { it.equals(suggestion, ignoreCase = true) }
                            FilterPill(
                                text = suggestion,
                                selected = on,
                                onClick = {
                                    note =
                                        if (on) {
                                            parts.filterNot { it.equals(suggestion, ignoreCase = true) }
                                        } else {
                                            parts + suggestion
                                        }.joinToString(NOTE_SEPARATOR + " ")
                                },
                                unselected = colors.sunk,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE) },
                    placeholder = {
                        Text(
                            stringResource(Res.string.note_hint),
                            style = Paper.type.bodySmall,
                            color = Paper.colors.inkFaint,
                        )
                    },
                    shape = Shapes.button,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(note) },
            ) { Text(stringResource(Res.string.order_save), color = colors.sealInk) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel), color = colors.ink) }
        },
    )
}

private const val NOTE_SEPARATOR = ","
private const val MAX_NOTE = 140
private const val MAX_INGREDIENT_SUGGESTIONS = 6
private const val MAX_ALLERGY_SUGGESTIONS = 3

private const val MAX_NAME = 20
