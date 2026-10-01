package com.menulango.feature.dish

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.CoverTips
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.FlagChips
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.SecondaryButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.chips
import com.menulango.core.ui.foodGroup
import com.menulango.core.ui.paperShimmer
import com.menulango.core.ui.pressable
import com.menulango.data.menu.model.Allergens
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.Nutrition
import com.menulango.data.photo.DishPhoto
import com.menulango.data.photo.DishPhotos
import com.menulango.feature.menu.rememberPriceConverter
import com.menulango.feature.menu.sameDishNameAs
import com.menulango.platform.urlQueryComponent
import com.menulango.resources.Res
import com.menulango.resources.action_close
import com.menulango.resources.dish_as_printed
import com.menulango.resources.dish_ask_restaurant
import com.menulango.resources.dish_best_guess_note
import com.menulango.resources.dish_check_before
import com.menulango.resources.dish_eaten
import com.menulango.resources.dish_eaten_done
import com.menulango.resources.dish_how_it_is_made
import com.menulango.resources.dish_ingredients
import com.menulango.resources.dish_may_contain
import com.menulango.resources.dish_nutrition
import com.menulango.resources.dish_nutrition_carbs
import com.menulango.resources.dish_nutrition_fat
import com.menulango.resources.dish_nutrition_kcal
import com.menulango.resources.dish_nutrition_note
import com.menulango.resources.dish_nutrition_protein
import com.menulango.resources.dish_often_contains
import com.menulango.resources.dish_open_menu
import com.menulango.resources.dish_photo_credit
import com.menulango.resources.dish_photo_description
import com.menulango.resources.dish_photo_typical
import com.menulango.resources.dish_see_photos
import com.menulango.resources.dish_sheet_description
import com.menulango.resources.dish_unknown_method
import com.menulango.resources.dish_unknown_what
import com.menulango.resources.dish_what_it_is
import com.menulango.resources.list_joiner
import com.menulango.resources.order_add
import com.menulango.resources.order_add_another
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
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
 * @param bottomInset room for anything floating over the bottom of the screen, such as the tab bar.
 * @param onOpenMenu shown as a button when the sheet is opened away from its menu.
 */
@Composable
internal fun DishSheet(
    dish: Dish,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    history: DishHistoryControl? = null,
    order: DishOrderControl? = null,
    bottomInset: Dp = 0.dp,
    onOpenMenu: (() -> Unit)? = null,
    /** The menu's currency, for a price that doesn't name its own. */
    menuCurrency: String? = null,
) {
    CoverTips()
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
            SheetHeader(dish, dragToResize, nameModifier, onDismiss, menuCurrency = menuCurrency)
            SheetBody(dish, history, order, bottomInset, onOpenMenu)
        }
    }
}

/**
 * The dish's explanation without a sheet of its own, for showing inside another sheet (the
 * "What you picked" journal), which then moves between its list and the dish in place rather
 * than stacking a second sheet on top.
 */
