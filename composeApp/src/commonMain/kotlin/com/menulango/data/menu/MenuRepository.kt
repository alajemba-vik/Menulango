package com.menulango.data.menu

import com.menulango.core.result.AppError
import com.menulango.data.menu.local.CachedMenu
import com.menulango.data.menu.local.CachedMenuSummary
import com.menulango.data.menu.local.MenuCache
import com.menulango.data.menu.local.MenuCacheKey
import com.menulango.data.menu.local.SavedMenuItem
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.Menu
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.menu.remote.MenuApi
import com.menulango.data.menu.remote.MenuAssembler
import com.menulango.data.menu.remote.MenuResponseParser
import com.menulango.data.menu.remote.MenuStreamScanner
import com.menulango.data.menu.remote.ScanFailure
import com.menulango.data.menu.remote.StreamFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * How a scan is going, as the menu screen renders it.
 */
internal sealed interface ScanProgress {
    /** The model is still writing. [dishes] grows as each one is finished. */
    data class Reading(
        val meta: MenuMeta,
        val dishes: List<Dish>,
    ) : ScanProgress

    /**
     * The response has ended.
     *
     * @param isKnownMenu this menu was already on the device, so the scan is not charged to the free tier.
     * @param isPartial the stream was cut short; [menu] holds the dishes that did arrive.
     */
    data class Finished(
        val menu: Menu,
        val cacheKey: String?,
        val isKnownMenu: Boolean,
        val isPartial: Boolean,
    ) : ScanProgress {
        /** An empty result means an unreadable photo, and nobody pays for an unreadable photo. */
        val countsAgainstFreeTier: Boolean get() = !isKnownMenu && menu.dishes.isNotEmpty()
    }

    data class Failed(
        val error: AppError,
    ) : ScanProgress
}

/** How one page of a multi-page menu is going. Nothing is cached until the menu is remembered. */
internal sealed interface PageProgress {
    data class Reading(
        val meta: MenuMeta,
        val dishes: List<Dish>,
    ) : PageProgress

    /** The page has ended. [dishes] may be empty: an unreadable page is not an error. */
    data class Done(
        val meta: MenuMeta,
        val dishes: List<Dish>,
        val isPartial: Boolean,
    ) : PageProgress

    data class Failed(
        val error: AppError,
    ) : PageProgress
}

/** Where a remembered menu was saved, and whether the device already had it. */
internal data class Remembered(
    val cacheKey: String,
    val isKnownMenu: Boolean,
)

/**
 * The only place the network and the cache meet.
 *
 * Exists so ViewModels ask for "this photo's menu" and never learn where it came from. When the
 * shared server-side cache arrives it slots in here, behind the same [ScanProgress] flow, and no
 * ViewModel changes.
 */
