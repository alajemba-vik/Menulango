package com.menulango

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.menulango.data.menu.model.Dish
import com.menulango.feature.choose.ChoiceMode

/** Where a menu screen gets its menu from. */
internal sealed interface MenuSource {
    /** Fresh photographs of a menu's pages, upload-ready, in reading order. More can follow. */
    class Photos(
        val pages: List<ByteArray>,
    ) : MenuSource {
        init {
            require(pages.isNotEmpty()) { "a menu needs at least one page" }
        }
    }

    /** A menu already on the device. Free to reopen, offline. */
    data class Saved(
        val cacheKey: String,
    ) : MenuSource

    /** The bundled sample, for debug builds and for iterating on the UI with no network. */
    data object Sample : MenuSource
}

/** Why the paywall opened, so its first line can say so honestly. */
internal enum class PaywallReason { OutOfScans, Choosing, MorePages, Upgrade }

/** The screens; adding a page reuses the capture screen. The dish detail is a sheet inside [Menu] and [Choose], not a sixth screen. */
internal sealed interface Route {
    /** The camera, the saved menus and settings, under the tab bar. Always the root. */
    data object Home : Route

    data class Menu(
        val source: MenuSource,
    ) : Route

    /**
     * The camera again, to add pages to the menu being read in [sessionId].
     *
     * @param maxPages how many more pages this menu may take; null for no limit (Plus).
     */
    data class AddPage(
        val sessionId: Long,
        val maxPages: Int?,
    ) : Route

    /** @param orderKey the table order a pick joins, shared with the menu it came from. */
    data class Choose(
        val dishes: List<Dish>,
        val mode: ChoiceMode,
        val orderKey: String,
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
    val entries = mutableStateListOf(BackStackEntry(Route.Home, nextId++))

    val current: BackStackEntry get() = entries.last()

    /**
     * Set when a fresh scan opens: the new menu is saved under Menus, so going back from it lands
     * there, next to it, rather than on the camera it came from.
     */
    var showMenusOnReturn by mutableStateOf(false)
    val canGoBack: Boolean get() = entries.size > 1

    fun push(route: Route) {
        if (route is Route.Menu && route.source is MenuSource.Photos) showMenusOnReturn = true
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
