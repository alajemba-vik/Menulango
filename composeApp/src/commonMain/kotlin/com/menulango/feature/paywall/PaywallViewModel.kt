package com.menulango.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.PaywallReason
import com.menulango.core.result.AppError
import com.menulango.core.result.AppResult
import com.menulango.data.billing.BillingFailure
import com.menulango.data.billing.BillingRepository
import com.menulango.data.billing.PlanKind
import com.menulango.data.billing.PlanOffer
import com.menulango.data.billing.PurchaseOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface PaywallUiState {
    val reason: PaywallReason

    data class Loading(
        override val reason: PaywallReason,
    ) : PaywallUiState

    data class Ready(
        override val reason: PaywallReason,
        val offers: List<PlanOffer>,
        val selectedId: String,
        val busy: Busy?,
        val message: PaywallMessage?,
        val unlocked: Boolean,
    ) : PaywallUiState {
        val selected: PlanOffer? get() = offers.firstOrNull { it.id == selectedId }
    }

    /** No plans to sell in this build (no RevenueCat key, or no products configured). */
    data class Empty(
        override val reason: PaywallReason,
    ) : PaywallUiState

    data class Failed(
        override val reason: PaywallReason,
        val error: AppError,
    ) : PaywallUiState
}

internal enum class Busy { Purchasing, Restoring }

internal enum class PaywallMessage(
    /** Shown in the error colour; the others are just news. */
    val isProblem: Boolean,
) {
    Pending(false),
    NothingToRestore(false),
    Offline(true),
    StoreError(true),
    NotAllowed(true),

    /** A copy the store didn't install (sideloaded, or signed differently) can't take payments. */
    NotInstalledFromStore(true),
}

/**
 * Sells Plus. The Trip Pass is selected by default — it is the product for how the app is used.
 */
internal class PaywallViewModel(
    private val reason: PaywallReason,
    private val billing: BillingRepository,
) : ViewModel() {
    private val state = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading(reason))
    val uiState: StateFlow<PaywallUiState> = state.asStateFlow()

    init {
        load()
        // A purchase that clears later (pending, family approval) closes the paywall on its own.
        viewModelScope.launch {
            billing.isPlus.collect { plus ->
                if (plus) state.update { (it as? PaywallUiState.Ready)?.copy(unlocked = true, busy = null) ?: it }
            }
        }
    }

    fun retry() = load()

    fun select(offer: PlanOffer) {
        state.update { (it as? PaywallUiState.Ready)?.copy(selectedId = offer.id, message = null) ?: it }
    }

    fun purchase() {
        val ready = state.value as? PaywallUiState.Ready ?: return
        val offer = ready.selected ?: return
        if (ready.busy != null) return
        state.value = ready.copy(busy = Busy.Purchasing, message = null)
        viewModelScope.launch { settle(billing.purchase(offer)) }
    }

    fun restore() {
        val ready = state.value as? PaywallUiState.Ready
        if (ready?.busy != null) return
        if (ready != null) state.value = ready.copy(busy = Busy.Restoring, message = null)
        viewModelScope.launch { settle(billing.restore()) }
    }

    private fun load() {
        state.value = PaywallUiState.Loading(reason)
        viewModelScope.launch {
            state.value =
                when (val result = billing.offers()) {
                    is AppResult.Err -> {
                        PaywallUiState.Failed(reason, result.error)
                    }

                    is AppResult.Ok -> {
                        if (result.value.isEmpty()) {
                            PaywallUiState.Empty(reason)
                        } else {
                            val hero = result.value.firstOrNull { it.kind == PlanKind.TripPass } ?: result.value.first()
                            PaywallUiState.Ready(
                                reason = reason,
                                offers = result.value,
                                selectedId = hero.id,
                                busy = null,
                                message = null,
                                unlocked = billing.isPlus.value,
                            )
                        }
                    }
                }
        }
    }

    private fun settle(outcome: PurchaseOutcome) {
        val message =
            when (outcome) {
                PurchaseOutcome.Unlocked, PurchaseOutcome.Cancelled -> {
                    null
                }

                PurchaseOutcome.Pending -> {
                    PaywallMessage.Pending
                }

                PurchaseOutcome.NothingToRestore -> {
                    PaywallMessage.NothingToRestore
                }

                is PurchaseOutcome.Failed -> {
                    when (outcome.reason) {
                        BillingFailure.Offline -> PaywallMessage.Offline
                        BillingFailure.NotAllowed -> PaywallMessage.NotAllowed
                        BillingFailure.NotInstalledFromStore -> PaywallMessage.NotInstalledFromStore
                        else -> PaywallMessage.StoreError
                    }
                }
            }
        state.update { current ->
            when (current) {
                is PaywallUiState.Ready -> {
                    current.copy(
                        busy = null,
                        message = message,
                        unlocked =
                            outcome == PurchaseOutcome.Unlocked || current.unlocked,
                    )
                }

                else -> {
                    current
                }
            }
        }
    }
}