@Composable
internal fun DishDetails(
    dish: Dish,
    onOpenMenu: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(dish, Modifier, Modifier, onDismiss = {}, showHandle = false)
        SheetBody(dish, history = null, order = null, bottomInset = 0.dp, onOpenMenu = onOpenMenu)
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
    showHandle: Boolean = true,
    menuCurrency: String? = null,
) {
    val colors = Paper.colors
    val type = Paper.type
    val converter = rememberPriceConverter()
    Column(dragToResize.fillMaxWidth().padding(horizontal = Space.gutter)) {
        if (showHandle) {
            Box(
                Modifier
                    .padding(top = Space.sm)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(5.dp)
                    .background(colors.rule, Shapes.pill),
            )
        }
        // No close button: the handle, a tap outside, the back gesture and the screen reader's
        // "close" action all dismiss the sheet, and a button here crowded the plate.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.md)) {
            Column(Modifier.weight(1f)) {
                Text(dish.readableName, style = type.dishTitle, color = colors.ink, modifier = nameModifier)
                // The name as printed, to point at on the menu, only when it is not the same words.
                if (!dish.readableName.sameDishNameAs(dish.originalName)) {
                    Text(
                        text = stringResource(Res.string.dish_as_printed, dish.originalName),
                        style = type.original,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                dish.price?.let {
                    // With conversion on: "€12.50 → ≈ ₦21,400", the diner's figure quieter than the menu's.
                    val converted = converter?.convert(it.amount, it.currency ?: menuCurrency)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Space.sm),
                    ) {
                        Text(
                            it.asPrinted,
                            style = type.price.copy(fontWeight = FontWeight.Bold),
                            color = colors.sealInk,
                            modifier =
                                Modifier
                                    .background(colors.sealWash, Shapes.chip)
                                    .padding(horizontal = Space.sm, vertical = 6.dp),
                        )
                        if (converted != null) {
                            Text(
                                "→ $converted",
                                style = type.price,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(start = Space.sm),
                            )
                        }
                    }
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
    bottomInset: Dp,
    onOpenMenu: (() -> Unit)?,
) {
    val colors = Paper.colors
    val type = Paper.type
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .navigationBarsPadding()
            .padding(bottom = Space.xl + bottomInset),
    ) {
        Column(Modifier.widthIn(max = Space.readingWidth), verticalArrangement = Arrangement.spacedBy(Space.related)) {
            dish.wikiTitle?.let { DishPhotoPanel(it) }
            dish.pitch?.let { Text(it, style = type.bodySmall, color = colors.inkMuted) }
            FlagChips(dish.chips(limit = 6), Modifier.padding(top = Space.xs))
            SeeItButton(dish, Modifier.padding(top = Space.xs))

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

            dish.nutrition?.let { Section(stringResource(Res.string.dish_nutrition)) { NutritionPanel(it) } }

            Section(null) { AllergenPanel(dish.allergens) }

            // The actions sit together, 16 dp apart, like any stack of buttons.
            if (order != null || history != null || onOpenMenu != null) {
                Spacer(Modifier.height(Space.md))
                Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                    order?.let { control ->
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
                        SecondaryButton(
                            text =
                                stringResource(
                                    if (control.eaten) Res.string.dish_eaten_done else Res.string.dish_eaten,
                                ),
                            icon = if (control.eaten) PaperIcons.Check else null,
                            onClick = { control.onChange(!control.eaten) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    onOpenMenu?.let {
                        SecondaryButton(
                            text = stringResource(Res.string.dish_open_menu),
                            icon = PaperIcons.ChevronRight,
                            onClick = it,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
 * A photo of the dish as it's usually served, from Wikimedia Commons, credited as its licence asks.
 * A soft shimmer while it loads; if there is no photo, or it can't be fetched, the panel quietly
 * isn't there and the plate beside the name does the job, as it always has.
 */
@Composable
private fun DishPhotoPanel(wikiTitle: String) {
    val colors = Paper.colors
    val photos = koinInject<DishPhotos>()
    val uriHandler = LocalUriHandler.current
    var loaded by remember(wikiTitle) { mutableStateOf<PhotoState?>(null) }
    LaunchedEffect(wikiTitle) {
        val found = photos.load(wikiTitle)
        val image = found?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() }
        loaded = if (found != null && image != null) PhotoState.Shown(found.photo, image) else PhotoState.None
    }
    // Room is held, shimmering, only once the proxy has said this dish has a photo. Otherwise the
    // sheet promises nothing: no photo means no gap, and one that turns up late slides open.
    val phase =
        loaded ?: if (photos.hasPhoto(wikiTitle) == true) PhotoState.Loading else PhotoState.None
    AnimatedContent(
        targetState = phase,
        contentKey = { it::class },
        transitionSpec = {
            fadeIn(tween(Motion.SHEET_MS)) togetherWith fadeOut(tween(Motion.QUICK_MS)) using
                SizeTransform(clip = true) { _, _ -> tween(Motion.SHEET_MS, easing = Motion.standard) }
        },
        label = "dish-photo",
    ) { shown ->
        when (shown) {
            PhotoState.None -> {
                Box(Modifier.fillMaxWidth())
            }

            PhotoState.Loading -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(PHOTO_RATIO)
                        .clip(Shapes.tile)
                        .paperShimmer(),
                )
            }

            is PhotoState.Shown -> {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(PHOTO_RATIO).clip(Shapes.tile)) {
                        Image(
                            shown.image,
                            contentDescription = stringResource(Res.string.dish_photo_description, wikiTitle),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Honest about what this is: how the dish usually looks, not this kitchen's plate.
                        Text(
                            stringResource(Res.string.dish_photo_typical),
                            style = Paper.type.label,
                            color = colors.ink,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(Space.sm)
                                    .background(colors.raised.copy(alpha = 0.9f), Shapes.chip)
                                    .padding(horizontal = Space.sm, vertical = 4.dp),
                        )
                    }
                    // The credit the licence asks for; tapping it opens the photo's page on Commons.
                    Text(
                        stringResource(Res.string.dish_photo_credit, shown.photo.credit, shown.photo.license),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .clip(Shapes.chip)
                                .pressable({ uriHandler.openUri(shown.photo.source) })
                                .semantics { role = Role.Button }
                                .heightIn(min = Space.touchTarget)
                                .wrapContentHeight(),
                    )
                }
            }
        }
    }
}

private sealed interface PhotoState {
    data object Loading : PhotoState

    data object None : PhotoState

    class Shown(
        val photo: DishPhoto,
        val image: ImageBitmap,
    ) : PhotoState
}

/** Landscape, like a photo of a plate on a table. */
private const val PHOTO_RATIO = 4f / 3f

/**
 * Photos of the dish, many real ones, in Google Images: opened in the browser rather than copied
 * into the app, so they are always the right dish, always current and never someone else's photo
 * republished. Searched by both names, the one we use and the one printed, for the truest results.
 */
@Composable
private fun SeeItButton(
    dish: Dish,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val uriHandler = LocalUriHandler.current
    val query =
        if (dish.readableName.sameDishNameAs(dish.originalName)) {
            dish.readableName
        } else {
            "${dish.readableName} ${dish.originalName}"
        }
    Row(
        modifier
            .heightIn(min = Space.touchTarget)
            .clip(Shapes.pill)
            .background(colors.sunk)
            .pressable({ uriHandler.openUri("https://www.google.com/search?udm=2&q=${query.urlQueryComponent()}") })
            .semantics { role = Role.Button }
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(stringResource(Res.string.dish_see_photos), style = Paper.type.button, color = colors.ink)
        Icon(PaperIcons.ArrowUpRight, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
    }
}

/**
 * Set in Nunito italic beside an aubergine bar, on a tile the colour of the dish's kind of food: the
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
 * Energy and the three macronutrients for one plate, as four small tiles: an estimate from how the
 * dish is usually served, and labelled as one.
 */
@Composable
private fun NutritionPanel(nutrition: Nutrition) {
    val colors = Paper.colors
    val type = Paper.type
    Column(verticalArrangement = Arrangement.spacedBy(Space.related)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.related)) {
            listOf(
                nutrition.kcal.toString() to stringResource(Res.string.dish_nutrition_kcal),
                "${nutrition.proteinG} g" to stringResource(Res.string.dish_nutrition_protein),
                "${nutrition.carbsG} g" to stringResource(Res.string.dish_nutrition_carbs),
                "${nutrition.fatG} g" to stringResource(Res.string.dish_nutrition_fat),
            ).forEach { (value, label) ->
                Column(
                    Modifier
                        .weight(1f)
                        .background(colors.sunk, Shapes.tile)
                        .padding(vertical = Space.sm, horizontal = Space.xs)
                        .semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = type.price.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
                    Text(label, style = type.caption, color = colors.inkMuted, maxLines = 1)
                }
            }
        }
        Text(stringResource(Res.string.dish_nutrition_note), style = type.caption, color = colors.inkMuted)
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

/** Between stacked action buttons: 16 dp, the usual gap in Apple's and Material's guidelines. */
private val BUTTON_GAP = Space.md
private const val FLING_VELOCITY = 1_200f
private val DRAG_THRESHOLD = 96.dp
