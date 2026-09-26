package com.menulango.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.resources.Res
import com.menulango.resources.search_clear
import com.menulango.resources.search_hide_tags
import com.menulango.resources.search_tags
import org.jetbrains.compose.resources.stringResource

/** One optional field a search can also look in, shown as a pill the diner switches on. */
internal data class SearchTag(
    val label: String,
    val selected: Boolean,
    val onToggle: () -> Unit,
)

/**
 * A local search field, iOS style: names by default, and a small "Add tags" beside it for the
 * diner who wants to reach further (ingredients, descriptions, dishes inside menus). Tags never
 * switch themselves on, so a plain search always means what it says.
 */
@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    tags: List<SearchTag>,
    modifier: Modifier = Modifier,
    pill: @Composable (SearchTag) -> Unit,
) {
    val colors = Paper.colors
    val focus = LocalFocusManager.current
    var showTags by remember { mutableStateOf(false) }
    val anyTag = tags.any { it.selected }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(Shapes.pill)
                    .paper(colors.raised)
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
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
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
            if (tags.isNotEmpty()) {
                QuietButton(
                    stringResource(if (showTags) Res.string.search_hide_tags else Res.string.search_tags),
                    { showTags = !showTags },
                    color = colors.sealInk,
                    singleLine = true,
                )
            }
        }
        AnimatedVisibility(
            visible = showTags || anyTag,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Space.related),
                horizontalArrangement = Arrangement.spacedBy(Space.related),
            ) {
                tags.forEach { pill(it) }
            }
        }
    }
}
