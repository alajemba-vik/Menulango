package com.menulango.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.resources.Res
import com.menulango.resources.search_cancel
import com.menulango.resources.search_clear
import com.menulango.resources.search_tags
import org.jetbrains.compose.resources.stringResource

/** One optional field a search can also look in, which the diner switches on. */
internal data class SearchTag(
    val label: String,
    val selected: Boolean,
    val onToggle: () -> Unit,
)

/**
 * A local search, opened from a search icon, iOS style: the field takes focus at once, Cancel
 * closes and clears it. Names are searched by default; "Search tags" underneath lets the diner
 * reach further (ingredients, descriptions, dishes inside menus). Tags are quiet grey tokens,
 * not the coral filter chips: they change where the search looks, not what the menu shows.
 */
@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    tags: List<SearchTag>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Paper.colors
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var showTags by remember { mutableStateOf(tags.any { it.selected }) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = FIELD_HEIGHT)
                    .clip(Shapes.pill)
                    .background(colors.raised)
                    .border(Space.hairline, colors.rule, Shapes.pill)
                    .padding(start = Space.md, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PaperIcons.Search,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.related))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = Paper.type.bodySmall, color = colors.inkFaint, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = Paper.type.bodySmall.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.seal),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = placeholder },
                    )
                }
                if (query.isNotEmpty()) {
                    IconAction(
                        PaperIcons.Close,
                        stringResource(Res.string.search_clear),
                        { onQuery("") },
                        tint = colors.inkMuted,
                    )
                }
            }
            QuietButton(stringResource(Res.string.search_cancel), onCancel, color = colors.sealInk, singleLine = true)
        }
        if (tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Space.related),
                horizontalArrangement = Arrangement.spacedBy(Space.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.search_tags),
                    style = Paper.type.chip.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.sealInk,
                    modifier = Modifier.clip(Shapes.chip).pressable({ showTags = !showTags }).padding(vertical = 6.dp),
                )
                AnimatedVisibility(
                    visible = showTags,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.related)) {
                        tags.forEach { SearchTagToken(it) }
                    }
                }
            }
        }
    }
}

/** A grey token: "+ Ingredients" to add, "✓ Ingredients" once it is part of the search. */
@Composable
private fun SearchTagToken(tag: SearchTag) {
    val colors = Paper.colors
    Text(
        (if (tag.selected) "✓ " else "+ ") + tag.label,
        style = Paper.type.chip,
        color = if (tag.selected) colors.ink else colors.inkMuted,
        modifier =
            Modifier
                .clip(Shapes.chip)
                .background(if (tag.selected) colors.sunk else colors.paper)
                .border(Space.hairline, if (tag.selected) colors.inkFaint else colors.rule, Shapes.chip)
                .toggleable(value = tag.selected, role = Role.Checkbox, onValueChange = { tag.onToggle() })
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** The height every text field in the app shares, search or not. */
internal val FIELD_HEIGHT = 44.dp
