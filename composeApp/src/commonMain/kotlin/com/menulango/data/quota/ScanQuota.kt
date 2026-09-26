package com.menulango.data.quota

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the free-tier counter lives between launches.
 *
 * An interface so the month-reset rules can be tested without a real settings store.
 */
internal interface QuotaStorage {
    var month: String?
    var used: Int
}

/**
 * How many free scans remain this month, as the capture screen shows it.
 */
internal data class QuotaState(
    val used: Int,
    val allowance: Int,
) {
    val remaining: Int get() = (allowance - used).coerceAtLeast(0)
    val isExhausted: Boolean get() = remaining == 0
}

/**
 * Counts free scans per calendar month.
 *
 * Exists because the free tier is three scans a month, reset on the 1st: nobody is locked out
 * forever, and a traveller who burns all three on the first evening of a trip meets the paywall
 * while the need is acute. The count is local only. Reinstalling resets it; at a quarter of a cent
 * per scan that abuse is cheaper than the server-side quota that would prevent it.
 *
 * @param currentMonth returns the month key, for example "2026-09". Injected so tests can move time.
 */
internal class ScanQuota(
    private val storage: QuotaStorage,
    private val currentMonth: () -> String,
    private val allowance: Int = FREE_SCANS_PER_MONTH,
) {
    private val state = MutableStateFlow(read())

    /** Observed by the capture screen; refreshed whenever a scan is recorded or the month turns. */
    val quota: StateFlow<QuotaState> = state.asStateFlow()

    /** Re-reads the counter, applying the monthly reset. Call on resume: the month can turn while backgrounded. */
    fun refresh(): QuotaState = read().also { state.value = it }

    /** Records one completed scan. Only called once the diner has seen the result. */
    fun recordScan() {
        refresh()
        storage.used = storage.used + 1
        state.value = read()
    }

    private fun read(): QuotaState {
        val month = currentMonth()
        if (storage.month != month) {
            storage.month = month
            storage.used = 0
        }
        return QuotaState(used = storage.used, allowance = allowance)
    }

    companion object {
        const val FREE_SCANS_PER_MONTH: Int = 3

        /**
         * A free menu may have this many pages, and still counts as one scan. Plus reads menus of
         * any length. Every page is a model call, so this is what keeps the free tier's cost flat.
         */
        const val FREE_PAGES_PER_MENU: Int = 3
    }
}
