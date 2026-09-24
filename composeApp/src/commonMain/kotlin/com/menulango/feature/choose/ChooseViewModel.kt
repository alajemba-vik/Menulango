package com.menulango.feature.choose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.data.billing.BillingRepository
import com.menulango.data.history.EatenHistory
import com.menulango.data.menu.model.Dish
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface ChooseUiState {
    data object Loading : ChooseUiState

    data class Ready(
        val mode: ChoiceMode,
        val pick: Pick,
        val openDish: Dish?,
        val isPlus: Boolean,
    ) : ChooseUiState

    /** The menu has no dishes at all. */
    data object Empty : ChooseUiState
}

/** The one dish revealed for a mode — always with its reason, never a bare name. */
internal sealed interface Pick {
    data class Found(
        val dish: Dish,
        val hasAnother: Boolean,
        val ordered: Boolean,
    ) : Pick

    /** Nothing on this menu fits the mode. Said plainly, not hidden. */
    data object NoneFits : Pick
}

/**
 * Picks a dish for the diner — locally, from the menu already on screen, with no second model call.
 */
internal class ChooseViewModel(
    private val dishes: List<Dish>,
    initialMode: ChoiceMode,
    billing: BillingRepository,
    private val history: EatenHistory,
) : ViewModel() {
    private val selection = MutableStateFlow(Selection(initialMode, index = 0, ordered = false))
    private val openDish = MutableStateFlow<Dish?>(null)

    /** Captured when the screen opens, so ordering a dish doesn't instantly re-rank "Something new". */
    private val eatenAtOpen = MutableStateFlow<Set<String>?>(null)

    init {
        viewModelScope.launch { history.keys.collect { if (eatenAtOpen.value == null) eatenAtOpen.value = it } }
    }

    val uiState: StateFlow<ChooseUiState> =
        combine(selection, openDish, eatenAtOpen, billing.isPlus) { selection, open, eaten, isPlus ->
            when {
                dishes.isEmpty() -> {
                    ChooseUiState.Empty
                }

                eaten == null -> {
                    ChooseUiState.Loading
                }

                else -> {
                    val ranked = DishChooser.rank(selection.mode, dishes, eaten)
                    val pick =
                        ranked.getOrNull(selection.index % ranked.size.coerceAtLeast(1))?.let {
                            Pick.Found(it, hasAnother = ranked.size > 1, ordered = selection.ordered)
                        } ?: Pick.NoneFits
                    ChooseUiState.Ready(selection.mode, pick, open, isPlus)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChooseUiState.Loading)

    fun selectMode(mode: ChoiceMode) {
        selection.value = Selection(mode, index = 0, ordered = false)
    }

    fun showAnother() {
        selection.value = selection.value.let { it.copy(index = it.index + 1, ordered = false) }
    }

    fun openDish(dish: Dish) {
        openDish.value = dish
    }

    fun closeDish() {
        openDish.value = null
    }

    /** "I'll have this": remembered, so tomorrow's "Something new" is new. */
    fun order(dish: Dish) {
        selection.value = selection.value.copy(ordered = true)
        viewModelScope.launch { history.setEaten(dishHistoryKey(dish), eaten = true) }
    }

    private data class Selection(
        val mode: ChoiceMode,
        val index: Int,
        val ordered: Boolean,
    )
}
