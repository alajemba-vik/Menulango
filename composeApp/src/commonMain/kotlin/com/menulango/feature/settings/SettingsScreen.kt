package com.menulango.feature.settings

import com.menulango.core.design.Motion
import com.menulango.feature.menu.FILTER_PILL_HEIGHT
import com.menulango.core.ui.FIELD_HEIGHT
import com.menulango.core.ui.flyInFrom
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.MenuSource
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.core.design.Elevation
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import androidx.compose.runtime.key
import com.menulango.core.ui.PillField
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.RestoreButton
import com.menulango.core.ui.ScrollTitleBar
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.SegmentedControl
import com.menulango.core.ui.bigTitleFade
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.paperFieldColors
import com.menulango.core.ui.pressable
import com.menulango.core.ui.rememberKept
import com.menulango.core.ui.tipTarget
import com.menulango.data.preferences.AppLanguage
import com.menulango.data.preferences.Appearance
import com.menulango.data.preferences.StartPage
import com.menulango.data.tips.Tip
import com.menulango.data.tips.Tips
import com.menulango.di.AppConfig
import com.menulango.feature.menu.DietaryFilters
import com.menulango.feature.menu.FilterPill
import com.menulango.feature.menu.label
import com.menulango.feature.paywall.TERMS_URL
import com.menulango.platform.feedbackMailUri
import com.menulango.platform.rememberBackupFileTransfer
import com.menulango.platform.subscriptionSettingsUrl
import com.menulango.feature.menu.currencyPrefix
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
import com.menulango.resources.settings_backup
import com.menulango.resources.settings_backup_body
import com.menulango.resources.settings_backup_confirm
import com.menulango.resources.settings_backup_failed
import com.menulango.resources.settings_backup_not_opened
import com.menulango.resources.settings_backup_passphrase
import com.menulango.resources.settings_backup_passphrase_hint
import com.menulango.resources.settings_backup_restored
import com.menulango.resources.settings_backup_title
import com.menulango.resources.settings_backup_unsupported
import com.menulango.resources.settings_backup_wrong_passphrase
import com.menulango.resources.settings_calm_motion
import com.menulango.resources.settings_convert_prices
import com.menulango.resources.settings_convert_prices_body
import com.menulango.resources.settings_home_currency
import com.menulango.resources.settings_rates_credit
import com.menulango.resources.settings_calm_motion_body
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
import com.menulango.resources.settings_language
import com.menulango.resources.settings_language_body
import com.menulango.resources.settings_menu_languages
import com.menulango.resources.settings_language_row
import com.menulango.resources.settings_language_system
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
import com.menulango.resources.settings_restore_backup
import com.menulango.resources.settings_restore_backup_title
import com.menulango.resources.settings_restore_confirm
import com.menulango.resources.settings_restore_failed
import com.menulango.resources.settings_restored
import com.menulango.resources.settings_tips
import com.menulango.resources.settings_tips_body
import com.menulango.resources.settings_tips_off_body
import com.menulango.resources.settings_something_wrong
import com.menulango.resources.settings_start_camera
import com.menulango.resources.settings_start_menus
import com.menulango.resources.settings_start_page
import com.menulango.resources.settings_tester
import com.menulango.resources.settings_tester_plus
import com.menulango.resources.settings_tester_plus_body
import com.menulango.resources.settings_tester_plus_paid
import com.menulango.resources.settings_tester_sample
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
    val backupFiles = rememberBackupFileTransfer()
    val scope = rememberCoroutineScope()
    val tipsReset = stringResource(Res.string.settings_tips_reset)
    val uriHandler = LocalUriHandler.current
    val colors = Paper.colors
    var confirmDelete by remember { mutableStateOf(false) }
    var backupDialog by remember { mutableStateOf<BackupDialog?>(null) }
    var importedBackup by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.message.collect { snackbar.showSnackbar(getString(it.text())) }
    }
    LaunchedEffect(viewModel, backupFiles) {
        viewModel.backupShare.collect { backupFiles.share(it.filename, it.contents) }
    }

    // Kept across a language change, so the diner stays by the language setting they just used.
    val scroll = rememberKept("settings-scroll") { ScrollState(0) }
    val titleGone = with(LocalDensity.current) { TITLE_SCROLL_AWAY.roundToPx() }
    val progress = { (scroll.value / titleGone.toFloat()).coerceIn(0f, 1f) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Plain, not felt: Settings is mostly words, so the page stays quiet behind them.
                .background(colors.paper)
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(horizontal = Space.gutter)
                .widthIn(max = Space.readingWidth),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = Paper.type.hero,
                color = colors.ink,
                modifier =
                    Modifier
                        .padding(
                            top = Space.xl,
                            bottom = Space.gutter,
                        ).bigTitleFade(progress)
                        .semantics { heading() },
            )

            PlusCard(
                isPlus = state.isPlus,
                onGetPlus = { navigate(Route.Paywall(PaywallReason.Upgrade)) },
                onManage = { uriHandler.openUri(subscriptionSettingsUrl) },
                perform = viewModel.performPlusCard,
            )
            // A text button has no fill to show its edges, so its padding would make the words
            // look indented: shifted left by that padding, they line up with the page's text.
            RestoreButton(
                stringResource(Res.string.settings_restore),
                viewModel::restore,
                color = colors.sealInk,
                modifier = Modifier.offset(x = -Space.sm),
            )

            if (viewModel.showsTestTools) {
                Section(stringResource(Res.string.settings_tester)) {
                    SettingsRow(stringResource(Res.string.settings_tester_sample)) {
                        navigate(Route.Menu(MenuSource.Sample))
                    }
                    // Bought for real, Plus can't be switched off here: say so, rather than a switch that ignores taps.
                    val paid by viewModel.paidPlus.collectAsStateWithLifecycle()
                    SwitchRow(
                        title = stringResource(Res.string.settings_tester_plus),
                        body =
                            stringResource(
                                if (paid) Res.string.settings_tester_plus_paid else Res.string.settings_tester_plus_body,
                            ),
                        checked = state.isPlus,
                        onChange = viewModel::setTestPlus,
                        enabled = !paid,
                        modifier = Modifier.tipTarget(Tip.TesterPlus),
                    )
                }
            }

            Section(stringResource(Res.string.settings_appearance)) {
                AppearancePicker(state.appearance, viewModel::setAppearance)
                GroupDivider()
                val startPage by viewModel.startPage.collectAsStateWithLifecycle()
                SubHeading(stringResource(Res.string.settings_start_page))
                SegmentedControl(
                    options =
                        listOf(
                            stringResource(Res.string.settings_start_menus),
                            stringResource(Res.string.settings_start_camera),
                        ),
                    selected = if (startPage == StartPage.Camera) 1 else 0,
                    onSelect = { viewModel.setStartPage(if (it == 1) StartPage.Camera else StartPage.Menus) },
                    modifier = Modifier.tipTarget(Tip.StartOnCameraHere),
                )
                GroupDivider()
                val calm by viewModel.calmMotion.collectAsStateWithLifecycle()
                SwitchRow(
                    title = stringResource(Res.string.settings_calm_motion),
                    body = stringResource(Res.string.settings_calm_motion_body),
                    checked = calm,
                    onChange = viewModel::setCalmMotion,
                )
            }

            Section(stringResource(Res.string.settings_language)) {
                val language by viewModel.language.collectAsStateWithLifecycle()
                var choosing by remember { mutableStateOf(false) }
                Text(
                    stringResource(Res.string.settings_language_body),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                )
                SettingsRow(
                    stringResource(Res.string.settings_language_row),
                    value = language.label(),
                ) { choosing = true }
                Text(
                    stringResource(Res.string.settings_menu_languages),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(top = Space.xs),
                )
                if (choosing) {
                    LanguageDialog(
                        selected = language,
                        onSelect = {
                            viewModel.setLanguage(it)
                            choosing = false
                        },
                        onDismiss = { choosing = false },
                    )
                }
            }

            // Everything that shapes how menus are read, in one group, iOS grouped-settings style.
            Section(stringResource(Res.string.settings_menus)) {
                SubHeading(stringResource(Res.string.settings_dietary))
                Text(
                    stringResource(Res.string.settings_dietary_body),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                )
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
                GroupDivider()
                SubHeading(stringResource(Res.string.settings_avoid))
                AvoidWords(state.avoid, viewModel::addAvoid, viewModel::removeAvoid)
                GroupDivider()
                SwitchRow(
                    title = stringResource(Res.string.settings_featured),
                    body = stringResource(Res.string.settings_featured_body),
                    checked = state.showFeatured,
                    onChange = viewModel::setShowFeatured,
                )
                GroupDivider()
                val convert by viewModel.convertPrices.collectAsStateWithLifecycle()
                val home by viewModel.homeCurrency.collectAsStateWithLifecycle()
                var choosingCurrency by remember { mutableStateOf(false) }
                SwitchRow(
                    title = stringResource(Res.string.settings_convert_prices),
                    body = stringResource(Res.string.settings_convert_prices_body),
                    checked = convert,
                    onChange = viewModel::setConvertPrices,
                )
                if (convert) {
                    SettingsRow(stringResource(Res.string.settings_home_currency), value = home) { choosingCurrency = true }
                    Text(
                        stringResource(Res.string.settings_rates_credit),
                        style = Paper.type.caption,
                        color = colors.inkMuted,
                    )
                }
                if (choosingCurrency) {
                    CurrencyDialog(
                        choices = viewModel.currencyChoices(),
                        selected = home,
                        onSelect = {
                            viewModel.setHomeCurrency(it)
                            choosingCurrency = false
                        },
                        onDismiss = { choosingCurrency = false },
                    )
                }
            }

            Section(stringResource(Res.string.settings_data)) {
                Text(
                    stringResource(Res.string.settings_backup_body),
                    style = Paper.type.caption,
                    color = colors.inkMuted,
                )
                SettingsRow(stringResource(Res.string.settings_backup)) {
                    backupDialog = BackupDialog.Export
                }
                SettingsRow(stringResource(Res.string.settings_restore_backup)) {
                    backupFiles.pick { contents ->
                        if (contents == null) {
                            viewModel.backupFileWasNotOpened()
                        } else {
                            importedBackup = contents
                            backupDialog = BackupDialog.Restore
                        }
                    }
                }
                SettingsRow(stringResource(Res.string.settings_delete_menus), colors.alarm) { confirmDelete = true }
            }

            Section(stringResource(Res.string.settings_about)) {
                SettingsRow(stringResource(Res.string.settings_something_wrong)) {
                    uriHandler.openUri(feedbackMailUri())
                }
                config.privacyPolicyUrl?.let { url ->
                    SettingsRow(stringResource(Res.string.paywall_privacy)) { uriHandler.openUri(url) }
                }
                SettingsRow(stringResource(Res.string.paywall_terms)) { uriHandler.openUri(TERMS_URL) }
                // On brings every note back from the start; off stops them all.
                // On while tips are still to come; off once turned off or once every tip has been
                // shown, so switching it on always means "show them to me again".
                val tipsOn by tips.enabled.collectAsStateWithLifecycle()
                val seenTips by tips.seen.collectAsStateWithLifecycle()
                val tipsComing = tipsOn && tips.notesLeft(seenTips, viewModel.showsTestTools)
                SwitchRow(
                    title = stringResource(Res.string.settings_tips),
                    body = stringResource(if (tipsComing) Res.string.settings_tips_body else Res.string.settings_tips_off_body),
                    checked = tipsComing,
                    onChange = { on ->
                        if (on) {
                            tips.reset()
                            scope.launch { snackbar.showSnackbar(tipsReset) }
                        } else {
                            tips.setEnabled(false)
                        }
                    },
                )
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

        backupDialog?.let { action ->
            BackupPassphraseDialog(
                action = action,
                onDismiss = {
                    backupDialog = null
                    importedBackup = null
                },
                onConfirm = { passphrase ->
                    when (action) {
                        BackupDialog.Export -> viewModel.createBackup(passphrase)
                        BackupDialog.Restore -> importedBackup?.let { viewModel.restoreBackup(it, passphrase) }
                    }
                    backupDialog = null
                    importedBackup = null
                },
            )
        }
        ScrollTitleBar(
            stringResource(Res.string.settings_title),
            progress,
            Modifier.align(Alignment.TopCenter),
            felt = false,
        )
    }
}

