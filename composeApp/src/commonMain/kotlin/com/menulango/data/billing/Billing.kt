package com.menulango.data.billing

/**
 * The plans MenuLango sells, in the order the paywall presents them.
 *
 * The week leads because this is a travel product: the need is intense and lasts six days.
 */
internal enum class PlanKind {
    TripPass,
    Annual,
    Lifetime,
}

/** A plan the store can sell right now, with its localised price. */
internal data class PlanOffer(
    val id: String,
    val kind: PlanKind,
    val price: String,
)

/** What happened when the diner tried to buy or restore. */
internal sealed interface PurchaseOutcome {
    data object Unlocked : PurchaseOutcome

    /** They backed out of the store sheet. Not an error; say nothing. */
    data object Cancelled : PurchaseOutcome

    /** Payment is awaiting approval (family sharing, slow card). Plus unlocks when it clears. */
    data object Pending : PurchaseOutcome

    /** Restore finished but found nothing to restore. */
    data object NothingToRestore : PurchaseOutcome

    data class Failed(
        val reason: BillingFailure,
    ) : PurchaseOutcome
}

internal enum class BillingFailure {
    Offline,
    StoreUnavailable,
    Unknown,
}
