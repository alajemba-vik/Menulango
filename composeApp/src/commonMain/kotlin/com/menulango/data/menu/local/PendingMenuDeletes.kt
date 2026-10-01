package com.menulango.data.menu.local

import com.russhwolf.settings.Settings

/**
 * Menus the diner has swiped away but could still undo. Kept on the phone, not only in memory, so
 * a delete is never lost if the app is closed before the undo message runs out: whatever is still
 * listed here the next time Menus opens is deleted for good.
 */
internal class PendingMenuDeletes(
    private val settings: Settings,
) {
    fun all(): Set<String> =
        settings
            .getStringOrNull(KEY)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    fun add(cacheKey: String) = save(all() + cacheKey)

    fun remove(cacheKey: String) = save(all() - cacheKey)

    private fun save(keys: Set<String>) = settings.putString(KEY, keys.joinToString(SEPARATOR))

    private companion object {
        const val KEY = "menus.pendingDeletes"

        // Cache keys are a geohash and a hex fingerprint, so they never contain a newline.
        const val SEPARATOR = "\n"
    }
}