private fun SettingsMessage.text(): StringResource =
    when (this) {
        SettingsMessage.Restored -> Res.string.settings_restored
        SettingsMessage.NothingToRestore -> Res.string.settings_nothing_to_restore
        SettingsMessage.RestoreFailed -> Res.string.settings_restore_failed
        SettingsMessage.MenusDeleted -> Res.string.settings_delete_menus_done
        SettingsMessage.BackupFailed -> Res.string.settings_backup_failed
        SettingsMessage.BackupRestored -> Res.string.settings_backup_restored
        SettingsMessage.BackupWrongPassphrase -> Res.string.settings_backup_wrong_passphrase
        SettingsMessage.BackupUnsupportedVersion -> Res.string.settings_backup_unsupported
        SettingsMessage.BackupFileNotOpened -> Res.string.settings_backup_not_opened
    }

private enum class BackupDialog { Export, Restore }

@Composable
private fun BackupPassphraseDialog(
    action: BackupDialog,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = Paper.colors
    var passphrase by remember { mutableStateOf("") }
    val isExport = action == BackupDialog.Export
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = {
            Text(
                stringResource(
                    if (isExport) Res.string.settings_backup_title else Res.string.settings_restore_backup_title,
                ),
                style = Paper.type.dishName,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    stringResource(Res.string.settings_backup_passphrase_hint),
                    style = Paper.type.bodySmall,
                    color = colors.inkMuted,
                )
                OutlinedTextField(
                    colors = paperFieldColors(),
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_backup_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = passphrase.isNotBlank()) {
                Text(
                    stringResource(
                        if (isExport) Res.string.settings_backup_confirm else Res.string.settings_restore_confirm,
                    ),
                    color = colors.sealInk,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel), color = colors.ink) }
        },
    )
}

