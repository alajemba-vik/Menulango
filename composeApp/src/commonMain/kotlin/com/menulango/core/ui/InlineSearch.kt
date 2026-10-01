package com.menulango.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Motion
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.resources.Res
import com.menulango.resources.search_cancel
import com.menulango.resources.search_clear
import org.jetbrains.compose.resources.stringResource

/**
 * The app's one search bar, used wherever a list can be searched. A row of filter chips led by a
 * Search chip; tapping it grows the chip, in place, into a capsule field the full width of the
 * row, keyboard up, while the filters slide down onto a row of their own beneath it. Anything
 * that changes where the search looks ("+ Ingredients") leads that lower row as a grey token.
 * Cancel shrinks it back into the chip.
 *
 * @param hints examples that take turns in the empty field, to show what the search can do.
 * @param gutter side padding inside the rows, so chips scroll edge to edge on full-bleed pages.
 * @param filters the chips beside the search, or beneath it while it is open.
 */
@Composable
internal fun InlineSearchRow(
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    hints: List<String> = emptyList(),
    tags: List<SearchTag> = emptyList(),
    gutter: Dp = 0.dp,
    filters: @Composable RowScope.() -> Unit,
) {
    val colors = Paper.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val growth = remember { Animatable(if (open) 1f else 0f) }
    val calm = Paper.reduceMotion
    var chipWidth by remember { mutableIntStateOf(0) }
    LaunchedEffect(open) {
        if (open) {
            scroll.animateScrollTo(0)
            focus.requestFocus()
        } else {
            focusManager.clearFocus()
        }
        val spec =
            if (calm) {
                tween<Float>(Motion.QUICK_MS)
            } else {
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            }
        growth.animateTo(if (open) 1f else 0f, spec)
    }
    val edge by animateColorAsState(
        if (open) colors.seal else colors.outline,
        tween(Motion.QUICK_MS),
        label = "search-edge",
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val full = with(density) { (maxWidth - gutter * 2).toPx() }
        // Resting closed, the chip wraps its label. Open, or on the way, it spans from its own
        // width to the row's; come back to a page with search still open, it is simply full width.
        val width =
            when {
                growth.value == 0f && !open -> null
                chipWidth == 0 -> full
                else -> chipWidth + (full - chipWidth).coerceAtLeast(0f) * growth.value
            }
        val height = CHIP_HEIGHT + (FIELD_HEIGHT - CHIP_HEIGHT) * growth.value
        Column {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(Space.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .then(if (width != null) Modifier.width(with(density) { width.toDp() }) else Modifier)
                        .height(height)
                        .clip(Shapes.pill)
                        .background(colors.raised)
                        .border(Space.hairline, edge, Shapes.pill)
                        .then(
                            if (open) {
                                Modifier
                            } else {
                                Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onOpen)
                            },
                        ).then(
                            if (!open && growth.value == 0f) {
                                Modifier.onSizeChanged { chipWidth = it.width }
                            } else {
                                Modifier
                            },
                        ).padding(start = Space.md, end = if (open) Space.xs else Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        PaperIcons.Search,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    if (open) {
                        Box(
                            Modifier.weight(1f).padding(start = Space.related),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) RotatingHint(listOf(placeholder) + hints)
                            BasicTextField(
                                value = query,
                                onValueChange = onQuery,
                                singleLine = true,
                                textStyle = Paper.type.bodySmall.copy(color = colors.ink),
                                cursorBrush = SolidColor(colors.seal),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focus)
                                        .semantics { contentDescription = placeholder },
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconAction(
                                PaperIcons.Close,
                                stringResource(Res.string.search_clear),
                                { onQuery("") },
                                tint = colors.inkMuted,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Text(
                            stringResource(Res.string.search_cancel),
                            style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.sealInk,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .clip(Shapes.pill)
                                    .clickable(role = Role.Button, onClick = onClose)
                                    .padding(horizontal = Space.sm, vertical = 6.dp),
                        )
                    } else {
                        Text(
                            label,
                            style = Paper.type.chip,
                            color = colors.ink,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                // Beside the chip while search is closed; they leave as it grows, and reappear below.
                AnimatedVisibility(
                    visible = !open,
                    enter = fadeIn(tween(Motion.QUICK_MS, delayMillis = Motion.QUICK_MS)),
                    exit = fadeOut(tween(Motion.QUICK_MS)),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.related)) { filters() }
                }
            }
            AnimatedVisibility(
                visible = open,
                enter =
                    if (calm) {
                        fadeIn(tween(Motion.QUICK_MS))
                    } else {
                        fadeIn(tween(Motion.QUICK_MS)) +
                            expandVertically(tween(Motion.SHEET_MS, easing = Motion.standard)) +
                            slideInVertically(tween(Motion.SHEET_MS, easing = Motion.standard)) { -it / 2 }
                    },
                exit =
                    if (calm) {
                        fadeOut(tween(Motion.QUICK_MS))
                    } else {
                        fadeOut(tween(Motion.QUICK_MS)) +
                            shrinkVertically(tween(Motion.QUICK_MS, easing = Motion.standard))
                    },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.related),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tags.forEach { SearchTagToken(it) }
                    filters()
                }
            }
        }
    }
}

/**
 * A one-line text field in the search bar's capsule, for anywhere the app asks for a short word:
 * the same height, fill, hairline and aubergine focus as search, so every field reads as one family.
 */
@Composable
internal fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val colors = Paper.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edge by animateColorAsState(
        if (focused) colors.seal else colors.outline,
        tween(Motion.QUICK_MS),
        label = "field-edge",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Paper.type.bodySmall.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.seal),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FIELD_HEIGHT)
                    .clip(Shapes.pill)
                    .background(colors.raised)
                    .border(Space.hairline, edge, Shapes.pill)
                    .padding(horizontal = Space.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = Paper.type.bodySmall, color = colors.inkFaint, maxLines = 1)
                }
                field()
            }
        },
    )
}

/** The height of a closed Search chip, matching the filter chips beside it. */
private val CHIP_HEIGHT = 40.dp
