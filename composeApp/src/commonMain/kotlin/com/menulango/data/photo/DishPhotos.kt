package com.menulango.data.photo

import androidx.compose.runtime.mutableStateMapOf
import com.menulango.data.AppAttestation
import com.menulango.data.DeviceIdentity
import com.menulango.di.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A photo of a dish from Wikimedia Commons, with the credit its licence asks for. */
@Serializable
internal data class DishPhoto(
    val url: String,
    val width: Int,
    val height: Int,
    val credit: String,
    val license: String,
    /** The photo's page on Commons, where the full credit and licence are. */
    val source: String,
)

/** A photo ready to draw: its bytes, and who took it. */
internal class LoadedPhoto(
    val photo: DishPhoto,
    val bytes: ByteArray,
)

@Serializable
private data class PhotoAnswer(
    val photo: DishPhoto? = null,
)

/**
 * Photos of dishes, found through the proxy (which looks them up on Wikipedia and relays the
 * image, so the phone never talks to Wikimedia itself). Remembered for the session, "no photo"
 * included, so reopening a dish is instant. Every failure is quiet: the dish keeps its plate.
 */
internal class DishPhotos(
    private val client: HttpClient,
    private val config: AppConfig,
    private val device: DeviceIdentity,
    private val attestation: AppAttestation,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val known = LinkedHashMap<String, LoadedPhoto?>()

    /** Whether each title has a photo, "no" included: asked ahead so a sheet never promises one it lacks. */
    private val answers = mutableStateMapOf<String, Boolean>()

    /** True or false once the proxy has said whether [wikiTitle] has a photo; null until then. */
    fun hasPhoto(wikiTitle: String): Boolean? = answers[wikiTitle.lowercase()]

    /**
     * Asks, a few at a time, which of a menu's dishes have a photo: a small answer each, no image
     * bytes, and cached on the proxy for everyone. Called once a menu is read, so opening a dish
     * already knows whether to hold room for a photo.
     */
    suspend fun lookAhead(wikiTitles: Collection<String>) {
        if (!config.hasProxy) return
        val gate = Semaphore(LOOK_AHEAD_AT_ONCE)
        coroutineScope {
            val unasked = wikiTitles.map { it.lowercase() }.distinct().filter { it !in answers }
            unasked.take(MAX_LOOK_AHEAD).forEach { title ->
                launch {
                    gate.withPermit {
                        val photo =
                            try {
                                lookup(title)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                return@withPermit
                            }
                        answers[title] = photo != null
                    }
                }
            }
        }
    }

    /** The dish's photo, or null when there is none, the lookup failed or the app has no proxy. */
    suspend fun load(wikiTitle: String): LoadedPhoto? {
        val key = wikiTitle.lowercase()
        lock.withLock { if (known.containsKey(key)) return known[key] }
        if (!config.hasProxy) return null
        val loaded =
            try {
                fetch(wikiTitle)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not remembered: the next opening can try again.
                return null
            }
        answers[key] = loaded != null
        lock.withLock {
            known[key] = loaded
            while (known.size > MAX_REMEMBERED) known.remove(known.keys.first())
        }
        return loaded
    }

    private suspend fun lookup(title: String): DishPhoto? {
        val token = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) { attestation.token() }
        val answer =
            client.get("${config.proxyUrl.trimEnd('/')}/photo") {
                parameter("title", title)
                header(DEVICE_HEADER, device.id)
                if (token != null) header(APP_CHECK_HEADER, token)
            }
        if (!answer.status.isSuccess()) error("photo lookup ${answer.status}")
        return json.decodeFromString(PhotoAnswer.serializer(), answer.bodyAsText()).photo
    }

    private suspend fun fetch(title: String): LoadedPhoto? {
        val base = config.proxyUrl.trimEnd('/')
        val token = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) { attestation.token() }
        val photo = lookup(title) ?: return null
        val image =
            client.get("$base/photo/image") {
                parameter("src", photo.url)
                header(DEVICE_HEADER, device.id)
                if (token != null) header(APP_CHECK_HEADER, token)
            }
        if (!image.status.isSuccess()) error("photo image ${image.status}")
        return LoadedPhoto(photo, image.readRawBytes())
    }

    private companion object {
        const val MAX_REMEMBERED = 80
        const val LOOK_AHEAD_AT_ONCE = 4
        const val MAX_LOOK_AHEAD = 60
        const val DEVICE_HEADER = "X-Device-Id"
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val APP_CHECK_TIMEOUT_MS = 5_000L
    }
}