/**
 * The subscription as a stitched label: charcoal felt with an aubergine running stitch just inside the
 * edge, like the woven label in a good coat. No emoji, no sparkle — the material does the work.
 */
@Composable
private fun PlusCard(
    isPlus: Boolean,
    onGetPlus: () -> Unit,
    onManage: () -> Unit,
    perform: Boolean,
) {
    val colors = Paper.colors
    val stitch = colors.sealOnInk.copy(alpha = 0.7f)
    val ink = rememberPlusCardInk(circleTarget = !isPlus, perform = perform)
    var cardAt by remember { mutableStateOf(Offset.Zero) }
    var titleAt by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardAt = it.positionInRoot() }
            .felt(colors.ink, Shapes.card)
            .plusCardInk(ink, pen = colors.sealOnInk, titleOrigin = { titleAt - cardAt })
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
            color = colors.sealOnInk,
        )
        // Written out by hand each time: an invitation, not a banner.
        HandwrittenText(
            stringResource(if (isPlus) Res.string.settings_plus_active_title else Res.string.settings_plus_title),
            style = Paper.type.hand,
            color = colors.paper,
            nib = colors.sealOnInk,
            ink = ink,
            modifier = Modifier.onGloballyPositioned { titleAt = it.positionInRoot() },
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
                        tint = colors.sealOnInk,
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
            // Just the words: the pen's loop is the button. The press squish stays on the text,
            // while the tap area keeps the 48dp minimum.
            Box(Modifier.fillMaxWidth().padding(top = Space.sm), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.settings_plus_cta),
                    style = Paper.type.button.copy(fontSize = Paper.type.title.fontSize),
                    color = colors.paper,
                    modifier =
                        Modifier
                            .onGloballyPositioned { ink.button = it.boundsInRoot().translate(-cardAt) }
                            .pressable(onGetPlus)
                            .defaultMinSize(minHeight = Space.touchTarget)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = Space.md),
                )
            }
        }
    }
}

