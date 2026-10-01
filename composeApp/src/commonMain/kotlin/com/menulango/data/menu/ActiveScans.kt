package com.menulango.data.menu

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Menus still being read after the diner left the menu screen. Menus shows them straight away,
 * with "Reading the menu…", instead of the scan vanishing until it is done.
 */
internal class ActiveScans {
    data class Scan(
        val id: Long,
        val cover: ByteArray?,
        /** Null until the first page is saved; from then the saved card carries the message. */
        val cacheKey: String?,
        val pagesRead: Int,
        val pagesTotal: Int,
    )

    /** A scan that ended with nothing saved after the diner had left its screen. */
    data class Failure(
        val id: Long,
        /** True when the photos were read but held no dishes; false when reading itself failed. */
        val nothingFound: Boolean,
    )

    private val state = MutableStateFlow<Map<Long, Scan>>(emptyMap())
    val scans: StateFlow<Map<Long, Scan>> = state.asStateFlow()

    private val unseen = MutableStateFlow<List<Failure>>(emptyList())

    /** Kept until told, so the diner hears about it even if no screen was listening at the time. */
    val failures: StateFlow<List<Failure>> = unseen.asStateFlow()

    fun update(scan: Scan) {
        state.value = state.value + (scan.id to scan)
    }

    fun finish(id: Long) {
        state.value = state.value - id
    }

    fun fail(failure: Failure) {
        unseen.value += failure
    }

    fun told(id: Long) {
        unseen.value = unseen.value.filterNot { it.id == id }
    }
}
