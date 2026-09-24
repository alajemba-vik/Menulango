package com.menulango.feature.menu

import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.MenuSource
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.core.result.AppError
import com.menulango.data.billing.BillingRepository
import com.menulango.data.history.EatenHistory
import com.menulango.data.menu.MenuRepository
import com.menulango.data.menu.ScanProgress
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.quota.ScanQuota
import com.menulango.feature.choose.ChoiceMode
import com.menulango.feature.choose.dishHistoryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The menu screen, in exactly one of four states. There is no "maybe loaded" field.
 */
internal sealed interface MenuUiState {
    /** The photo the diner took, shown dimmed behind everything. Null for the sample menu. */
    val photo: ByteArray?

    data class Loading(
        override val photo: ByteArray?,
    ) : MenuUiState

    data class Ready(
        override val photo: ByteArray?,
        val meta: MenuMeta,
        val dishes: List<Dish>,
        /** True while more dishes are still arriving from the stream. */
        val isReading: Boolean,
        val notice: MenuNotice?,
        val selectedDishId: String?,
        val isPlus: Boolean,
        val eatenKeys: Set<String>,
    ) : MenuUiState {
        val selectedDish: Dish? get() = dishes.firstOrNull { it.id == selectedDishId }
    }

    data class Empty(
        override val photo: ByteArray?,
    ) : MenuUiState

    data class Failed(
        override val photo: ByteArray?,
        val error: AppError,
    ) : MenuUiState
}

/** One quiet line of context under the dish list, when something is worth saying. */
internal enum class MenuNotice { Partial, Truncated, KnownMenu, LastFreeScan }

/**
 * Reads one menu — from a photo, the device cache or the bundled sample — and records the scan
 * against the free tier only once the diner has seen the result.
 */
internal class MenuViewModel(
    private val source: MenuSource,
    private val repository: MenuRepository,
    private val quota: ScanQuota,
    private val billing: BillingRepository,
    private val history: EatenHistory,
) : ViewModel() {
    private val reading = MutableStateFlow<Reading>(Reading.Loading)
    private val selectedDishId = MutableStateFlow<String?>(null)
    private var job: Job? = null

    val uiState: StateFlow<MenuUiState> =
        combine(reading, selectedDishId, billing.isPlus, history.keys) { reading, selected, isPlus, eaten ->
            val photo = (source as? MenuSource.Photo)?.jpeg ?: reading.cachedPhoto
            when (reading) {
                Reading.Loading -> {
                    MenuUiState.Loading(photo)
                }

                is Reading.Failed -> {
                    MenuUiState.Failed(photo, reading.error)
                }

                is Reading.Dishes -> {
                    if (reading.dishes.isEmpty() && !reading.inProgress) {
                        MenuUiState.Empty(photo)
                    } else if (reading.dishes.isEmpty()) {
                        MenuUiState.Loading(photo)
                    } else {
                        MenuUiState.Ready(
                            photo = photo,
                            meta = reading.meta,
                            dishes = reading.dishes,
                            isReading = reading.inProgress,
                            notice = reading.notice,
                            selectedDishId = selected,
                            isPlus = isPlus,
                            eatenKeys = eaten,
                        )
                    }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MenuUiState.Loading((source as? MenuSource.Photo)?.jpeg),
        )

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun selectDish(dishId: String) {
        selectedDishId.value = dishId
    }

    fun dismissDish() {
        selectedDishId.value = null
    }

    /** Choosing is part of Plus: free diners are shown what they would get instead. */
    fun routeForMode(mode: ChoiceMode): Route? {
        val ready = uiState.value as? MenuUiState.Ready ?: return null
        return if (billing.isPlus.value) Route.Choose(ready.dishes, mode) else Route.Paywall(PaywallReason.Choosing)
    }

    fun setEaten(
        dish: Dish,
        eaten: Boolean,
    ) {
        if (!billing.isPlus.value) return
        viewModelScope.launch { history.setEaten(dishHistoryKey(dish), eaten) }
    }

    private fun load() {
        job?.cancel()
        reading.value = Reading.Loading
        job =
            viewModelScope.launch {
                when (source) {
                    is MenuSource.Saved -> {
                        openSaved(source.cacheKey)
                    }

                    is MenuSource.Photo -> {
                        repository
                            .scan(
                                source.jpeg,
                                Locale.current.toLanguageTag(),
                            ).collect(::onProgress)
                    }

                    MenuSource.Sample -> {
                        repository.sample().collect(::onProgress)
                    }
                }
            }
    }

    private suspend fun openSaved(cacheKey: String) {
        val saved = repository.open(cacheKey)
        reading.value =
            if (saved == null) {
                Reading.Failed(AppError.Malformed)
            } else {
                Reading.Dishes(
                    saved.menu.meta,
                    saved.menu.dishes,
                    inProgress = false,
                    notice = null,
                    cachedPhoto = saved.photo,
                )
            }
    }

    private fun onProgress(progress: ScanProgress) {
        reading.value =
            when (progress) {
                is ScanProgress.Reading -> {
                    Reading.Dishes(progress.meta, progress.dishes, inProgress = true, notice = null)
                }

                is ScanProgress.Failed -> {
                    Reading.Failed(progress.error)
                }

                is ScanProgress.Finished -> {
                    val charged = source is MenuSource.Photo && progress.countsAgainstFreeTier && !billing.isPlus.value
                    if (charged) quota.recordScan()
                    val notice =
                        when {
                            progress.isPartial -> MenuNotice.Partial
                            progress.menu.meta.truncated -> MenuNotice.Truncated
                            progress.isKnownMenu && source is MenuSource.Photo -> MenuNotice.KnownMenu
                            charged && quota.quota.value.isExhausted -> MenuNotice.LastFreeScan
                            else -> null
                        }
                    Reading.Dishes(progress.menu.meta, progress.menu.dishes, inProgress = false, notice = notice)
                }
            }
    }

    private sealed interface Reading {
        val cachedPhoto: ByteArray? get() = null

        data object Loading : Reading

        data class Failed(
            val error: AppError,
        ) : Reading

        data class Dishes(
            val meta: MenuMeta,
            val dishes: List<Dish>,
            val inProgress: Boolean,
            val notice: MenuNotice?,
            override val cachedPhoto: ByteArray? = null,
        ) : Reading
    }
}
