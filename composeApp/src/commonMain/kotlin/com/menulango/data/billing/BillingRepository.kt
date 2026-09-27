package com.menulango.data.billing

import com.menulango.core.result.AppError
import com.menulango.core.result.AppResult
import com.menulango.di.AppConfig
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.DiscountPaymentMode
import com.revenuecat.purchases.kmp.models.Period
import com.revenuecat.purchases.kmp.models.PeriodUnit
import com.revenuecat.purchases.kmp.models.freePhase
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The single seam between MenuLango and RevenueCat.
 *
 * Exists so that swapping billing providers touches exactly one file: nothing else in the app
 * imports the SDK. Features gate on [isPlus]; screens buy through [purchase] and [restore].
 *
 * When the build has no RevenueCat key (a stranger's clone, CI) the repository reports "not
 * Plus" and offers nothing, and the paywall explains that purchases are unavailable in this build.
 */
internal class BillingRepository(
    private val config: AppConfig,
    appScope: CoroutineScope,
) {
    private val entitled = MutableStateFlow(false)
    private val debugUnlock = MutableStateFlow(false)
    private val packagesById = mutableMapOf<String, Package>()

    /** True while the diner holds the "menulango_pro" entitlement. The only thing features should read. */
    val isPlus: StateFlow<Boolean> =
        combine(entitled, debugUnlock) { real, debug -> real || debug }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    /** Configures the SDK once, at app start, and learns the current entitlement from its cache. */
    suspend fun start() {
        if (!config.hasBilling || Purchases.isConfigured) return
        Purchases.logLevel = if (config.isDebug) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(PurchasesConfiguration(apiKey = config.revenueCatApiKey))
        Purchases.sharedInstance.delegate =
            object : PurchasesDelegate {
                override fun onPurchasePromoProduct(
                    product: StoreProduct,
                    startPurchase: (
                        onError: (PurchasesError, Boolean) -> Unit,
                        onSuccess: (StoreTransaction, CustomerInfo) -> Unit,
                    ) -> Unit,
                ) {
                    // App Store promoted purchases: let them go ahead and update the entitlement.
                    startPurchase({ _, _ -> }, { _, info -> update(info) })
                }

                override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) = update(customerInfo)
            }
        try {
            update(Purchases.sharedInstance.awaitCustomerInfo())
        } catch (e: PurchasesException) {
            // Offline on first launch: the delegate delivers the entitlement once the SDK reaches the network.
            log("customer info unavailable: ${e.error.code}")
        }
    }

    suspend fun offers(): AppResult<PlanCatalog> {
        if (!config.hasBilling) return AppResult.Ok(PlanCatalog(emptyList()))
        return try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            // The current offering first: that is the one RevenueCat's Experiments and Targeting
            // choose, so prices and plans can be tested and changed from the dashboard alone.
            val offering = offerings.current ?: offerings.all[OFFERING_ID]
            val packages = offering?.availablePackages.orEmpty()
            packagesById.clear()
            val offers =
                packages
                    .mapNotNull { pkg ->
                        val kind = pkg.packageType.toPlanKind() ?: return@mapNotNull null
                        packagesById[pkg.identifier] = pkg
                        PlanOffer(
                            id = pkg.identifier,
                            kind = kind,
                            price = pkg.storeProduct.price.formatted,
                            freeTrialDays = pkg.storeProduct.freeTrialDays(),
                        )
                    }.sortedBy { it.kind.ordinal }
            AppResult.Ok(
                PlanCatalog(
                    offers = offers,
                    highlight = offering?.metadata?.get("highlight")?.toString()?.toPlanKind(),
                    headlines = offering?.metadata?.get("headline").toHeadlines(),
                ),
            )
        } catch (e: PurchasesException) {
            when {
                e.error.code.isOffline() -> AppResult.Err(AppError.Offline)
                // No store on this device (or purchases blocked): retrying can't help, so the
                // paywall says purchases aren't available here instead of "try again".
                e.error.code == PurchasesErrorCode.PurchaseNotAllowedError -> AppResult.Ok(PlanCatalog(emptyList()))
                else -> AppResult.Err(AppError.Upstream)
            }
        }
    }

    suspend fun purchase(offer: PlanOffer): PurchaseOutcome {
        val pkg = packagesById[offer.id] ?: return PurchaseOutcome.Failed(BillingFailure.StoreUnavailable)
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(pkg)
            update(result.customerInfo)
            if (entitled.value) PurchaseOutcome.Unlocked else PurchaseOutcome.Pending
        } catch (e: PurchasesTransactionException) {
            when {
                e.userCancelled -> PurchaseOutcome.Cancelled
                e.error.code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
                e.error.code == PurchasesErrorCode.ProductAlreadyPurchasedError -> restore()
                else -> PurchaseOutcome.Failed(e.error.code.toFailure())
            }
        }
    }

    suspend fun restore(): PurchaseOutcome {
        if (!config.hasBilling) return PurchaseOutcome.Failed(BillingFailure.StoreUnavailable)
        return try {
            update(Purchases.sharedInstance.awaitRestore())
            if (entitled.value) PurchaseOutcome.Unlocked else PurchaseOutcome.NothingToRestore
        } catch (e: PurchasesException) {
            PurchaseOutcome.Failed(e.error.code.toFailure())
        }
    }

    /** Debug builds only: unlock Plus without a purchase, to work on the choosing screens. */
    fun setDebugUnlock(enabled: Boolean) {
        if (config.showsTestTools) debugUnlock.value = enabled
    }

    private fun update(info: CustomerInfo) {
        entitled.value = info.entitlements.active.containsKey(ENTITLEMENT_ID)
    }

    private fun log(message: String) {
        if (config.isDebug) println("MenuLango billing: $message")
    }

    private companion object {
        const val ENTITLEMENT_ID = "menulango_pro"
        const val OFFERING_ID = "default"
    }
}

