package com.menulango.data.share

import com.menulango.core.ui.emoji
import com.menulango.data.AppAttestation
import com.menulango.data.DeviceIdentity
import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.Menu
import com.menulango.data.menu.model.MenuMeta
import com.menulango.data.menu.remote.toDocument
import com.menulango.di.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A link to a shared menu, and how long it keeps working. */
internal data class ShareLink(
    val url: String,
    val expiresInSeconds: Long,
)

@Serializable
private data class ShareAnswer(
    val url: String,
    val expiresInSeconds: Long = DAY_S,
)

private const val DAY_S = 24L * 60 * 60

/**
 * Shares an explained menu with the table: the proxy keeps it for a day and answers with a link
 * anyone can open in a browser. Only the explanations go: no photo, no picks, nothing about the
 * diner. The dishes carry the emoji the app shows, so the page looks like the menu they saw.
 */
internal class MenuSharing(
    private val client: HttpClient,
    private val config: AppConfig,
    private val device: DeviceIdentity,
    private val attestation: AppAttestation,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The link, or null when it couldn't be made (offline, the server refused, no proxy). */
    suspend fun share(
        title: String,
        languageTag: String,
        meta: MenuMeta,
        dishes: List<Dish>,
    ): ShareLink? {
        if (!config.hasProxy || dishes.isEmpty()) return null
        val menu = Menu(meta, dishes.map { it.copy(emoji = it.emoji()) }).toDocument()
        val body =
            buildJsonObject {
                put("title", JsonPrimitive(title))
                put("locale", JsonPrimitive(languageTag))
                put("menu", json.parseToJsonElement(menu))
            }.toString()
        return try {
            val token = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) { attestation.token() }
            val response =
                client.post("${config.proxyUrl.trimEnd('/')}/share") {
                    header(DEVICE_HEADER, device.id)
                    if (token != null) header(APP_CHECK_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            if (!response.status.isSuccess()) return null
            val answer = json.decodeFromString(ShareAnswer.serializer(), response.bodyAsText())
            ShareLink(answer.url, answer.expiresInSeconds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val DEVICE_HEADER = "X-Device-Id"
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val APP_CHECK_TIMEOUT_MS = 5_000L
    }
}
