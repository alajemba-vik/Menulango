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

    private val state = MutableStateFlow<Map<Long, Scan>>(emptyMap())
    val scans: StateFlow<Map<Long, Scan>> = state.asStateFlow()

    fun update(scan: Scan) {
        state.value = state.value + (scan.id to scan)
    }

    fun finish(id: Long) {
        state.value = state.value - id
    }
}
