package com.menulango.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "MenuLangoCapture"

@Composable
internal actual fun CameraViewfinder(
    controller: CameraController,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val camera = remember { menuCamera(context) }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                camera.start(context, lifecycleOwner, controller)
            } else {
                controller.state = CameraState.PermissionDenied
            }
        }

    DisposableEffect(camera) {
        controller.takePhoto = { camera.takeUploadReadyPhoto(context) }
        // Only on the diner's own tap on Allow. Once Android has stopped showing its dialog (a
        // second refusal), the only way left is the app's page in system settings.
        controller.requestAccess = {
            val androidWillAsk =
                activity == null ||
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            if (context.hasCameraPermission()) {
                // Already granted (in Settings, say): nothing to ask, just open the camera.
                camera.start(context, lifecycleOwner, controller)
            } else if (context.cameraAsked() && !androidWillAsk) {
                context.openAppSettings()
            } else {
                context.markCameraAsked()
                permission.launch(Manifest.permission.CAMERA)
            }
        }
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                camera.start(context, lifecycleOwner, controller)
            }

            // Asked before and refused: show the page that explains and offers Allow, rather than
            // the system prompt again every time the camera screen comes back.
            context.cameraAsked() -> {
                controller.state = CameraState.PermissionDenied
            }

            else -> {
                context.markCameraAsked()
                permission.launch(Manifest.permission.CAMERA)
            }
        }
        onDispose {
            controller.takePhoto = null
            camera.unbind()
        }
    }

    // Back from Settings with the camera now allowed: start it straight away, no second tap.
    LifecycleResumeEffect(camera) {
        if (controller.state == CameraState.PermissionDenied && context.hasCameraPermission()) {
            camera.start(context, lifecycleOwner, controller)
        }
        onPauseOrDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = camera
            }
        },
    )
}

private fun menuCamera(context: Context): LifecycleCameraController =
    LifecycleCameraController(context).apply {
        setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        // No point capturing 12MP only to shrink it to 1536px: ask the sensor for about 2K.
        imageCaptureResolutionSelector =
            ResolutionSelector
                .Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build()
    }

private fun LifecycleCameraController.start(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    controller: CameraController,
) {
    bindToLifecycle(lifecycleOwner)
    initializationFuture.addListener(
        {
            controller.state =
                try {
                    if (hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraState.Ready else CameraState.Unavailable
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Camera failed to initialise", e)
                    CameraState.Unavailable
                }
        },
        ContextCompat.getMainExecutor(context),
    )
}

private suspend fun LifecycleCameraController.takeUploadReadyPhoto(context: Context): ByteArray? {
    val image =
        suspendCancellableCoroutine<ImageProxy?> { continuation ->
            takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) = continuation.resume(image)

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "Capture failed", exception)
                        continuation.resume(null)
                    }
                },
            )
        } ?: return null
    return withContext(Dispatchers.Default) {
        image.use { proxy -> encodeForUpload(proxy.toBitmap().rotated(proxy.imageInfo.rotationDegrees)) }
    }
}

@Composable
internal actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): (maxPhotos: Int?) -> Unit {
    val context = LocalContext.current
    // Android fixes a multi-picker's maximum when it is registered, so the limit is applied here.
    val limit = remember { arrayOfNulls<Int>(1) }
    val deliver: (List<Uri>) -> Unit = { uris ->
        if (uris.isEmpty()) {
            onPicked(PickedPhoto.Cancelled)
        } else {
            // Its own scope: the photos keep loading after the camera screen gives way to the menu.
            val pages =
                CoroutineScope(Dispatchers.Default).async {
                    uris.take(limit[0] ?: uris.size).mapNotNull { uri ->
                        readPicked(context, uri)?.let { compressForUpload(it) }
                    }
                }
            onPicked(PickedPhoto.Chosen(pages))
        }
    }
    // The system file picker rather than the photo picker: the photo picker shows only the photo
    // library, so a menu saved to Downloads (or sent in a chat) couldn't be chosen. The file
    // picker opens on recent images and reaches Downloads, the gallery, Google Photos and Drive.
    // Neither needs a storage permission.
    val single =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { deliver(listOfNotNull(it)) }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { deliver(it) }
    return remember(single, multiple) {
        { maxPhotos ->
            limit[0] = maxPhotos
            if (maxPhotos == 1) single.launch(IMAGE_TYPES) else multiple.launch(IMAGE_TYPES)
        }
    }
}

private suspend fun readPicked(
    context: Context,
    uri: Uri,
): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read picked photo", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Picked photo is not readable", e)
            null
        }
    }

internal actual suspend fun compressForUpload(encoded: ByteArray): ByteArray? =
    withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Decode at the smallest power-of-two size still larger than the target, then scale exactly.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= UPLOAD_LONG_EDGE_PX) sample *= 2
        val decoded =
            BitmapFactory.decodeByteArray(
                encoded,
                0,
                encoded.size,
                BitmapFactory.Options().apply {
                    inSampleSize =
                        sample
                },
            )
                ?: return@withContext null
        encodeForUpload(decoded.rotated(exifRotation(encoded)))
    }

private fun encodeForUpload(source: Bitmap): ByteArray {
    val longEdge = max(source.width, source.height)
    val scaled =
        if (longEdge > UPLOAD_LONG_EDGE_PX) {
            val scale = UPLOAD_LONG_EDGE_PX.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt(),
                (source.height * scale).roundToInt(),
                true,
            )
        } else {
            source
        }
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, out)
        out.toByteArray()
    }
}

private fun exifRotation(encoded: ByteArray): Int =
    try {
        when (
            ExifInterface(
                encoded.inputStream(),
            ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        Log.w(TAG, "Unreadable EXIF; assuming upright", e)
        0
    }

private fun Bitmap.rotated(degrees: Int): Bitmap =
    if (degrees ==
        0
    ) {
        this
    } else {
        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }

private fun Context.openAppSettings() {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Whether the camera permission has ever been asked for, kept across launches. */
private fun Context.cameraAsked(): Boolean =
    getSharedPreferences(CAMERA_PREFS, Context.MODE_PRIVATE).getBoolean(CAMERA_ASKED, false)

private fun Context.markCameraAsked() {
    getSharedPreferences(CAMERA_PREFS, Context.MODE_PRIVATE).edit().putBoolean(CAMERA_ASKED, true).apply()
}

private const val CAMERA_PREFS = "menulango.camera"
private const val CAMERA_ASKED = "permission.asked"

private val IMAGE_TYPES = arrayOf("image/*")
