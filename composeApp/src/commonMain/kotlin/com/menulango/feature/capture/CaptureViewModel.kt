package com.menulango.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.MenuSource
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.data.billing.BillingRepository
import com.menulango.data.menu.MenuRepository
import com.menulango.data.menu.local.CachedMenuSummary
import com.menulango.data.quota.QuotaState
import com.menulango.data.quota.ScanQuota
import com.menulango.di.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the capture screen shows around the viewfinder. */
internal sealed interface CaptureUiState {
    data object Loading : CaptureUiState

    data class Ready(
        val allowance: Allowance,
        val lastMenu: CachedMenuSummary?,
        val isPreparingPhoto: Boolean,
        val photoProblem: Boolean,
        val debug: DebugTools?,
    ) : CaptureUiState
}

/** The honest counter under the shutter. */
internal sealed interface Allowance {
    data object Plus : Allowance

    data class Free(
        val quota: QuotaState,
    ) : Allowance
}

/** Only present in debug builds. */
internal data class DebugTools(
    val plusUnlocked: Boolean,
)

/**
 * Decides whether a scan may start, and says so before the photo is taken — never mid-scan.
 */
internal class CaptureViewModel(
    private val quota: ScanQuota,
    private val billing: BillingRepository,
    repository: MenuRepository,
    private val config: AppConfig,
) : ViewModel() {
    private val photoStatus = MutableStateFlow(PhotoStatus.Idle)

    val uiState: StateFlow<CaptureUiState> =
        combine(quota.quota, billing.isPlus, repository.latest(), photoStatus) { quota, isPlus, last, photo ->
            CaptureUiState.Ready(
                allowance = if (isPlus) Allowance.Plus else Allowance.Free(quota),
                lastMenu = last,
                isPreparingPhoto = photo == PhotoStatus.Preparing,
                photoProblem = photo == PhotoStatus.Unreadable,
                debug = if (config.isDebug) DebugTools(plusUnlocked = isPlus) else null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CaptureUiState.Loading)

    /** The month can turn while the app sits in the background. */
    fun onResume() {
        quota.refresh()
    }

    /** Where the shutter leads: the camera if a scan is available, otherwise the paywall. */
    fun gateForScan(): Route? =
        if (billing.isPlus.value || !quota.refresh().isExhausted) null else Route.Paywall(PaywallReason.OutOfScans)

    fun onPreparingPhoto() {
        photoStatus.value = PhotoStatus.Preparing
    }

    /** A null photo means the capture or the picked file failed. */
    fun onPhotoReady(jpeg: ByteArray?): Route? {
        photoStatus.value = if (jpeg == null) PhotoStatus.Unreadable else PhotoStatus.Idle
        return jpeg?.let { Route.Menu(MenuSource.Photo(it)) }
    }

    fun onPhotoCancelled() {
        photoStatus.value = PhotoStatus.Idle
    }

    fun toggleDebugPlus() {
        billing.setDebugUnlock(!billing.isPlus.value)
    }

    private enum class PhotoStatus { Idle, Preparing, Unreadable }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
