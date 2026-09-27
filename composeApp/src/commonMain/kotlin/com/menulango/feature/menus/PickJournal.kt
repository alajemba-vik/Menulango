package com.menulango.feature.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.DishPlate
import com.menulango.core.ui.paperFieldColors
import com.menulango.core.ui.pressable
import com.menulango.data.menu.model.Dish
import com.menulango.resources.Res
import com.menulango.resources.dish_sheet_description
import com.menulango.resources.menus_journal_body
import com.menulango.resources.menus_journal_hint
import com.menulango.resources.menus_journal_title
import org.jetbrains.compose.resources.stringResource

/**
 * A light diary for one menu: the dishes picked from it (picked, not ordered, since only the
 * diner knows what reached the table) and a line on each. Tapping a dish opens its sheet again.
 * Deliberately small: a place to jot, not a journal to keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickJournalSheet(
    title: String,
    picked: List<Dish>,
    notes: Map<String, String>,
    onNote: (dishId: String, text: String) -> Unit,
    onOpen: (Dish) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
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
                stringResource(Res.string.menus_journal_title),
                style = Paper.type.headline,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(title, style = Paper.type.bodySmall, color = colors.inkMuted)
            Text(stringResource(Res.string.menus_journal_body), style = Paper.type.caption, color = colors.inkMuted)
            picked.forEach { dish -> JournalEntry(dish, notes[dish.id].orEmpty(), onNote, onOpen) }
        }
    }
}

@Composable
private fun JournalEntry(
    dish: Dish,
    saved: String,
    onNote: (dishId: String, text: String) -> Unit,
    onOpen: (Dish) -> Unit,
) {
    val colors = Paper.colors
    val focus = LocalFocusManager.current
    var draft by remember(dish.id, saved) { mutableStateOf(saved) }
    val openLabel = stringResource(Res.string.dish_sheet_description, dish.readableName)
    Column(Modifier.fillMaxWidth().padding(top = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.tile)
                .pressable({ onOpen(dish) })
                .padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            DishPlate(dish, size = 44.dp)
            Text(
                dish.readableName,
                style = Paper.type.dishName,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                PaperIcons.ChevronRight,
                contentDescription = null,
                tint = colors.inkFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        // Saved when the diner moves on, so there's no Save button to forget.
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(280) },
            placeholder = { Text(stringResource(Res.string.menus_journal_hint), color = colors.inkFaint) },
            colors = paperFieldColors(),
            shape = Shapes.button,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier =
                Modifier.fillMaxWidth().onFocusChanged { state ->
                    if (!state.isFocused && draft != saved) onNote(dish.id, draft)
                },
        )
    }
}
