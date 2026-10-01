package com.menulango.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import kotlinx.coroutines.delay

/** One optional field a search can also look in, which the diner switches on. */
internal data class SearchTag(
    val label: String,
    val selected: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The field's placeholder, then each example in turn, rising into place every few seconds. Held
 * still under reduce motion, and never read aloud in turns: screen readers get the field's label.
 */
@Composable
internal fun RotatingHint(lines: List<String>) {
    val colors = Paper.colors
    var index by remember(lines) { mutableStateOf(0) }
    val reduceMotion = Paper.reduceMotion
    LaunchedEffect(lines, reduceMotion) {
        if (lines.size < 2 || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(HINT_TURN_MS)
            index = (index + 1) % lines.size
        }
    }
    AnimatedContent(
        targetState = index,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "search-hint",
        modifier = Modifier.clearAndSetSemantics { },
    ) { i ->
        Text(
            lines[i],
            style = Paper.type.bodySmall,
            color = colors.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val HINT_TURN_MS = 3_000L

/** A grey token: "+ Ingredients" to add, "✓ Ingredients" once it is part of the search. */
@Composable
internal fun SearchTagToken(tag: SearchTag) {
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

/**
 * Text entry fields keep Material's rounded rectangle (search stays a capsule, as on iOS and
 * Android), but share the search field's paper fill, hairline border and aubergine focus, so every
 * field in the app reads as one family.
 */
@Composable
internal fun paperFieldColors(): TextFieldColors {
    val colors = Paper.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.raised,
        unfocusedContainerColor = colors.raised,
        focusedBorderColor = colors.seal,
        unfocusedBorderColor = colors.outline,
        cursorColor = colors.seal,
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        focusedLabelColor = colors.sealInk,
        unfocusedLabelColor = colors.inkMuted,
    )
}
