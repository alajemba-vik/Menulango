package com.menulango.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.pressable
import com.menulango.data.preferences.Appearance
import com.menulango.data.tips.Tips
import com.menulango.di.AppConfig
import com.menulango.feature.menu.DietaryFilters
import com.menulango.feature.menu.FilterPill
import com.menulango.feature.menu.label
import com.menulango.feature.paywall.TERMS_URL
import com.menulango.platform.subscriptionSettingsUrl
import com.menulango.resources.Res
import com.menulango.resources.filter_avoid
import com.menulango.resources.paywall_privacy
import com.menulango.resources.paywall_terms
import com.menulango.resources.settings_about
import com.menulango.resources.settings_ai_note
import com.menulango.resources.settings_appearance
import com.menulango.resources.settings_appearance_dark
import com.menulango.resources.settings_appearance_light
import com.menulango.resources.settings_appearance_system
import com.menulango.resources.settings_avoid
import com.menulango.resources.settings_avoid_add
import com.menulango.resources.settings_avoid_body
import com.menulango.resources.settings_avoid_hint
import com.menulango.resources.settings_avoid_remove_hint
import com.menulango.resources.settings_cancel
import com.menulango.resources.settings_data
import com.menulango.resources.settings_delete_menus
import com.menulango.resources.settings_delete_menus_confirm_body
import com.menulango.resources.settings_delete_menus_confirm_title
import com.menulango.resources.settings_delete_menus_done
import com.menulango.resources.settings_dietary
import com.menulango.resources.settings_dietary_body
import com.menulango.resources.settings_featured
import com.menulango.resources.settings_featured_body
import com.menulango.resources.settings_menus
import com.menulango.resources.settings_nothing_to_restore
import com.menulango.resources.settings_plus_active_title
import com.menulango.resources.settings_plus_benefit_choose
import com.menulango.resources.settings_plus_benefit_memory
import com.menulango.resources.settings_plus_benefit_pages
import com.menulango.resources.settings_plus_cta
import com.menulango.resources.settings_plus_label
import com.menulango.resources.settings_plus_label_member
import com.menulango.resources.settings_plus_manage
import com.menulango.resources.settings_plus_title
import com.menulango.resources.settings_restore
import com.menulango.resources.settings_restore_failed
import com.menulango.resources.settings_restored
import com.menulango.resources.settings_show_tips
import com.menulango.resources.settings_tips_reset
import com.menulango.resources.settings_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Everything a diner may want to set once: how the app looks, what they never want suggested,
 * their subscription, their data, and the small print every store requires to be findable.
 */
