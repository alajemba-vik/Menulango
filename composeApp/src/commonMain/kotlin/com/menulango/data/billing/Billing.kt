package com.menulango.data.billing

/**
 * The plans MenuLango sells, in the order the paywall presents them.
 *
 * The week leads because this is a travel product: the need is intense and lasts six days.
 */
internal enum class PlanKind {
    TripPass,
    Monthly,
    Annual,
    Lifetime,
}

/** A plan the store can sell right now, with its localised price. */
internal data class PlanOffer(
    val id: String,
    val kind: PlanKind,
    val price: String,
    /** Days free before the first charge, when the store offers a free trial; else null. */
    val freeTrialDays: Int? = null,
)

/**
 * The plans on sale, plus what the RevenueCat dashboard says about showing them, read from the
 * offering's metadata so the paywall can be tuned without an app update:
 *
 * - `highlight`: the plan selected first: `trip_pass` (or `weekly`), `monthly`, `annual`, `lifetime`.
 * - `headline`: the paywall's title, either one string or one per language, `{"en": "...", "fr": "..."}`.
 *
 * Anything missing or unreadable falls back to the app's own choices.
 */
internal data class PlanCatalog(
    val offers: List<PlanOffer>,
    val highlight: PlanKind? = null,
    val headlines: Map<String, String> = emptyMap(),
) {
    /**
     * The headline for a language tag ("fr", "zh-Hans"), else the one given for every language.
     * Null when the dashboard has nothing for this language, so the app's own translated title
     * shows rather than an English one.
     */
    fun headlineFor(languageTag: String): String? {
        val language = languageTag.substringBefore('-').lowercase()
        return headlines[languageTag.lowercase()] ?: headlines[language] ?: headlines[ANY_LANGUAGE]
    }

    companion object {
        /** The key used when the dashboard gives one headline for every language. */
        const val ANY_LANGUAGE = "*"
    }
}

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

    /** The store won't sell on this device or account: no store, not signed in, purchases blocked. */
    NotAllowed,

    /** The store rejects this copy of the app itself: not installed from it, or signed differently. */
    NotInstalledFromStore,
    Unknown,
}