/** "System" in the current language, otherwise each language in its own name. */
@Composable
private fun AppLanguage.label(): String =
    if (this == AppLanguage.System) stringResource(Res.string.settings_language_system) else nativeName

/**
 * The languages as a list of radio rows in a dialog, as both iOS and Android present a language
 * choice: every name written in its own language, so anyone can find theirs.
 */
@Composable
private fun LanguageDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(stringResource(Res.string.settings_language), style = Paper.type.dishName, color = colors.ink) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget)
                            .clip(Shapes.chip)
                            .selectable(
                                selected = language == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(language) },
                            ).padding(horizontal = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            language.label(),
                            style = Paper.type.body,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (language == selected) {
                            Icon(
                                PaperIcons.Check,
                                contentDescription = null,
                                tint = colors.sealInk,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel), color = colors.ink) }
        },
    )
}

@Composable
private fun CurrencyDialog(
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Paper.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = {
            Text(stringResource(Res.string.settings_home_currency), style = Paper.type.dishName, color = colors.ink)
        },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                choices.forEach { code ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget)
                            .clip(Shapes.chip)
                            .selectable(
                                selected = code == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(code) },
                            ).padding(horizontal = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(code, style = Paper.type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        val symbol = currencyPrefix(code).trim()
                        if (symbol != code) {
                            Text(symbol, style = Paper.type.body, color = colors.inkMuted, modifier = Modifier.padding(end = Space.sm))
                        }
                        if (code == selected) {
                            Icon(
                                PaperIcons.Check,
                                contentDescription = null,
                                tint = colors.sealInk,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel), color = colors.ink) }
        },
    )
}