@Composable
internal fun SettingsScreen(
    navigate: (Route) -> Unit,
    snackbar: SnackbarHostState,
    bottomInset: Dp,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = koinInject<AppConfig>()
    val tips = koinInject<Tips>()
    val scope = rememberCoroutineScope()
    val tipsReset = stringResource(Res.string.settings_tips_reset)
    val uriHandler = LocalUriHandler.current
    val colors = Paper.colors
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.message.collect { snackbar.showSnackbar(getString(it.text())) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .felt(colors.paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Space.gutter)
            .widthIn(max = Space.readingWidth),
    ) {
        Text(
            stringResource(Res.string.settings_title),
            style = Paper.type.hero,
            color = colors.ink,
            modifier = Modifier.padding(top = Space.xl, bottom = Space.gutter).semantics { heading() },
        )

        PlusCard(
            isPlus = state.isPlus,
            onGetPlus = { navigate(Route.Paywall(PaywallReason.Upgrade)) },
            onManage = { uriHandler.openUri(subscriptionSettingsUrl) },
        )
        if (!state.isPlus) {
            QuietButton(stringResource(Res.string.settings_restore), viewModel::restore, color = colors.sealInk)
        }

        Section(stringResource(Res.string.settings_appearance)) {
            AppearancePicker(state.appearance, viewModel::setAppearance)
        }

        Section(stringResource(Res.string.settings_dietary)) {
            Text(stringResource(Res.string.settings_dietary_body), style = Paper.type.caption, color = colors.inkMuted)
            Spacer(Modifier.height(Space.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.related),
                verticalArrangement = Arrangement.spacedBy(Space.related),
            ) {
                DietaryFilters.forEach { filter ->
                    FilterPill(
                        text = filter.label(),
                        selected = filter in state.dietary,
                        onClick = { viewModel.toggleDietary(filter) },
                        unselected = colors.sunk,
                    )
                }
            }
        }

        Section(stringResource(Res.string.settings_avoid)) {
            AvoidWords(state.avoid, viewModel::addAvoid, viewModel::removeAvoid)
        }

        Section(stringResource(Res.string.settings_menus)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.showFeatured,
                        role = Role.Switch,
                        onValueChange = viewModel::setShowFeatured,
                    ).padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.settings_featured), style = Paper.type.body, color = colors.ink)
                    Text(
                        stringResource(Res.string.settings_featured_body),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Switch(
                    checked = state.showFeatured,
                    onCheckedChange = null,
                    colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = colors.seal,
                            checkedThumbColor = colors.onSeal,
                            uncheckedTrackColor = colors.sunk,
                            uncheckedThumbColor = colors.inkFaint,
                            uncheckedBorderColor = colors.rule,
                        ),
                )
            }
        }

        Section(stringResource(Res.string.settings_data)) {
            SettingsRow(stringResource(Res.string.settings_delete_menus), colors.alarm) { confirmDelete = true }
        }

        Section(stringResource(Res.string.settings_about)) {
            config.privacyPolicyUrl?.let { url ->
                SettingsRow(stringResource(Res.string.paywall_privacy)) { uriHandler.openUri(url) }
            }
            SettingsRow(stringResource(Res.string.paywall_terms)) { uriHandler.openUri(TERMS_URL) }
            SettingsRow(stringResource(Res.string.settings_show_tips)) {
                tips.reset()
                scope.launch { snackbar.showSnackbar(tipsReset) }
            }
            Text(
                stringResource(Res.string.settings_ai_note),
                style = Paper.type.caption,
                color = colors.inkMuted,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
        Spacer(Modifier.height(bottomInset))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.raised,
            title = {
                Text(
                    stringResource(Res.string.settings_delete_menus_confirm_title),
                    style = Paper.type.dishName,
                )
            },
            text = {
                Text(
                    stringResource(Res.string.settings_delete_menus_confirm_body),
                    style = Paper.type.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSavedMenus()
                }) { Text(stringResource(Res.string.settings_delete_menus), color = colors.alarm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(Res.string.settings_cancel), color = colors.ink)
                }
            },
        )
    }
}

private fun SettingsMessage.text(): StringResource =
    when (this) {
        SettingsMessage.Restored -> Res.string.settings_restored
        SettingsMessage.NothingToRestore -> Res.string.settings_nothing_to_restore
        SettingsMessage.RestoreFailed -> Res.string.settings_restore_failed
        SettingsMessage.MenusDeleted -> Res.string.settings_delete_menus_done
    }

/**
 * The subscription as a stitched label: charcoal felt with a coral running stitch just inside the
 * edge, like the woven label in a good coat. No emoji, no sparkle — the material does the work.
 */