internal class MenuRepository(
    private val api: MenuApi,
    private val parser: MenuResponseParser,
    private val cache: MenuCache,
    private val sampleMenu: suspend () -> ByteArray,
    private val log: (String) -> Unit,
) {
    /** Reads a photographed menu, streaming dishes as the model finishes each one. */
    fun scan(
        jpeg: ByteArray,
        locale: String,
    ): Flow<ScanProgress> = read(api.scan(jpeg, locale), photo = jpeg)

    /**
     * Reads one page of a longer menu. Pages are merged by the caller, which then [remember]s the
     * whole menu, so a four-page menu is one saved menu rather than four.
     */
    fun scanPage(
        jpeg: ByteArray,
        locale: String,
    ): Flow<PageProgress> = readPage(api.scan(jpeg, locale))

    /**
     * Saves a menu, replacing [replacing] — the same menu as it stood before its latest page —
     * so growing a menu page by page leaves one entry behind, not one per page.
     */
    suspend fun remember(
        menu: Menu,
        photo: ByteArray?,
        replacing: String?,
    ): Remembered {
        val key = MenuCacheKey.of(menu.dishes.map { it.originalName }, location = null)
        val isKnown = key.value != replacing && cache.containsMenu(key.fingerprint)
        if (replacing != null && replacing != key.value) cache.delete(replacing)
        cache.save(key, menu, photo)
        return Remembered(key.value, isKnown)
    }

    /**
     * Replays the bundled sample menu through the same streaming path, paced like a real
     * response. The fast way to iterate on every screen with no camera, network or API spend.
     */
    fun sample(): Flow<ScanProgress> =
        read(
            flow {
                sampleMenu().asList().chunked(SAMPLE_CHUNK_BYTES).forEach { chunk ->
                    delay(SAMPLE_CHUNK_DELAY_MS)
                    emit(chunk.toByteArray())
                }
            },
            photo = null,
        )

    suspend fun open(cacheKey: String): CachedMenu? = cache.open(cacheKey)

    fun latest(): Flow<CachedMenuSummary?> = cache.latest()

    fun saved(): Flow<List<SavedMenuItem>> = cache.all()

    suspend fun forget(cacheKey: String) = cache.delete(cacheKey)

    suspend fun forgetAll() = cache.clear()

    private fun read(
        source: Flow<ByteArray>,
        photo: ByteArray?,
    ): Flow<ScanProgress> =
        flow {
            readPage(source).collect { progress ->
                when (progress) {
                    is PageProgress.Reading -> {
                        emit(ScanProgress.Reading(progress.meta, progress.dishes))
                    }

                    is PageProgress.Failed -> {
                        emit(ScanProgress.Failed(progress.error))
                    }

                    is PageProgress.Done -> {
                        if (progress.dishes.isEmpty()) {
                            emit(emptyResult(progress.meta))
                        } else {
                            val meta = if (progress.isPartial) progress.meta.copy(truncated = true) else progress.meta
                            val menu = Menu(meta, progress.dishes)
                            val saved = remember(menu, photo, replacing = null)
                            emit(
                                ScanProgress.Finished(
                                    menu,
                                    saved.cacheKey,
                                    isKnownMenu = saved.isKnownMenu,
                                    isPartial = progress.isPartial,
                                ),
                            )
                        }
                    }
                }
            }
        }

    /** Streams one response into validated dishes. Knows nothing of caching or of other pages. */
    private fun readPage(source: Flow<ByteArray>): Flow<PageProgress> =
        flow {
            val scanner = MenuStreamScanner()
            val assembler = MenuAssembler()
            var meta = MenuMeta.Unknown
            var rejected = 0
            emit(PageProgress.Reading(meta, emptyList()))

            val failure =
                try {
                    source.collect { chunk ->
                        var changed = false
                        for (fragment in scanner.feed(chunk)) {
                            when (fragment) {
                                is StreamFragment.MetaBlock -> {
                                    meta = parser.parseMeta(fragment.json)
                                    changed = true
                                }

                                is StreamFragment.DishObject -> {
                                    val dish = parser.parseDish(fragment.json)
                                    if (dish == null) rejected++ else changed = assembler.add(dish) || changed
                                }
                            }
                        }
                        if (changed) emit(PageProgress.Reading(meta, assembler.dishes))
                    }
                    null
                } catch (e: ScanFailure) {
                    e
                }

            val dishes = assembler.dishes
            if (dishes.isEmpty()) {
                val error =
                    when {
                        failure != null -> failure.error
                        !scanner.isComplete -> AppError.Malformed
                        rejected > 0 -> AppError.Malformed
                        else -> null
                    }
                log("scan: dishes=0 rejected=$rejected complete=${scanner.isComplete} error=$error")
                emit(if (error != null) PageProgress.Failed(error) else PageProgress.Done(meta, emptyList(), false))
                return@flow
            }

            // A cut-off stream still gives the diner every dish that arrived whole.
            val isPartial = failure != null || !scanner.isComplete
            // The client half of the three numbers worth watching: cache hits and parse failures.
            log("scan: dishes=${dishes.size} rejected=$rejected partial=$isPartial")
            emit(PageProgress.Done(meta, dishes, isPartial))
        }

    private fun emptyResult(meta: MenuMeta) =
        ScanProgress.Finished(Menu(meta, emptyList()), cacheKey = null, isKnownMenu = false, isPartial = false)

    private companion object {
        const val SAMPLE_CHUNK_BYTES = 96
        const val SAMPLE_CHUNK_DELAY_MS = 9L
    }
}
