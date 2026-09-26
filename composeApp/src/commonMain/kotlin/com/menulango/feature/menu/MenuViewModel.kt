package com.menulango.feature.menu

import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menulango.MenuSource
import com.menulango.PaywallReason
import com.menulango.Route
import com.menulango.core.result.AppError
import com.menulango.core.ui.menuColourOf
import com.menulango.data.billing.BillingRepository
import com.menulango.data.history.EatenHistory
import com.menulango.data.menu.ActiveScans
import com.menulango.data.menu.MenuRepository
import com.menulango.data.menu.PageMerger
import com.menulango.data.menu.PageProgress
import com.menulango.data.menu.ScanProgress
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.Menu
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.preferences.Preferences
import com.menulango.data.quota.ScanQuota
import com.menulango.data.search.DishSearch
import com.menulango.data.search.DishSearchTag
import com.menulango.data.search.TextSearch
import com.menulango.feature.choose.ChoiceMode
import com.menulango.feature.choose.dishHistoryKey
import com.menulango.feature.order.OrderBook
import com.menulango.feature.order.TableOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The menu screen, in exactly one of four states. There is no "maybe loaded" field.
 */
internal sealed interface MenuUiState {
    /** The photo the diner took, shown dimmed behind everything. Null for the sample menu. */
    val photo: ByteArray?

    data class Loading(
        override val photo: ByteArray?,
    ) : MenuUiState

    data class Ready(
        override val photo: ByteArray?,
        val meta: MenuMeta,
        val dishes: List<Dish>,
        /** True while more dishes are still arriving from the stream. */
        val isReading: Boolean,
        val notice: MenuNotice?,
        val selectedDishId: String?,
        val isPlus: Boolean,
        val eatenKeys: Set<String>,
        val filters: Set<DishFilter> = emptySet(),
        /** The diner's own words, still in force on this menu. */
        val avoid: Set<String> = emptySet(),
        /** Whether the diner wants "Don't leave without trying" at all. */
        val showFeatured: Boolean = true,
        /** Present for a menu being photographed page by page; null for the sample or a saved menu. */
        val pages: PageStatus? = null,
        /** What the diner typed in the menu's search, and which extra fields they chose to include. */
        val search: DishSearch = DishSearch(),
    ) : MenuUiState {
        val selectedDish: Dish? get() = dishes.firstOrNull { it.id == selectedDishId }

        private val filtering = DishFilters(dishes)

        /** The dishes left after the diner's filters, in menu order. */
        val visibleDishes: List<Dish> =
            filtering.apply(filters, avoid).filter { dish ->
                TextSearch.matches(
                    search.query,
                    buildList {
                        add(dish.readableName)
                        add(dish.originalName)
                        if (DishSearchTag.Ingredients in search.tags) addAll(dish.ingredients)
                        if (DishSearchTag.Description in search.tags) {
                            add(dish.whatItIs)
                            add(dish.howItIsMade)
                            add(dish.pitch)
                        }
                    },
                )
            }

        val filterOptions: List<DishFilter> = filtering.options(filters)

        val budgetLimit: Double? get() = filtering.budgetLimit

        /** Anything narrowing the menu, the diner's own words included. */
        val isFiltering: Boolean get() = filters.isNotEmpty() || avoid.isNotEmpty() || search.isActive
    }

    data class Empty(
        override val photo: ByteArray?,
    ) : MenuUiState

    data class Failed(
        override val photo: ByteArray?,
        val error: AppError,
    ) : MenuUiState
}

/**
 * Where a multi-page menu has got to.
 *
 * @param reading the page being read now, counting from 1; null between pages.
 * @param waiting true while the next page waits out the proxy's per-minute limit.
 * @param unreadable pages, counting from 1, that produced no dishes.
 * @param freePagesLeft how many more pages a free menu may take; null for Plus, which has no limit.
 */
internal data class PageStatus(
    val total: Int,
    val reading: Int?,
    val waiting: Boolean,
    val unreadable: List<Int>,
    val freePagesLeft: Int?,
    /** The page each dish was first printed on, counting from 1, by dish id. */
    val pageOfDish: Map<String, Int> = emptyMap(),
)