@Composable
private fun PlusCard(
    isPlus: Boolean,
    onGetPlus: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = Paper.colors
    val stitch = colors.seal.copy(alpha = 0.7f)
    Column(
        Modifier
            .fillMaxWidth()
            .felt(colors.ink, Shapes.card)
            .drawBehind {
                val inset = 7.dp.toPx()
                drawRoundRect(
                    color = stitch,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style =
                        Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }.padding(horizontal = Space.cardPadding + Space.xs, vertical = Space.cardPadding + Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.related),
    ) {
        SectionLabel(
            stringResource(if (isPlus) Res.string.settings_plus_label_member else Res.string.settings_plus_label),
            color = colors.seal,
        )
        Text(
            stringResource(if (isPlus) Res.string.settings_plus_active_title else Res.string.settings_plus_title),
            style = Paper.type.headline,
            color = colors.paper,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
            listOf(
                Res.string.settings_plus_benefit_pages,
                Res.string.settings_plus_benefit_choose,
                Res.string.settings_plus_benefit_memory,
            ).forEach { benefit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        PaperIcons.Check,
                        contentDescription = null,
                        tint = colors.seal,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Space.related))
                    Text(
                        stringResource(benefit),
                        style = Paper.type.bodySmall,
                        color = colors.paper.copy(alpha = 0.85f),
                    )
                }
            }
        }
        if (isPlus) {
            QuietButton(stringResource(Res.string.settings_plus_manage), onManage, color = colors.paper)
        } else {
            PrimaryButton(
                stringResource(Res.string.settings_plus_cta),
                onGetPlus,
                Modifier.fillMaxWidth().padding(top = Space.sm),
            )
        }
    }
}

@Composable
private fun Section(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Spacer(Modifier.height(Space.section))
    SectionLabel(label, Modifier.padding(start = Space.xs, bottom = Space.related))
    Column(
        Modifier
            .fillMaxWidth()
            .paper(Paper.colors.raised, Shapes.card)
            .padding(Space.md),
        content = content,
    )
}

@Composable
private fun SettingsRow(
    text: String,
    color: Color = Paper.colors.ink,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(Shapes.tile)
            .pressable(onClick)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Paper.type.body, color = color, modifier = Modifier.weight(1f))
        Icon(
            PaperIcons.ChevronRight,
            contentDescription = null,
            tint = Paper.colors.inkFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The diner's own "never show me" words: type one, add it, see it as a chip you can take away.
 * Honest about what it can do — it reads names and ingredients, it cannot promise a kitchen.
 */
@Composable
private fun AvoidWords(
    words: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = Paper.colors
    var draft by remember { mutableStateOf("") }
    val add = {
        onAdd(draft)
        draft = ""
    }
    Text(stringResource(Res.string.settings_avoid_body), style = Paper.type.caption, color = colors.inkMuted)
    Spacer(Modifier.height(Space.sm))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(30) },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.settings_avoid_hint), style = Paper.type.bodySmall) },
            shape = Shapes.button,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.related))
        IconAction(
            PaperIcons.Plus,
            stringResource(Res.string.settings_avoid_add),
            add,
            tint = colors.onSeal,
            background = colors.seal,
        )
    }
    if (words.isNotEmpty()) {
        Spacer(Modifier.height(Space.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.related),
            verticalArrangement = Arrangement.spacedBy(Space.related),
        ) {
            words.sorted().forEach { word ->
                FilterPill(
                    text = stringResource(Res.string.filter_avoid, word),
                    selected = true,
                    onClick = { onRemove(word) },
                )
            }
        }
        Text(
            stringResource(Res.string.settings_avoid_remove_hint),
            style = Paper.type.caption,
            color = colors.inkFaint,
            modifier = Modifier.padding(top = Space.related),
        )
    }
}

/** System, Light or Dark as one segmented control, the selected segment lifted like a card. */
@Composable
private fun AppearancePicker(
    selected: Appearance,
    onSelect: (Appearance) -> Unit,
) {
    val colors = Paper.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.button)
            .background(colors.sunk)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        Appearance.entries.forEach { option ->
            val isSelected = option == selected
            Text(
                stringResource(option.label()),
                style = Paper.type.button.copy(fontSize = Paper.type.bodySmall.fontSize),
                color = if (isSelected) colors.ink else colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .then(if (isSelected) Modifier.shadow(Elevation.resting, Shapes.chip) else Modifier)
                        .clip(Shapes.chip)
                        .background(if (isSelected) colors.raised else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(vertical = Space.sm),
            )
        }
    }
}

private fun Appearance.label(): StringResource =
    when (this) {
        Appearance.System -> Res.string.settings_appearance_system
        Appearance.Light -> Res.string.settings_appearance_light
        Appearance.Dark -> Res.string.settings_appearance_dark
    }
