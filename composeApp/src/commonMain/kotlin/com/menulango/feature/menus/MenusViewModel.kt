package com.menulango.feature.menus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.data.menu.MenuRepository
import com.menulango.data.menu.local.SavedMenuItem
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
) : ViewModel() {
    private val pendingDeletes = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<MenusUiState> =
        combine(repository.saved(), pendingDeletes) { menus, pending ->
            MenusUiState.Ready(menus.filterNot { it.cacheKey in pending }, nowMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MenusUiState.Loading)

    /**
     * The dish names inside each saved menu, loaded only once the diner adds the "Dishes" tag to
     * their search: a plain search never has to open every saved menu.
     */
    val dishNames = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    fun loadDishNames(cacheKeys: List<String>) {
        val missing = cacheKeys.filterNot { it in dishNames.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded =
                missing.associateWith { key ->
                    repository
                        .open(key)
                        ?.menu
                        ?.dishes
                        ?.flatMap { listOf(it.readableName, it.originalName) }
                        .orEmpty()
                }
            dishNames.value = dishNames.value + loaded
        }
    }

    fun hide(cacheKey: String) {
        pendingDeletes.value += cacheKey
    }

    fun undo(cacheKey: String) {
        pendingDeletes.value -= cacheKey
    }

    fun commit(cacheKey: String) {
        viewModelScope.launch {
            repository.forget(cacheKey)
            pendingDeletes.value -= cacheKey
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
