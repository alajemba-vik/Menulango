package com.menulango

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.menulango.data.menu.model.Dish
import com.menulango.feature.choose.ChoiceMode

/** Where a menu screen gets its menu from. */
internal sealed interface MenuSource {
    /** A fresh photograph, upload-ready. */
    class Photo(
        val jpeg: ByteArray,
    ) : MenuSource

    /** A menu already on the device. Free to reopen, offline. */
    data class Saved(
        val cacheKey: String,
    ) : MenuSource

    /** The bundled sample, for debug builds and for iterating on the UI with no network. */
    data object Sample : MenuSource
}

/** Why the paywall opened, so its first line can say so honestly. */
internal enum class PaywallReason { OutOfScans, Choosing, Upgrade }

/** The five screens. The dish detail is a sheet inside [Menu] and [Choose], not a sixth screen. */
internal sealed interface Route {
    data object Capture : Route

    data class Menu(
        val source: MenuSource,
    ) : Route

    data class Choose(
        val dishes: List<Dish>,
        val mode: ChoiceMode,
    ) : Route

    data class Paywall(
        val reason: PaywallReason,
    ) : Route
}

/**
 * One screen on the back stack, owning the ViewModels created while it is shown.
 *
 * Exists so each screen's ViewModels live exactly as long as the screen: two menus opened one
 * after another get two MenuViewModels, and popping a screen clears its state.
 */
internal class BackStackEntry(
    val route: Route,
    val id: Long,
) : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

/**
 * A deliberately small back stack. Five screens do not justify a navigation library, and owning
 * the stack keeps the transitions — which are part of the product — fully under our control.
 */
internal class Navigator {
    private var nextId = 0L
    val entries = mutableStateListOf(BackStackEntry(Route.Capture, nextId++))

    val current: BackStackEntry get() = entries.last()
    val canGoBack: Boolean get() = entries.size > 1

    fun push(route: Route) {
        entries.add(BackStackEntry(route, nextId++))
    }

    /** Replaces the top screen, so back skips it (a paywall that unlocked, a retaken photo). */
    fun replace(route: Route) {
        val top = entries.removeAt(entries.lastIndex)
        top.viewModelStore.clear()
        entries.add(BackStackEntry(route, nextId++))
    }

    fun pop() {
        if (!canGoBack) return
        entries.removeAt(entries.lastIndex).viewModelStore.clear()
    }
}
