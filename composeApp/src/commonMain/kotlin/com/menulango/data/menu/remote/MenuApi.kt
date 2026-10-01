package com.menulango.data.menu.remote

import com.menulango.core.result.AppError
import com.menulango.data.AppAttestation
import com.menulango.data.DeviceIdentity
import com.menulango.di.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

/**
 * The one network call MenuLango makes: a photo in, the menu JSON streamed back.
 *
 * An interface so the repository can be driven by canned bytes (the sample menu, tests) exactly
 * as it is by the network.
 */
internal interface MenuApi {
    /** Emits the response body in chunks as they arrive. Fails with [ScanFailure]. */
    fun scan(
        jpeg: ByteArray,
        locale: String,
    ): Flow<ByteArray>
}

/** A scan failure the UI can explain. */
internal class ScanFailure(
    val error: AppError,
    cause: Throwable? = null,
) : Exception("Scan failed: $error", cause)

@Serializable
private data class ScanRequestDto(
    val image: String,
    val locale: String,
)

/**
 * Talks to the Cloudflare Worker described in `proxy/README.md`. Never to Gemini directly: the
 * key must not ship inside the app.
 */
internal class ProxyMenuApi(
    private val client: HttpClient,
    private val config: AppConfig,
    private val device: DeviceIdentity,
    private val parser: MenuResponseParser,
    private val attestation: AppAttestation,
) : MenuApi {
    override fun scan(
        jpeg: ByteArray,
        locale: String,
    ): Flow<ByteArray> =
        channelFlow {
            if (!config.hasProxy) throw ScanFailure(AppError.NotConfigured)
            // Cached by Firebase between scans; a slow first attestation must not hold the scan up.
            val appCheck = withTimeoutOrNull(APP_CHECK_TIMEOUT_MS) { attestation.token() }
            try {
                client
                    .preparePost("${config.proxyUrl.trimEnd('/')}/scan") {
                        header(DEVICE_HEADER, device.id)
                        if (appCheck != null) header(APP_CHECK_HEADER, appCheck)
                        contentType(ContentType.Application.Json)
                        setBody(ScanRequestDto(image = Base64.encode(jpeg), locale = locale))
                    }.execute { response ->
                        if (!response.status.isSuccess()) {
                            throw ScanFailure(
                                errorFor(response.status, response.bodyAsText()),
                            )
                        }
                        val body = response.bodyAsChannel()
                        val buffer = ByteArray(CHUNK_BYTES)
                        while (true) {
                            val read = body.readAvailable(buffer, 0, buffer.size)
                            if (read == -1) break
                            if (read > 0) send(buffer.copyOf(read))
                        }
                    }
            } catch (e: HttpRequestTimeoutException) {
                throw ScanFailure(AppError.Offline, e)
            } catch (e: IOException) {
                throw ScanFailure(AppError.Offline, e)
            }
        }

    private fun errorFor(
        status: HttpStatusCode,
        body: String,
    ): AppError {
        val declared = parser.parseProxyError(body)
        return when {
            declared != null -> declared
            status == HttpStatusCode.TooManyRequests -> AppError.RateLimited
            else -> AppError.Upstream
        }
    }

    private companion object {
        const val DEVICE_HEADER = "X-Device-Id"
        const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
        const val APP_CHECK_TIMEOUT_MS = 5_000L
        const val CHUNK_BYTES = 4 * 1024
    }
}
