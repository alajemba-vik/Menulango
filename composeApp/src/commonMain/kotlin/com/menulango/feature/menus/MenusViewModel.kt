package com.menulango.feature.menus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.data.marks.MenuMark
import com.menulango.data.marks.MenuMarks
import com.menulango.data.menu.MenuRepository
import com.menulango.data.menu.local.SavedMenuItem
import com.menulango.data.menu.model.Dish
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface MenusUiState {
    data object Loading : MenusUiState

    /** @param nowMillis when the list was built, so "3 days ago" is worked out consistently. */
    data class Ready(
        val menus: List<SavedMenuItem>,
        val nowMillis: Long,
    ) : MenusUiState
}

/**
 * Every menu the diner has read. Deleting hides a menu at once and only removes it for good when
 * the undo window closes, so a slipped swipe costs nothing.
 */
internal class MenusViewModel(
    private val repository: MenuRepository,
    private val nowMillis: () -> Long,
    private val menuMarks: MenuMarks,
) : ViewModel() {
    private val pendingDeletes = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<MenusUiState> =
        combine(repository.saved(), pendingDeletes) { menus, pending ->
            MenusUiState.Ready(menus.filterNot { it.cacheKey in pending }, nowMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MenusUiState.Loading)

    /**
     * Each saved menu's dishes, read once in the background: they give every card a one-line
     * summary, let the "Dishes" search tag look inside, and fill the diner's notes.
     */
    val dishes = MutableStateFlow<Map<String, List<Dish>>>(emptyMap())

    /** The diner's marks on each menu: a name, dishes picked, notes on them. */
    val marks: StateFlow<Map<String, MenuMark>> = menuMarks.all

    init {
        viewModelScope.launch {
            repository.saved().collect { menus ->
                val missing = menus.map { it.cacheKey }.filterNot { it in dishes.value }
                if (missing.isEmpty()) return@collect
                val loaded =
                    missing.associateWith { key ->
                        repository
                            .open(key)
                            ?.menu
                            ?.dishes
                            .orEmpty()
                    }
                dishes.value = dishes.value + loaded
            }
        }
    }

    fun note(
        cacheKey: String,
        dishId: String,
        text: String,
    ) = menuMarks.note(cacheKey, dishId, text)

    fun hide(cacheKey: String) {
        pendingDeletes.value += cacheKey
    }

    fun undo(cacheKey: String) {
        pendingDeletes.value -= cacheKey
    }

    fun commit(cacheKey: String) {
        viewModelScope.launch {
            repository.forget(cacheKey)
            menuMarks.forget(cacheKey)
            pendingDeletes.value -= cacheKey
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
