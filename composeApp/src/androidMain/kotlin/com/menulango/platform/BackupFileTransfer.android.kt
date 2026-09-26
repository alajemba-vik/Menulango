package com.menulango.platform

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val BACKUP_MIME_TYPE = "application/vnd.menulango.backup"

@Composable
internal actual fun rememberBackupFileTransfer(): BackupFileTransfer {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var resultHandler by remember { mutableStateOf<((ByteArray?) -> Unit)?>(null) }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val handler = resultHandler
            resultHandler = null
            scope.launch {
                val contents =
                    withContext(Dispatchers.IO) {
                        uri?.let { selected ->
                            runCatching {
                                context.contentResolver
                                    .openInputStream(
                                        selected,
                                    )?.use { it.readBytes() }
                            }.getOrNull()
                        }
                    }
                handler?.invoke(contents)
            }
        }

    return remember(context, importLauncher) {
        object : BackupFileTransfer {
            override fun share(
                filename: String,
                contents: ByteArray,
            ) {
                scope.launch {
                    val uri =
                        withContext(Dispatchers.IO) {
                            val directory = File(context.cacheDir, "backups").apply { mkdirs() }
                            val file = File(directory, filename)
                            file.writeBytes(contents)
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        }
                    context.startActivity(
                        Intent(Intent.ACTION_SEND)
                            .setType(BACKUP_MIME_TYPE)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .let { Intent.createChooser(it, "Back up MenuLango data") },
                    )
                }
            }

            override fun pick(onResult: (ByteArray?) -> Unit) {
                resultHandler = onResult
                importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream"))
            }
        }
    }
}
