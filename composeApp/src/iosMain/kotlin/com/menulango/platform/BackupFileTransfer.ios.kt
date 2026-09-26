package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.menulango.data.backup.IosBackupFileBridge
import com.menulango.data.backup.IosBackupImportCallback
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private var bridge: IosBackupFileBridge? = null

/** Installed by the Swift shell before Koin starts. */
public fun installIosBackupFileBridge(value: IosBackupFileBridge) {
    bridge = value
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
internal actual fun rememberBackupFileTransfer(): BackupFileTransfer =
    remember {
        object : BackupFileTransfer {
            override fun share(
                filename: String,
                contents: ByteArray,
            ) {
                bridge?.share(Base64.encode(contents), filename)
            }

            override fun pick(onResult: (ByteArray?) -> Unit) {
                val activeBridge = bridge
                if (activeBridge == null) {
                    onResult(null)
                    return
                }
                activeBridge.pick(
                    object : IosBackupImportCallback {
                        override fun onData(encoded: String?) {
                            onResult(encoded?.let { runCatching { Base64.decode(it) }.getOrNull() })
                        }
                    },
                )
            }
        }
    }
