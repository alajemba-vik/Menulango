package com.menulango.data.backup

import com.menulango.data.history.EatenDishRecord
import com.menulango.data.history.EatenHistory
import com.menulango.data.marks.MenuMark
import com.menulango.data.marks.MenuMarks
import com.menulango.data.menu.local.BackupSavedMenu
import com.menulango.data.menu.local.MenuCache
import com.menulango.data.preferences.PreferenceBackup
import com.menulango.data.preferences.Preferences
import com.menulango.data.tips.Tips
import com.menulango.feature.order.OrderBook
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A portable, encrypted copy of the data a diner owns. The data never leaves their device except
 * through the system share sheet they choose themselves.
 */
internal class BackupService(
    private val preferences: Preferences,
    private val menus: MenuCache,
    private val history: EatenHistory,
    private val orders: OrderBook,
    private val marks: MenuMarks,
    private val tips: Tips,
) {
    suspend fun export(passphrase: String): ByteArray {
        require(passphrase.isNotBlank()) { "A backup passphrase is required." }
        val payload =
            BackupPayload(
                preferences = preferences.backup(),
                menus = menus.backup(),
                eatenDishes = history.backup(),
                orders = orders.backup(),
                marks = marks.backup(),
                tips = tips.backup(),
            )
        return BackupCrypto.encrypt(json.encodeToString(payload).encodeToByteArray(), passphrase)
    }

    suspend fun restore(
        encrypted: ByteArray,
        passphrase: String,
    ): BackupRestoreResult {
        if (passphrase.isBlank()) return BackupRestoreResult.WrongPassphraseOrDamaged
        val decoded = BackupCrypto.decrypt(encrypted, passphrase) ?: return BackupRestoreResult.WrongPassphraseOrDamaged
        val payload =
            try {
                json.decodeFromString<BackupPayload>(decoded.decodeToString())
            } catch (_: IllegalArgumentException) {
                return BackupRestoreResult.WrongPassphraseOrDamaged
            } catch (_: SerializationException) {
                return BackupRestoreResult.WrongPassphraseOrDamaged
            }
        if (payload.version != FORMAT_VERSION) return BackupRestoreResult.UnsupportedVersion

        preferences.merge(payload.preferences)
        menus.merge(payload.menus)
        history.merge(payload.eatenDishes)
        orders.merge(payload.orders)
        marks.merge(payload.marks)
        tips.merge(payload.tips)
        return BackupRestoreResult.Restored
    }

    private companion object {
        const val FORMAT_VERSION = 1
        val json = Json { ignoreUnknownKeys = true }
    }
}

internal enum class BackupRestoreResult { Restored, WrongPassphraseOrDamaged, UnsupportedVersion }

/** The Swift shell owns iOS's native share sheet and document picker. */
public interface IosBackupImportCallback {
    public fun onData(encoded: String?)
}

public interface IosBackupFileBridge {
    public fun share(
        encoded: String,
        filename: String,
    )

    public fun pick(callback: IosBackupImportCallback)
}

@Serializable
private data class BackupPayload(
    val version: Int = 1,
    val preferences: PreferenceBackup,
    val menus: List<BackupSavedMenu>,
    val eatenDishes: List<EatenDishRecord>,
    val orders: Map<String, com.menulango.feature.order.TableOrder>,
    /** Added later; older backups simply have none. */
    val marks: Map<String, MenuMark> = emptyMap(),
    /** Which how-to notes have been read. Also added later. */
    val tips: Set<String> = emptySet(),
)

/** The self-contained file envelope. A new random salt and AES-GCM nonce are used every time. */
@OptIn(ExperimentalEncodingApi::class)
internal object BackupCrypto {
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val PBKDF2_ITERATIONS = 600_000
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun encrypt(
        plaintext: ByteArray,
        passphrase: String,
    ): ByteArray {
        val salt = CryptographyRandom.Default.nextBytes(SALT_BYTES)
        val key = deriveKey(passphrase, salt)
        val cipher =
            CryptographyProvider.Default
                .get(
                    AES.GCM,
                ).keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, key)
                .cipher()
        val ciphertext = cipher.encrypt(plaintext)
        return json
            .encodeToString(
                EncryptedBackupEnvelope(
                    version = VERSION,
                    salt = Base64.encode(salt),
                    ciphertext = Base64.encode(ciphertext),
                ),
            ).encodeToByteArray()
    }

    suspend fun decrypt(
        encrypted: ByteArray,
        passphrase: String,
    ): ByteArray? =
        try {
            val envelope = json.decodeFromString<EncryptedBackupEnvelope>(encrypted.decodeToString())
            if (envelope.version != VERSION) return null
            val salt = Base64.decode(envelope.salt)
            val ciphertext = Base64.decode(envelope.ciphertext)
            val key = deriveKey(passphrase, salt)
            CryptographyProvider.Default
                .get(AES.GCM)
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, key)
                .cipher()
                .decrypt(ciphertext)
        } catch (_: Exception) {
            null
        }

    private suspend fun deriveKey(
        passphrase: String,
        salt: ByteArray,
    ): ByteArray =
        CryptographyProvider.Default
            .get(PBKDF2)
            .secretDerivation(SHA256, PBKDF2_ITERATIONS, 32.bytes, salt)
            .deriveSecretToByteArray(passphrase.encodeToByteArray())
}

@Serializable
private data class EncryptedBackupEnvelope(
    val version: Int,
    val salt: String,
    val ciphertext: String,
)
