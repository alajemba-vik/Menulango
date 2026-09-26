package com.menulango.platform

import androidx.compose.runtime.Composable

/** Native file sharing and picking, kept outside the data layer and its portable file format. */
internal interface BackupFileTransfer {
    fun share(
        filename: String,
        contents: ByteArray,
    )

    fun pick(onResult: (ByteArray?) -> Unit)
}

@Composable
internal expect fun rememberBackupFileTransfer(): BackupFileTransfer
