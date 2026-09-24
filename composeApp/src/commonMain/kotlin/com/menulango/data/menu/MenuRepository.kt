package com.menulango.data.menu

import com.menulango.core.result.AppError
import com.menulango.data.menu.local.CachedMenu
import com.menulango.data.menu.local.CachedMenuSummary
import com.menulango.data.menu.local.MenuCache
import com.menulango.data.menu.local.MenuCacheKey
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

    private fun read(
        source: Flow<ByteArray>,
        photo: ByteArray?,
    ): Flow<ScanProgress> =
        flow {
            val scanner = MenuStreamScanner()
            val assembler = MenuAssembler()
            var meta = MenuMeta.Unknown
            var rejected = 0
            emit(ScanProgress.Reading(meta, emptyList()))

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
                        if (changed) emit(ScanProgress.Reading(meta, assembler.dishes))
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
                emit(if (error != null) ScanProgress.Failed(error) else emptyResult(meta))
                return@flow
            }

            // A cut-off stream still gives the diner every dish that arrived whole.
            val isPartial = failure != null || !scanner.isComplete
            val menu = Menu(if (isPartial) meta.copy(truncated = true) else meta, dishes)
            val key = MenuCacheKey.of(dishes.map { it.originalName }, location = null)
            val isKnown = cache.containsMenu(key.fingerprint)
            cache.save(key, menu, photo)

            // The client half of the three numbers worth watching: cache hits and parse failures.
            log("scan: dishes=${dishes.size} rejected=$rejected knownMenu=$isKnown partial=$isPartial")
            emit(ScanProgress.Finished(menu, key.value, isKnownMenu = isKnown, isPartial = isPartial))
        }

    private fun emptyResult(meta: MenuMeta) =
        ScanProgress.Finished(Menu(meta, emptyList()), cacheKey = null, isKnownMenu = false, isPartial = false)

    private companion object {
        const val SAMPLE_CHUNK_BYTES = 96
        const val SAMPLE_CHUNK_DELAY_MS = 9L
    }
}
