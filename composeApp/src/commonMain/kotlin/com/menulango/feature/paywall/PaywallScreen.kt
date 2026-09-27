package com.menulango.feature.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menulango.PaywallReason
import com.menulango.core.design.Paper
import com.menulango.core.design.PaperIcons
import com.menulango.core.design.Shapes
import com.menulango.core.design.Space
import com.menulango.core.result.AppError
import com.menulango.core.ui.ErrorMessage
import com.menulango.core.ui.IconAction
import com.menulango.core.ui.PrimaryButton
import com.menulango.core.ui.QuietButton
import com.menulango.core.ui.SectionLabel
import com.menulango.core.ui.StateMessage
import com.menulango.core.ui.felt
import com.menulango.core.ui.paper
import com.menulango.core.ui.paperShimmer
import com.menulango.data.billing.PlanKind
import com.menulango.data.billing.PlanOffer
import com.menulango.di.AppConfig
import com.menulango.platform.feedbackMailUri
import com.menulango.resources.Res
import com.menulango.resources.action_close
import com.menulango.resources.action_try_again
import com.menulango.resources.paywall_annual
import com.menulango.resources.paywall_annual_detail
import com.menulango.resources.paywall_benefit_history
import com.menulango.resources.paywall_benefit_modes
import com.menulango.resources.paywall_benefit_scans
import com.menulango.resources.paywall_cta
import com.menulango.resources.paywall_cta_trip
import com.menulango.resources.paywall_free_note
import com.menulango.resources.paywall_lifetime
import com.menulango.resources.paywall_lifetime_detail
import com.menulango.resources.paywall_monthly
import com.menulango.resources.paywall_monthly_detail
import com.menulango.resources.paywall_nothing_to_restore
import com.menulango.resources.paywall_not_allowed
import com.menulango.resources.paywall_not_from_store
import com.menulango.resources.paywall_offline
import com.menulango.resources.paywall_pending
import com.menulango.resources.paywall_plans_failed_body
import com.menulango.resources.paywall_plans_failed_title
import com.menulango.resources.paywall_privacy
import com.menulango.resources.paywall_reason_choose
import com.menulango.resources.paywall_reason_pages
import com.menulango.resources.paywall_reason_scans
import com.menulango.resources.paywall_report_issue
import com.menulango.resources.paywall_restore
import com.menulango.resources.paywall_store_error
import com.menulango.resources.paywall_subtitle
import com.menulango.resources.paywall_terms
import com.menulango.resources.paywall_title
import com.menulango.resources.paywall_trip_pass
import com.menulango.resources.paywall_trip_pass_detail
import com.menulango.resources.paywall_unavailable_body
import com.menulango.resources.paywall_unavailable_title
import com.menulango.resources.paywall_unlocked
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PaywallScreen(
    reason: PaywallReason,
    onClose: () -> Unit,
) {
    val viewModel = koinViewModel<PaywallViewModel> { parametersOf(reason) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = koinInject<AppConfig>()
    val uriHandler = LocalUriHandler.current

    val unlocked = (state as? PaywallUiState.Ready)?.unlocked == true
    LaunchedEffect(unlocked) {
        if (unlocked) {
            delay(UNLOCKED_PAUSE_MS)
            onClose()
        }
    }

    PaywallContent(
        state = state,
        actions =
            PaywallActions(
                onClose = onClose,
                onSelect = viewModel::select,
                onPurchase = viewModel::purchase,
                onRestore = viewModel::restore,
                onRetry = viewModel::retry,
                onTerms = { uriHandler.openUri(TERMS_URL) },
                onPrivacy = config.privacyPolicyUrl?.let { url -> { uriHandler.openUri(url) } },
            ),
    )
}

internal data class PaywallActions(
    val onClose: () -> Unit,
    val onSelect: (PlanOffer) -> Unit,
    val onPurchase: () -> Unit,
    val onRestore: () -> Unit,
    val onRetry: () -> Unit,
    val onTerms: () -> Unit,
    val onPrivacy: (() -> Unit)?,
) {
    companion object {
        val Preview = PaywallActions({}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Built by hand, on the same cream and white cards as the rest of the app. It shows what Plus gives, never what
 * the diner loses; the Trip Pass is selected because it fits how the app is used; and it says,
 * in one honest sentence, what stays free.
 */
@Composable
internal fun PaywallContent(
    state: PaywallUiState,
    actions: PaywallActions,
) {
    val colors = Paper.colors
    val type = Paper.type
    Column(
        Modifier
            .fillMaxSize()
            .felt(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        IconAction(
            PaperIcons.Close,
            stringResource(Res.string.action_close),
            actions.onClose,
            background = colors.raised,
            modifier = Modifier.padding(start = Space.md, top = Space.xs),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .widthIn(max = Space.readingWidth),
        ) {
            Spacer(Modifier.height(Space.md))
            state.reason.label()?.let { SectionLabel(stringResource(it), color = colors.sealInk) }
            Spacer(Modifier.height(Space.sm))
            Text(
                stringResource(
                    Res.string.paywall_title,
                ),
                style = type.hero,
                color = colors.ink,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Spacer(Modifier.height(Space.md))
            Text(stringResource(Res.string.paywall_subtitle), style = type.body, color = colors.inkMuted)
            Spacer(Modifier.height(Space.section))
            Benefits()
            Spacer(Modifier.height(Space.section))

            when (state) {
                is PaywallUiState.Loading -> {
                    PlanPlaceholders()
                }

                is PaywallUiState.Ready -> {
                    Plans(state, actions.onSelect)
                }

                is PaywallUiState.Empty -> {
                    StateMessage(
                        stringResource(Res.string.paywall_unavailable_title),
                        stringResource(Res.string.paywall_unavailable_body),
                        horizontalPadding = 0.dp,
                    )
                }

                is PaywallUiState.Failed -> {
                    if (state.error == AppError.Offline) {
                        ErrorMessage(state.error, horizontalPadding = 0.dp) {
                            PrimaryButton(
                                stringResource(Res.string.action_try_again),
                                actions.onRetry,
                                Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        // The photo has nothing to do with it: the store didn't hand over the plans.
                        // Say so plainly, and make reporting it one tap.
                        val uriHandler = LocalUriHandler.current
                        StateMessage(
                            stringResource(Res.string.paywall_plans_failed_title),
                            stringResource(Res.string.paywall_plans_failed_body),
                            horizontalPadding = 0.dp,
                        ) {
                            PrimaryButton(
                                stringResource(Res.string.action_try_again),
                                actions.onRetry,
                                Modifier.fillMaxWidth(),
                            )
                            QuietButton(
                                stringResource(Res.string.paywall_report_issue),
                                {
                                    uriHandler.openUri(
                                        feedbackMailUri("Plans didn't load on the paywall (${state.error})"),
                                    )
                                },
                                color = colors.sealInk,
                                singleLine = true,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            Text(stringResource(Res.string.paywall_free_note), style = type.caption, color = colors.inkMuted)
            Spacer(Modifier.height(Space.section))
        }

        Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
            val ready = state as? PaywallUiState.Ready
            ready?.message?.let { message ->
                Text(
                    stringResource(message.text()),
                    style = type.caption,
                    // Problems read as problems; "waiting for approval" is news, not an error.
                    color = if (message.isProblem) colors.sealInk else colors.inkMuted,
                    modifier =
                        Modifier
                            .padding(
                                bottom = Space.related,
                            ).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (ready?.unlocked == true) {
                Text(
                    stringResource(Res.string.paywall_unlocked),
                    style = type.title,
                    color = colors.sealInk,
                    modifier = Modifier.padding(vertical = Space.md).semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else if (ready != null) {
                PrimaryButton(
                    text =
                        stringResource(
                            if (ready.selected?.kind ==
                                PlanKind.TripPass
                            ) {
                                Res.string.paywall_cta_trip
                            } else {
                                Res.string.paywall_cta
                            },
                        ),
                    onClick = actions.onPurchase,
                    busy = ready.busy == Busy.Purchasing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // On a narrow phone a link that doesn't fit moves to the next line whole, never split
            // mid-word ("Pri / vacy").
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton(stringResource(Res.string.paywall_restore), actions.onRestore, singleLine = true)
                QuietButton(
                    stringResource(Res.string.paywall_terms),
                    actions.onTerms,
                    color = colors.inkFaint,
                    singleLine = true,
                )
                actions.onPrivacy?.let {
                    QuietButton(
                        stringResource(Res.string.paywall_privacy),
                        it,
                        color = colors.inkFaint,
                        singleLine = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun Benefits() {
    val colors = Paper.colors
    Column(
        Modifier
            .fillMaxWidth()
            .paper(colors.raised, Shapes.card)
            .padding(horizontal = Space.cardPadding, vertical = Space.sm),
    ) {
        listOf(
            Res.string.paywall_benefit_scans to "📸",
            Res.string.paywall_benefit_modes to "🍽️",
            Res.string.paywall_benefit_history to "📖",
        ).forEach { (benefit, emoji) ->
            Row(Modifier.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(colors.sealWash, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
                }
                Spacer(Modifier.width(Space.sm))
                Text(stringResource(benefit), style = Paper.type.bodySmall, color = colors.ink)
            }
        }
    }
}

@Composable
private fun Plans(
    state: PaywallUiState.Ready,
    onSelect: (PlanOffer) -> Unit,
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.related)) {
        state.offers.forEach { offer ->
            PlanOption(offer, isSelected = offer.id == state.selectedId, onClick = { onSelect(offer) })
        }
    }
}

@Composable
private fun PlanOption(
    offer: PlanOffer,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = Paper.colors
    val (title, detail) = offer.kind.copy()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(if (isSelected) colors.sealWash else colors.raised)
            .border(2.dp, if (isSelected) colors.seal else Color.Transparent, Shapes.card)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(Space.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(title),
                style = Paper.type.dishName,
                color = if (isSelected) colors.sealInk else colors.ink,
            )
            Text(stringResource(detail), style = Paper.type.caption, color = colors.inkMuted)
        }
        Spacer(Modifier.width(Space.md))
        Text(offer.price, style = Paper.type.price.copy(fontSize = Paper.type.title.fontSize), color = colors.ink)
    }
}

@Composable
private fun PlanPlaceholders() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.related)) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(Shapes.card)
                    .paperShimmer(),
            )
        }
    }
}

private fun PlanKind.copy(): Pair<StringResource, StringResource> =
    when (this) {
        PlanKind.TripPass -> Res.string.paywall_trip_pass to Res.string.paywall_trip_pass_detail
        PlanKind.Monthly -> Res.string.paywall_monthly to Res.string.paywall_monthly_detail
        PlanKind.Annual -> Res.string.paywall_annual to Res.string.paywall_annual_detail
        PlanKind.Lifetime -> Res.string.paywall_lifetime to Res.string.paywall_lifetime_detail
    }

private fun PaywallReason.label(): StringResource? =
    when (this) {
        PaywallReason.OutOfScans -> Res.string.paywall_reason_scans
        PaywallReason.Choosing -> Res.string.paywall_reason_choose
        PaywallReason.MorePages -> Res.string.paywall_reason_pages
        PaywallReason.Upgrade -> null
    }

private fun PaywallMessage.text(): StringResource =
    when (this) {
        PaywallMessage.Pending -> Res.string.paywall_pending
        PaywallMessage.NothingToRestore -> Res.string.paywall_nothing_to_restore
        PaywallMessage.Offline -> Res.string.paywall_offline
        PaywallMessage.StoreError -> Res.string.paywall_store_error
        PaywallMessage.NotAllowed -> Res.string.paywall_not_allowed
        PaywallMessage.NotInstalledFromStore -> Res.string.paywall_not_from_store
    }

/** Apple's standard licence agreement; the subscription terms both stores already show at purchase. */
internal const val TERMS_URL = "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"

/** Long enough to read "Welcome to Plus", short enough not to feel like a delay. */
private const val UNLOCKED_PAUSE_MS = 900L