/** A small heading inside a settings group, above the control it names. */
@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        style = Paper.type.body.copy(fontWeight = FontWeight.SemiBold),
        color = Paper.colors.ink,
        modifier = Modifier.padding(bottom = Space.xs).semantics { heading() },
    )
}

/** The hairline between rows of one group, inset like an iOS grouped list. */
@Composable
private fun GroupDivider() {
    Box(
        Modifier
            .padding(vertical = Space.md)
            .fillMaxWidth()
            .height(Space.hairline)
            .background(Paper.colors.rule),
    )
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
    value: String? = null,
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
        // The current choice beside the chevron, as iOS Settings shows it.
        value?.let {
            Text(
                it,
                style = Paper.type.body,
                color = Paper.colors.inkMuted,
                modifier = Modifier.padding(end = Space.xs),
            )
        }
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
    val density = LocalDensity.current
    var draft by remember { mutableStateOf("") }
    // Where the typed word sits in the field, and which word was just added: that chip starts
    // there and drops into its place, as if the word fell out of the field onto the list.
    var fieldText by remember { mutableStateOf(Offset.Zero) }
    var landing by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val add = {
        val word = draft.trim().lowercase()
        // A word already listed stays put; only a new one drops in from the field.
        if (word !in words) landing = word to fieldText
        onAdd(draft)
        draft = ""
    }
    Text(
        stringResource(Res.string.settings_avoid_body),
        style = Paper.type.caption,
        color = colors.inkMuted,
    )
    Spacer(Modifier.height(Space.sm))
    Column(Modifier.animateContentSize(tween(Motion.SHEET_MS, easing = Motion.standard))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The same capsule as search, so every field in the app looks alike.
            PillField(
                value = draft,
                onValueChange = { draft = it.take(30) },
                placeholder = stringResource(Res.string.settings_avoid_hint),
                onDone = { add() },
                modifier =
                    Modifier.weight(1f).onGloballyPositioned {
                        // The text's start: inside the capsule's padding, centred like a chip.
                        fieldText =
                            it.positionInRoot() +
                            with(density) { Offset(Space.md.toPx(), ((FIELD_HEIGHT - FILTER_PILL_HEIGHT) / 2).toPx()) }
                    },
            )
            Spacer(Modifier.width(Space.related))
            IconAction(
                PaperIcons.Plus,
                stringResource(Res.string.settings_avoid_add),
                add,
                tint = colors.onSeal,
                background = colors.seal,
                // Nothing to add until a word is typed.
                enabled = draft.isNotBlank(),
            )
        }
        if (words.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.related),
                verticalArrangement = Arrangement.spacedBy(Space.related),
            ) {
                words.sorted().forEach { word ->
                    key(word) {
                        val from = landing?.takeIf { it.first == word }?.second
                        Box(Modifier.flyInFrom(from)) {
                            FilterPill(
                                text = stringResource(Res.string.filter_avoid, word),
                                selected = true,
                                onClick = { onRemove(word) },
                            )
                        }
                    }
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
}

/** System, Light or Dark as one segmented control, the selected segment lifted like a card. */
@Composable
private fun AppearancePicker(
    selected: Appearance,
    onSelect: (Appearance) -> Unit,
) {
    val options = Appearance.entries
    SegmentedControl(
        options = options.map { stringResource(it.label()) },
        selected = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
    )
}

/** A setting that is on or off: the whole row toggles, and reads as one switch to a screen reader. */
@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Paper.colors
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Paper.type.body, color = colors.ink)
            Text(body, style = Paper.type.caption, color = colors.inkMuted)
        }
        Spacer(Modifier.width(Space.sm))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = colors.seal,
                    checkedThumbColor = colors.onSeal,
                    uncheckedTrackColor = colors.sunk,
                    uncheckedThumbColor = colors.inkFaint,
                    uncheckedBorderColor = colors.outline,
                ),
        )
    }
}

private fun Appearance.label(): StringResource =
    when (this) {
        Appearance.System -> Res.string.settings_appearance_system
        Appearance.Light -> Res.string.settings_appearance_light
        Appearance.Dark -> Res.string.settings_appearance_dark
    }

/** How far the big title scrolls before the small one appears in the bar. */
private val TITLE_SCROLL_AWAY = 72.dp
