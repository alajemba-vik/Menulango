package com.menulango.feature.choose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.data.billing.BillingRepository
import com.menulango.data.history.EatenHistory
import com.menulango.data.menu.model.Dish
import com.menulango.feature.order.OrderBook
import com.menulango.feature.order.TableOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface ChooseUiState {
    data object Loading : ChooseUiState

    /**
     * @param deck the dishes this mood suggests, best first, starting with the one on top. Swiping
     *   the top card away brings the next; the deck wraps round when it runs out.
     * @param order the table's picks, which a swipe right adds to.
     */
    data class Ready(
        val mode: ChoiceMode,
        val deck: List<Dish>,
        val openDish: Dish?,
        val isPlus: Boolean,
        val order: TableOrder,
    ) : ChooseUiState {
        val top: Dish? get() = deck.firstOrNull()
    }

    /** The menu has no dishes at all. */
    data object Empty : ChooseUiState
}

/**
 * Suggests dishes for the diner as a deck of cards — locally, from the menu already on screen,
 * with no second model call. A swipe right adds the top dish to the table's picks, a swipe left
 * passes on it; either way the next suggestion comes up.
 */
internal class ChooseViewModel(
    private val dishes: List<Dish>,
    initialMode: ChoiceMode,
    private val orderKey: String,
    billing: BillingRepository,
    private val history: EatenHistory,
    private val orders: OrderBook,
) : ViewModel() {
    private val selection = MutableStateFlow(Selection(initialMode, index = 0))
    private val openDish = MutableStateFlow<Dish?>(null)

    /** Captured when the screen opens, so picking a dish doesn't instantly re-rank "Something new". */
    private val eatenAtOpen = MutableStateFlow<Set<String>?>(null)

    init {
        viewModelScope.launch { history.keys.collect { if (eatenAtOpen.value == null) eatenAtOpen.value = it } }
    }

    val uiState: StateFlow<ChooseUiState> =
        combine(
            selection,
            openDish,
            eatenAtOpen,
            billing.isPlus,
            orders.order(orderKey),
        ) { selection, open, eaten, isPlus, order ->
            when {
                dishes.isEmpty() -> {
                    ChooseUiState.Empty
                }

                eaten == null -> {
                    ChooseUiState.Loading
                }

                else -> {
                    val ranked = DishChooser.rank(selection.mode, dishes, eaten)
                    val deck =
                        if (ranked.isEmpty()) {
                            emptyList()
                        } else {
                            List(minOf(DECK_SHOWN, ranked.size)) { ranked[(selection.index + it) % ranked.size] }
                        }
                    ChooseUiState.Ready(selection.mode, deck, open, isPlus, order)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChooseUiState.Loading)

    fun selectMode(mode: ChoiceMode) {
        selection.value = Selection(mode, index = 0)
    }

    /** Swiped left: not this one. */
    fun pass() {
        selection.value = selection.value.let { it.copy(index = it.index + 1) }
    }

    /**
     * Swiped right: the dish joins the table's picks for whoever is being chosen for, and the next
     * suggestion comes up. Not recorded as eaten — choosing is not eating.
     */
    fun keep(dish: Dish) {
        orders.update(orderKey) { it.add(dish) }
        pass()
    }

    /** The next person at the table: a new seat, so the next swipe right is theirs. */
    fun nextPerson() {
        orders.update(orderKey) { it.addDiner(null) }
    }

    fun openDish(dish: Dish) {
        openDish.value = dish
    }

    fun closeDish() {
        openDish.value = null
    }

    private data class Selection(
        val mode: ChoiceMode,
        val index: Int,
    )

    private companion object {
        /** The top card and the two peeking behind it. */
        const val DECK_SHOWN = 3
    }
}
