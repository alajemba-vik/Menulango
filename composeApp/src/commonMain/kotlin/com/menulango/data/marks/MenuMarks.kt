package com.menulango.data.marks

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the diner has added to one saved menu. Everything is optional and stays on the phone. */
@Serializable
internal data class MenuMark(
    /** The restaurant's name, when the diner gives one: menus rarely print it. */
    val name: String? = null,
    /** Dishes swiped out of view on this menu. */
    val hidden: Set<String> = emptySet(),
    /** Every dish picked from this menu, oldest first: the diner may well have ordered these. */
    val picked: List<String> = emptyList(),
    /** A line the diner wrote about a picked dish, afterwards. */
    val notes: Map<String, String> = emptyMap(),
)

/** The diner's own marks on their saved menus, kept by the menu's cache key. */
internal class MenuMarks(
    private val settings: Settings? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(read())

    val all: StateFlow<Map<String, MenuMark>> = state.asStateFlow()

    fun of(menuKey: String): Flow<MenuMark> = state.map { it[menuKey] ?: MenuMark() }.distinctUntilChanged()

    fun current(menuKey: String): MenuMark = state.value[menuKey] ?: MenuMark()

    fun rename(
        menuKey: String,
        name: String?,
    ) = update(menuKey) { it.copy(name = name?.trim()?.take(MAX_NAME)?.takeIf(String::isNotEmpty)) }

    fun hide(
        menuKey: String,
        dishId: String,
    ) = update(menuKey) { it.copy(hidden = it.hidden + dishId) }

    fun unhide(
        menuKey: String,
        dishId: String,
    ) = update(menuKey) { it.copy(hidden = it.hidden - dishId) }

    fun recordPick(
        menuKey: String,
        dishId: String,
    ) {
        if (dishId !in current(menuKey).picked) update(menuKey) { it.copy(picked = it.picked + dishId) }
    }

    fun note(
        menuKey: String,
        dishId: String,
        text: String,
    ) = update(menuKey) { mark ->
        val clean = text.trim().take(MAX_NOTE)
        mark.copy(notes = if (clean.isEmpty()) mark.notes - dishId else mark.notes + (dishId to clean))
    }

    /** Called when a saved menu is deleted, so nothing about it lingers. */
    fun forget(menuKey: String) {
        state.value = state.value - menuKey
        persist()
    }

    fun clear() {
        state.value = emptyMap()
        persist()
    }

    fun backup(): Map<String, MenuMark> = state.value

    /** Marks on this phone win; the backup only fills in menus this phone knows nothing about. */
    fun merge(incoming: Map<String, MenuMark>) {
        state.value = incoming + state.value
        persist()
    }

    private fun update(
        menuKey: String,
        change: (MenuMark) -> MenuMark,
    ) {
        state.value = state.value + (menuKey to change(current(menuKey)))
        persist()
    }

    private fun read(): Map<String, MenuMark> =
        settings
            ?.getStringOrNull(KEY)
            ?.let { runCatching { json.decodeFromString<Map<String, MenuMark>>(it) }.getOrNull() }
            .orEmpty()

    private fun persist() {
        settings?.putString(KEY, json.encodeToString(state.value))
    }

    private companion object {
        const val KEY = "menu.marks"
        const val MAX_NAME = 60
        const val MAX_NOTE = 280
    }
}