/**
 * What colour the menu's header is. Worked out before the menu is shown, so the header never
 * flashes coral and then turns the restaurant's colour.
 */
internal sealed interface HeaderTone {
    /** The photo is still being read for its colour: a neutral header, briefly. */
    data object Pending : HeaderTone

    /** No photo, or a menu printed without colour: MenuLango coral. */
    data object Brand : HeaderTone

    data class Menu(
        val colour: androidx.compose.ui.graphics.Color,
    ) : HeaderTone
}

/** One quiet line of context under the dish list, when something is worth saying. */
internal enum class MenuNotice { Partial, Truncated, KnownMenu, LastFreeScan }

/**
 * Reads one menu — from photos of its pages, the device cache or the bundled sample — and records
 * the scan against the free tier only once the diner has seen the result.
 *
 * Photographed pages are read one at a time, never in parallel: it keeps every request inside the
 * proxy's per-device limits, and the diner is usually still photographing the next page anyway.
 * However many pages a menu has, it is one menu, one saved entry and at most one free scan.
 */
internal class MenuViewModel(
    private val source: MenuSource,
    private val repository: MenuRepository,
    private val quota: ScanQuota,
    private val billing: BillingRepository,
    private val history: EatenHistory,
    private val inbox: PageInbox,
    private val preferences: Preferences,
    private val orders: OrderBook,
    /** Outlives this screen: a menu being read keeps reading, and is saved, after the diner leaves. */
    private val appScope: CoroutineScope,
    private val activeScans: ActiveScans,
) : ViewModel() {
    private val reading = MutableStateFlow<Reading>(Reading.Loading)
    private val selectedDishId = MutableStateFlow<String?>(null)

    /** Starts from the diner's standing dietary profile; they can still change it for this menu. */
    private val filters = MutableStateFlow(preferences.dietary.value.toFilters())
    private val avoid = MutableStateFlow(preferences.avoid.value)
    private val search = MutableStateFlow(DishSearch())
    private val showFeatured = preferences.showFeatured.value
    private var job: Job? = null

    /** Identifies this menu to the add-page camera, so its pages come back here. */
    private val sessionId = Random.nextLong()
    private val pages = PageSession()

    private val tone =
        MutableStateFlow<HeaderTone>(if (source == MenuSource.Sample) HeaderTone.Brand else HeaderTone.Pending)
    val headerTone: StateFlow<HeaderTone> = tone

    private suspend fun readTone(photo: ByteArray?) {
        tone.value = photo?.let { menuColourOf(it) }?.let { HeaderTone.Menu(it) } ?: HeaderTone.Brand
    }

    /** Which table order this menu keeps, shared with the choose screen it opens. */
    private val orderKey: String =
        when (source) {
            is MenuSource.Saved -> "saved:${source.cacheKey}"
            is MenuSource.Photos -> "session:$sessionId"
            MenuSource.Sample -> "sample"
        }

    val order: StateFlow<TableOrder> =
        orders.order(orderKey).stateIn(viewModelScope, SharingStarted.Eagerly, orders.current(orderKey))

    fun updateOrder(change: (TableOrder) -> TableOrder) = orders.update(orderKey, change)

    val uiState: StateFlow<MenuUiState> =
        combine(
            reading,
            selectedDishId,
            billing.isPlus,
            history.keys,
            combine(filters, avoid, search) { f, a, s -> Triple(f, a, s) },
        ) { reading, selected, isPlus, eaten, (filters, avoid, search) ->
            val photo = pages.cover ?: reading.cachedPhoto
            when (reading) {
                Reading.Loading -> {
                    MenuUiState.Loading(photo)
                }

                is Reading.Failed -> {
                    MenuUiState.Failed(photo, reading.error)
                }

                is Reading.Dishes -> {
                    if (reading.dishes.isEmpty() && !reading.inProgress) {
                        MenuUiState.Empty(photo)
                    } else if (reading.dishes.isEmpty()) {
                        MenuUiState.Loading(photo)
                    } else {
                        MenuUiState.Ready(
                            photo = photo,
                            meta = reading.meta,
                            dishes = reading.dishes,
                            isReading = reading.inProgress,
                            notice = reading.notice,
                            selectedDishId = selected,
                            isPlus = isPlus,
                            eatenKeys = eaten,
                            filters = filters,
                            avoid = avoid,
                            search = search,
                            showFeatured = showFeatured,
                            pages =
                                reading.pages?.let {
                                    it.copy(
                                        freePagesLeft = if (isPlus) null else it.freePagesLeft,
                                    )
                                },
                        )
                    }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MenuUiState.Loading((source as? MenuSource.Photos)?.pages?.first()),
        )

    init {
        load()
        viewModelScope.launch {
            inbox.incoming.collect { delivery -> if (delivery.sessionId == sessionId) addPages(delivery.pages) }
        }
    }

    fun retry() {
        load()
    }

    fun selectDish(dishId: String) {
        selectedDishId.value = dishId
    }

    fun dismissDish() {
        selectedDishId.value = null
    }

    fun toggleFilter(filter: DishFilter) {
        filters.value = if (filter in filters.value) filters.value - filter else filters.value + filter
    }

    fun clearFilters() {
        filters.value = emptySet()
        avoid.value = emptySet()
        search.value = DishSearch()
    }

    fun openSearch() {
        search.value = search.value.copy(open = true)
    }

    /** Cancel: the field goes and so does what it was narrowing, as iOS's search does. */
    fun closeSearch() {
        search.value = DishSearch()
    }

    fun setSearchQuery(query: String) {
        search.value = search.value.copy(query = query.take(MAX_QUERY))
    }

    fun toggleSearchTag(tag: DishSearchTag) {
        val tags = search.value.tags
        search.value = search.value.copy(tags = if (tag in tags) tags - tag else tags + tag)
    }

    /** Lets a dish the diner usually avoids back onto this menu, without changing their settings. */
    fun dropAvoid(word: String) {
        avoid.value -= word
    }

    /**
     * Choosing is part of Plus: free diners are shown what they would get instead. The modes choose
     * among the dishes the diner's filters left, so "no pork" still holds when the app picks.
     */
    fun routeForMode(mode: ChoiceMode): Route? {
        val ready = uiState.value as? MenuUiState.Ready ?: return null
        return if (billing.isPlus.value) {
            Route.Choose(ready.visibleDishes, mode, orderKey)
        } else {
            Route.Paywall(PaywallReason.Choosing)
        }
    }

    /** The camera for another page — or, for a free menu that has all its pages, the paywall. */
    fun routeForAddPage(): Route {
        val left = pagesLeft()
        return if (left == 0) Route.Paywall(PaywallReason.MorePages) else Route.AddPage(sessionId, left)
    }

    fun setEaten(
        dish: Dish,
        eaten: Boolean,
    ) {
        if (!billing.isPlus.value) return
        viewModelScope.launch { history.setEaten(dishHistoryKey(dish), eaten) }
    }

    private fun load() {
        job?.cancel()
        reading.value = Reading.Loading
        // A photographed menu is read in the app's scope, not the screen's: going back mid-read
        // must not throw the scan away. It finishes quietly and appears under Menus.
        val scope = if (source is MenuSource.Photos) appScope else viewModelScope
        job =
            scope.launch {
                when (source) {
                    is MenuSource.Saved -> {
                        openSaved(source.cacheKey)
                    }

                    is MenuSource.Photos -> {
                        pages.restart(source.pages)
                        readTone(source.pages.first())
                        try {
                            reportScan()
                            for (index in pages.queue) {
                                readPage(index)
                                reportScan()
                            }
                        } finally {
                            activeScans.finish(sessionId)
                        }
                    }

                    MenuSource.Sample -> {
                        repository.sample().collect(::onProgress)
                    }
                }
            }
    }

    /**
     * Leaving the screen: pages already queued are still read and saved in the app's scope, but
     * no new ones can arrive, so the reading ends instead of waiting forever.
     */
    override fun onCleared() {
        if (source is MenuSource.Photos) pages.closeQueue()
    }

    /** Tells Menus how far this scan has got, so it can show it while the diner is elsewhere. */
    private fun reportScan() {
        activeScans.update(
            ActiveScans.Scan(
                id = sessionId,
                cover = pages.cover,
                cacheKey = pages.cacheKey,
                pagesRead = pages.finished,
                pagesTotal = pages.count,
            ),
        )
    }

    /** Null when there is no limit (Plus), otherwise how many more pages a free menu may take. */
    private fun pagesLeft(): Int? =
        if (billing.isPlus.value) null else (ScanQuota.FREE_PAGES_PER_MENU - pages.count).coerceAtLeast(0)

    private fun addPages(photos: List<ByteArray>) {
        if (source !is MenuSource.Photos) return
        val accepted = pagesLeft()?.let { photos.take(it) } ?: photos
        accepted.forEach(pages::add)
        reportScan()
        publishPages()
    }

    /** Reads one page, waiting out the proxy's per-minute limit rather than giving the page up. */
    private suspend fun readPage(index: Int) {
        var attempt = 0
        while (true) {
            pages.readingIndex = index
            pages.waiting = false
            publishPages()
            var outcome: PageProgress? = null
            repository.scanPage(pages.photo(index), preferences.contentLanguageTag).collect { progress ->
                if (progress is PageProgress.Reading) {
                    pages.update(index, progress.meta, progress.dishes)
                    publishPages()
                } else {
                    outcome = progress
                }
            }
            val failed = outcome as? PageProgress.Failed
            if (failed?.error == AppError.RateLimited && attempt < RATE_LIMIT_RETRIES) {
                attempt++
                pages.waiting = true
                publishPages()
                delay(RATE_LIMIT_WAIT_MS * attempt)
                continue
            }
            when (val finished = outcome) {
                is PageProgress.Done -> pages.finish(index, finished.meta, finished.dishes, finished.isPartial)
                is PageProgress.Failed -> pages.fail(index, finished.error)
                else -> pages.fail(index, AppError.Malformed)
            }
            break
        }
        pages.readingIndex = null
        pages.waiting = false
        rememberPages()
        publishPages()
    }

    /** Saves the menu as it now stands and, the first time it has dishes, settles the free scan. */
    private suspend fun rememberPages() {
        val dishes = PageMerger.dishes(pages.dishes)
        if (dishes.isEmpty()) return
        val menu = Menu(pages.meta(), dishes)
        val firstSave = pages.cacheKey == null
        val saved = repository.remember(menu, pages.cover, replacing = pages.cacheKey)
        pages.cacheKey = saved.cacheKey
        if (firstSave) {
            val charged = !saved.isKnownMenu && !billing.isPlus.value
            if (charged) quota.recordScan()
            pages.baseNotice =
                when {
                    saved.isKnownMenu -> MenuNotice.KnownMenu
                    charged && quota.quota.value.isExhausted -> MenuNotice.LastFreeScan
                    else -> null
                }
        }
    }

    private fun publishPages() {
        val dishes = PageMerger.dishes(pages.dishes)
        val inProgress = pages.readingIndex != null || pages.finished < pages.count
        reading.value =
            when {
                dishes.isEmpty() && inProgress -> {
                    Reading.Loading
                }

                dishes.isEmpty() -> {
                    pages.firstError?.let { Reading.Failed(it) }
                        ?: Reading.Dishes(pages.meta(), emptyList(), inProgress = false, notice = null)
                }

                else -> {
                    Reading.Dishes(
                        meta = pages.meta(),
                        dishes = dishes,
                        inProgress = inProgress,
                        notice = pages.notice(),
                        pages =
                            PageStatus(
                                total = pages.count,
                                reading = pages.readingIndex?.plus(1),
                                waiting = pages.waiting,
                                unreadable = pages.unreadable.map { it + 1 },
                                freePagesLeft = (ScanQuota.FREE_PAGES_PER_MENU - pages.count).coerceAtLeast(0),
                                pageOfDish = PageMerger.pageOfDish(pages.dishes),
                            ),
                    )
                }
            }
    }

    private suspend fun openSaved(cacheKey: String) {
        val saved = repository.open(cacheKey)
        // The colour is known before the menu is shown, never after.
        readTone(saved?.photo)
        reading.value =
            if (saved == null) {
                Reading.Failed(AppError.Malformed)
            } else {
                Reading.Dishes(
                    saved.menu.meta,
                    saved.menu.dishes,
                    inProgress = false,
                    notice = null,
                    cachedPhoto = saved.photo,
                )
            }
    }

    /** The bundled sample: never charged, never "known". */
    private fun onProgress(progress: ScanProgress) {
        reading.value =
            when (progress) {
                is ScanProgress.Reading -> {
                    Reading.Dishes(progress.meta, progress.dishes, inProgress = true, notice = null)
                }

                is ScanProgress.Failed -> {
                    Reading.Failed(progress.error)
                }

                is ScanProgress.Finished -> {
                    val notice =
                        when {
                            progress.isPartial -> MenuNotice.Partial
                            progress.menu.meta.truncated -> MenuNotice.Truncated
                            else -> null
                        }
                    Reading.Dishes(progress.menu.meta, progress.menu.dishes, inProgress = false, notice = notice)
                }
            }
    }

    private sealed interface Reading {
        val cachedPhoto: ByteArray? get() = null

        data object Loading : Reading

        data class Failed(
            val error: AppError,
        ) : Reading

        data class Dishes(
            val meta: MenuMeta,
            val dishes: List<Dish>,
            val inProgress: Boolean,
            val notice: MenuNotice?,
            override val cachedPhoto: ByteArray? = null,
            val pages: PageStatus? = null,
        ) : Reading
    }

    private companion object {
        /** The proxy allows a few scans a minute per device; a long menu waits its turn. */
        const val RATE_LIMIT_RETRIES = 6
        const val RATE_LIMIT_WAIT_MS = 10_000L
    }
}

/**
 * The pages of one photographed menu and what each has produced so far. Only touched from the
 * ViewModel's main-thread coroutines, so it needs no locking.
 */
private class PageSession {
    private val photos = mutableListOf<ByteArray>()
    private val metas = mutableListOf<MenuMeta>()
    private val partial = mutableListOf<Boolean>()
    val dishes = mutableListOf<List<Dish>>()
    val unreadable = mutableListOf<Int>()

    /** Page indexes waiting to be read, in order. Never closed: more pages can always arrive. */
    var queue = Channel<Int>(Channel.UNLIMITED)
        private set

    var readingIndex: Int? = null
    var waiting = false
    var finished = 0
        private set
    var firstError: AppError? = null
        private set
    var cacheKey: String? = null
    var baseNotice: MenuNotice? = null

    val count: Int get() = photos.size

    /** The first page, shown behind the header and kept with the saved menu. */
    val cover: ByteArray? get() = photos.firstOrNull()

    fun photo(index: Int): ByteArray = photos[index]

    /** No more pages can join: the reading loop finishes what is queued, then ends. */
    fun closeQueue() {
        queue.close()
    }

    fun restart(first: List<ByteArray>) {
        queue.close()
        queue = Channel(Channel.UNLIMITED)
        photos.clear()
        metas.clear()
        partial.clear()
        dishes.clear()
        unreadable.clear()
        readingIndex = null
        waiting = false
        finished = 0
        firstError = null
        cacheKey = null
        baseNotice = null
        first.forEach(::add)
    }

    fun add(photo: ByteArray) {
        photos += photo
        metas += MenuMeta.Unknown
        partial += false
        dishes += emptyList<Dish>()
        queue.trySend(photos.lastIndex)
    }

    fun update(
        index: Int,
        meta: MenuMeta,
        pageDishes: List<Dish>,
    ) {
        metas[index] = meta
        dishes[index] = pageDishes
    }

    fun finish(
        index: Int,
        meta: MenuMeta,
        pageDishes: List<Dish>,
        isPartial: Boolean,
    ) {
        update(index, meta, pageDishes)
        partial[index] = isPartial
        if (pageDishes.isEmpty()) unreadable += index
        finished++
    }

    fun fail(
        index: Int,
        error: AppError,
    ) {
        dishes[index] = emptyList()
        unreadable += index
        if (firstError == null) firstError = error
        finished++
    }

    fun meta(): MenuMeta {
        val merged = PageMerger.meta(metas.filter { it != MenuMeta.Unknown })
        return if (partial.any { it }) merged.copy(truncated = true) else merged
    }

    fun notice(): MenuNotice? =
        when {
            partial.any { it } -> MenuNotice.Partial
            metas.any { it.truncated } -> MenuNotice.Truncated
            else -> baseNotice
        }
}

private const val MAX_QUERY = 60
