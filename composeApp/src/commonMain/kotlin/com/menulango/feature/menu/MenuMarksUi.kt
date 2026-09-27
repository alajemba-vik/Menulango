package com.menulango.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.menulango.core.design.Paper
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.paperFieldColors
import com.menulango.data.menu.model.Dish
import com.menulango.resources.Res
import com.menulango.resources.menu_featured_reason
import com.menulango.resources.menu_featured_reason_title
import com.menulango.resources.menu_featured_reason_unknown
import com.menulango.resources.menu_hidden_by_words
import com.menulango.resources.menu_hidden_by_words_body
import com.menulango.resources.menu_hidden_by_you
import com.menulango.resources.menu_hidden_title
import com.menulango.resources.menu_hide
import com.menulango.resources.menu_rename_hint
import com.menulango.resources.menu_rename_title
import com.menulango.resources.menu_unhide
import com.menulango.resources.order_save
import com.menulango.resources.settings_cancel
import com.menulango.resources.tip_got_it
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.time.Clock

/**
 * Notices a diner struggling to choose: scrolling back and forth across a long menu, without
 * picking anything, several times in a couple of minutes. It only ever raises its hand once;
 * the tip system remembers that it has been seen.
 */
internal class BrowsingSignal {
    var hinting by mutableStateOf(false)
        private set

    private var travel = 0f
    private var direction = 0
    private val reversals = ArrayDeque<Long>()

    fun connection(
        viewport: Float,
        eligible: () -> Boolean,
    ): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = consumed.y
                if (dy == 0f || hinting || !eligible()) return Offset.Zero
                val now = if (dy < 0) 1 else -1
                if (now == direction) {
                    travel += abs(dy)
                } else {
                    // A turn only counts after real travel: a screen's worth, not a nudge.
                    if (direction != 0 && travel > viewport) {
                        val at = Clock.System.now().toEpochMilliseconds()
                        reversals.addLast(at)
                        while (reversals.isNotEmpty() &&
                            at - reversals.first() > BROWSE_WINDOW_MS
                        ) {
                            reversals.removeFirst()
                        }
                        if (reversals.size >= BROWSE_REVERSALS) hinting = true
                    }
                    direction = now
                    travel = abs(dy)
                }
                return Offset.Zero
            }
        }
}

internal const val BROWSE_MIN_DISHES = 15
private const val BROWSE_REVERSALS = 5
private const val BROWSE_WINDOW_MS = 150_000L

/**
 * A dish row that can be swiped left to hide it from this menu, as mail apps archive. Screen
 * readers get the same thing as an action, since a swipe can't be heard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToHide(
    enabled: Boolean,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val colors = Paper.colors
    val hideLabel = stringResource(Res.string.menu_hide)
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier =
            modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(hideLabel) {
                            onHide()
                            true
                        },
                    )
            },
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onHide() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(Shapes.card)
                    .background(colors.sunk)
                    .padding(horizontal = Space.gutter),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(hideLabel, style = Paper.type.button, color = colors.inkMuted)
            }
        },
    ) { content() }
}

/**
 * Everything kept out of view on this menu, in two honest groups: the dishes the diner swiped
 * away (each can come back), and the ones their Leave out words removed, with the word, since
 * those change in Settings rather than here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenDishesSheet(
    byYou: List<Dish>,
    byWords: List<Pair<Dish, String>>,
    onUnhide: (String) -> Unit,
    onOpen: (String) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                stringResource(Res.string.menu_hidden_title),
                style = Paper.type.headline,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            if (byYou.isNotEmpty()) {
                SubLabel(stringResource(Res.string.menu_hidden_by_you))
                byYou.forEach { dish ->
                    HiddenRow(
                        dish.readableName,
                        action = stringResource(Res.string.menu_unhide),
                        onOpen = { onOpen(dish.id) },
                    ) {
                        onUnhide(dish.id)
                    }
                }
            }
            if (byWords.isNotEmpty()) {
                SubLabel(stringResource(Res.string.menu_hidden_by_words))
                Text(
                    stringResource(Res.string.menu_hidden_by_words_body),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                )
                byWords.forEach { (dish, word) ->
                    HiddenRow(dish.readableName, detail = word, onOpen = { onOpen(dish.id) })
                }
            }
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = Paper.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = Paper.colors.ink,
        modifier = Modifier.padding(top = Space.md).semantics { heading() },
    )
}

@Composable
private fun HiddenRow(
    name: String,
    detail: String? = null,
    action: String? = null,
    onOpen: () -> Unit,
    onAction: () -> Unit = {},
) {
    val colors = Paper.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Space.touchTarget), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            QuietButton(name, onOpen, color = colors.ink)
            detail?.let {
                Text(
                    "“$it”",
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }
        action?.let { QuietButton(it, onAction, color = colors.sealInk, singleLine = true) }
    }
}

/** Names the menu after its restaurant; menus rarely print one. Clearing the field undoes it. */
@Composable
internal fun RenameMenuDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(stringResource(Res.string.menu_rename_title), style = Paper.type.dishName, color = colors.ink) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                colors = paperFieldColors(),
                shape = Shapes.button,
                placeholder = { Text(stringResource(Res.string.menu_rename_hint), color = colors.inkFaint) },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
            )
        },
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
 * How "Local specialities" are chosen, said plainly: MenuLango never knows where the diner is.
 * It reads the region from the menu's language, so these are dishes typical where that language
 * is spoken.
 */
@Composable
internal fun FeaturedReasonDialog(
    language: String?,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = {
            Text(stringResource(Res.string.menu_featured_reason_title), style = Paper.type.dishName, color = colors.ink)
        },
        text = {
            Text(
                language?.let { stringResource(Res.string.menu_featured_reason, it) }
                    ?: stringResource(Res.string.menu_featured_reason_unknown),
                style = Paper.type.body,
                color = colors.ink,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tip_got_it), color = colors.sealInk) }
        },
    )
}
