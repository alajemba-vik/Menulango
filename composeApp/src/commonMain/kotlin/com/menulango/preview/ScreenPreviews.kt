package com.menulango.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.menulango.PaywallReason
import com.menulango.core.result.AppError
import com.menulango.data.billing.PlanKind
import com.menulango.data.billing.PlanOffer
import com.menulango.feature.choose.ChoiceMode
import com.menulango.feature.choose.ChooseActions
import com.menulango.feature.choose.ChooseContent
import com.menulango.feature.choose.ChooseUiState
import com.menulango.feature.dish.DishSheet
import com.menulango.feature.menu.MenuActions
import com.menulango.feature.menu.MenuContent
import com.menulango.feature.menu.MenuUiState
import com.menulango.feature.order.TableOrder
import com.menulango.feature.paywall.PaywallActions
import com.menulango.feature.paywall.PaywallContent
import com.menulango.feature.paywall.PaywallUiState

private val menuReady =
    MenuUiState.Ready(
        photo = null,
        meta = PreviewDishes.meta,
        dishes = PreviewDishes.all,
        isReading = false,
        notice = null,
        selectedDishId = null,
        isPlus = false,
        eatenKeys = emptySet(),
    )

private val paywallReady =
    PaywallUiState.Ready(
        reason = PaywallReason.Choosing,
        offers =
            listOf(
                PlanOffer("trip", PlanKind.TripPass, "€4,99"),
                PlanOffer("monthly", PlanKind.Monthly, "€9,99"),
                PlanOffer("annual", PlanKind.Annual, "€29,99"),
            ),
        selectedId = "trip",
        busy = null,
        message = null,
        unlocked = false,
    )

private val chooseReady =
    ChooseUiState.Ready(
        mode = ChoiceMode.OnlyHere,
        deck = listOf(PreviewDishes.kokoretsi, PreviewDishes.sfougato),
        openDish = null,
        isPlus = true,
        order = TableOrder(),
    )

@Preview
@Composable
private fun MenuReadyLight() = PaperPreview(false) { MenuContent(menuReady, MenuActions.Preview) }

@Preview
@Composable
private fun MenuReadyDark() = PaperPreview(true) { MenuContent(menuReady, MenuActions.Preview) }

@Preview
@Composable
private fun MenuReadingLight() =
    PaperPreview(false) {
        MenuContent(menuReady.copy(isReading = true), MenuActions.Preview)
    }

@Preview
@Composable
private fun MenuLoadingDark() = PaperPreview(true) { MenuContent(MenuUiState.Loading(null), MenuActions.Preview) }

@Preview
@Composable
private fun MenuEmptyLight() = PaperPreview(false) { MenuContent(MenuUiState.Empty(null), MenuActions.Preview) }

@Preview
@Composable
private fun MenuFailedDark() =
    PaperPreview(true) {
        MenuContent(MenuUiState.Failed(null, AppError.Offline), MenuActions.Preview)
    }

@Preview
@Composable
private fun DishSheetLight() = PaperPreview(false) { DishSheet(PreviewDishes.kokoretsi, onDismiss = {}) }

@Preview
@Composable
private fun DishSheetDark() = PaperPreview(true) { DishSheet(PreviewDishes.kokoretsi, onDismiss = {}) }

@Preview
@Composable
private fun DishSheetBestGuessLight() = PaperPreview(false) { DishSheet(PreviewDishes.sfougato, onDismiss = {}) }

@Preview
@Composable
private fun ChooseLight() = PaperPreview(false) { ChooseContent(chooseReady, ChooseActions.Preview) }

@Preview
@Composable
private fun ChooseDark() = PaperPreview(true) { ChooseContent(chooseReady, ChooseActions.Preview) }

@Preview
@Composable
private fun ChooseNoneFitsLight() =
    PaperPreview(false) {
        ChooseContent(chooseReady.copy(deck = emptyList()), ChooseActions.Preview)
    }

@Preview
@Composable
private fun PaywallLight() = PaperPreview(false) { PaywallContent(paywallReady, PaywallActions.Preview) }

@Preview
@Composable
private fun PaywallDark() = PaperPreview(true) { PaywallContent(paywallReady, PaywallActions.Preview) }

@Preview
@Composable
private fun PaywallLoadingLight() =
    PaperPreview(false) {
        PaywallContent(PaywallUiState.Loading(PaywallReason.OutOfScans), PaywallActions.Preview)
    }

@Preview
@Composable
private fun PaywallUnavailableDark() =
    PaperPreview(true) {
        PaywallContent(PaywallUiState.Empty(PaywallReason.Upgrade), PaywallActions.Preview)
    }
