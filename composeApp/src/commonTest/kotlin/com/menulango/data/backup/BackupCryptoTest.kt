package com.menulango.data.backup

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class BackupCryptoTest {
    @Test
    fun encryptThenDecryptRestoresTheExactBytes() =
        runTest {
            val source = "A note for the waiter: no cucumber.".encodeToByteArray()

            val encrypted = BackupCrypto.encrypt(source, "a long enough passphrase")

            assertContentEquals(source, BackupCrypto.decrypt(encrypted, "a long enough passphrase"))
        }

    @Test
    fun wrongPassphraseCannotDecryptTheBackup() =
        runTest {
            val encrypted = BackupCrypto.encrypt("private picks".encodeToByteArray(), "correct passphrase")

            assertNull(BackupCrypto.decrypt(encrypted, "wrong passphrase"))
        }
}
