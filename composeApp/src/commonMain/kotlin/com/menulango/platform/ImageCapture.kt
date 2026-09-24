package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/*
 * Image capture and image compression are the only two things MenuLango implements per platform.
 * Everything else — networking, storage, billing, every screen — is shared.
 */

/** What the viewfinder can currently do. */
internal sealed interface CameraState {
    data object Starting : CameraState

    data object Ready : CameraState

    /** The diner declined camera access. The gallery still works. */
    data object PermissionDenied : CameraState

    /** No usable camera (simulator, hardware fault, in use elsewhere). */
    data object Unavailable : CameraState
}

/**
 * The shared handle on a platform camera.
 *
 * The platform [CameraViewfinder] fills in [takePhoto] and [requestAccess] once the camera is
 * bound; shared code only ever calls [capture].
 */
internal class CameraController {
    var state: CameraState by mutableStateOf(CameraState.Starting)
        internal set

    internal var takePhoto: (suspend () -> ByteArray?)? = null
    internal var requestAccess: () -> Unit = {}

    /** Returns an upload-ready JPEG (see [compressForUpload]), or null if the capture failed. */
    suspend fun capture(): ByteArray? = takePhoto?.invoke()

    /** Asks for camera access again, or opens system settings if the platform will no longer ask. */
    fun retryAccess(): Unit = requestAccess()
}

/** Full-bleed live camera preview, bound to [controller]. */
@Composable
internal expect fun CameraViewfinder(
    controller: CameraController,
    modifier: Modifier,
)

/** The outcome of the system photo picker. */
internal sealed interface PickedPhoto {
    /** Upload-ready, already passed through [compressForUpload]. */
    class Chosen(
        val jpeg: ByteArray,
    ) : PickedPhoto

    data object Cancelled : PickedPhoto

    data object Unreadable : PickedPhoto
}

/** Returns a launcher for the system photo picker. */
@Composable
internal expect fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): () -> Unit

/**
 * Prepares a photo for the proxy: upright, long edge [UPLOAD_LONG_EDGE_PX], JPEG quality
 * [UPLOAD_JPEG_QUALITY]. That lands in Gemini's four-tile band (~1,000 image tokens) and keeps
 * the upload around 300 KB, which matters on restaurant wifi. Returns null if undecodable.
 */
internal expect suspend fun compressForUpload(encoded: ByteArray): ByteArray?

internal const val UPLOAD_LONG_EDGE_PX: Int = 1536
internal const val UPLOAD_JPEG_QUALITY: Int = 80