/** "trip_pass", "weekly", "monthly", "annual" or "lifetime", in any case, as the dashboard spells it. */
private fun String.toPlanKind(): PlanKind? =
    when (trim().lowercase().replace('-', '_')) {
        "trip_pass", "trippass", "weekly", "week" -> PlanKind.TripPass
        "monthly", "month" -> PlanKind.Monthly
        "annual", "yearly", "year" -> PlanKind.Annual
        "lifetime" -> PlanKind.Lifetime
        else -> null
    }

/** One headline for everyone, or a map of language tag to headline. Anything else is ignored. */
private fun Any?.toHeadlines(): Map<String, String> =
    when (this) {
        is String -> if (isBlank()) emptyMap() else mapOf(PlanCatalog.ANY_LANGUAGE to trim())
        is Map<*, *> ->
            entries
                .mapNotNull { (key, value) ->
                    val text = (value as? String)?.trim()
                    if (key is String && !text.isNullOrEmpty()) key.lowercase() to text else null
                }.toMap()
        else -> emptyMap()
    }

private fun PackageType.toPlanKind(): PlanKind? =
    when (this) {
        PackageType.WEEKLY -> PlanKind.TripPass
        PackageType.MONTHLY -> PlanKind.Monthly
        PackageType.ANNUAL -> PlanKind.Annual
        PackageType.LIFETIME -> PlanKind.Lifetime
        else -> null
    }

/**
 * The free trial in days, from whichever store sold it: Apple's introductory offer, or Google's
 * free phase on the default offer. Null when there is none.
 */
private fun StoreProduct.freeTrialDays(): Int? {
    introductoryDiscount
        ?.takeIf { it.paymentMode == DiscountPaymentMode.FREE_TRIAL }
        ?.let { return it.subscriptionPeriod.inDays() * it.numberOfPeriods.toInt() }
    return defaultOption?.freePhase?.let { it.billingPeriod.inDays() * (it.billingCycleCount ?: 1) }
}

private fun Period.inDays(): Int =
    when (unit) {
        PeriodUnit.DAY -> value
        PeriodUnit.WEEK -> value * 7
        PeriodUnit.MONTH -> value * 30
        PeriodUnit.YEAR -> value * 365
        PeriodUnit.UNKNOWN -> 0
    }

private fun PurchasesErrorCode.isOffline(): Boolean =
    this == PurchasesErrorCode.NetworkError || this == PurchasesErrorCode.OfflineConnectionError

private fun PurchasesErrorCode.toFailure(): BillingFailure =
    when {
        isOffline() -> BillingFailure.Offline

        this == PurchasesErrorCode.PurchaseNotAllowedError ||
            this == PurchasesErrorCode.ProductNotAvailableForPurchaseError -> BillingFailure.NotAllowed

        this == PurchasesErrorCode.StoreProblemError -> BillingFailure.StoreUnavailable

        // Google's DEVELOPER_ERROR ("ensure the app is signed correctly") arrives as this: the
        // copy on the phone isn't one the store distributed, so no retry will ever work.
        this == PurchasesErrorCode.PurchaseInvalidError -> BillingFailure.NotInstalledFromStore

        else -> BillingFailure.Unknown
    }
