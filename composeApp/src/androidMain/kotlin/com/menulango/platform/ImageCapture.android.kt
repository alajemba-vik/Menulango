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
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val permanentlyDenied = remember { booleanArrayOf(false) }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                camera.start(context, lifecycleOwner, controller)
            } else {
                // After a second refusal Android stops showing the dialog; send them to settings instead.
                permanentlyDenied[0] =
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                controller.state = CameraState.PermissionDenied
            }
        }

    DisposableEffect(camera) {
        controller.takePhoto = { camera.takeUploadReadyPhoto(context) }
        controller.requestAccess = {
            if (permanentlyDenied[0]) context.openAppSettings() else permission.launch(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            camera.start(context, lifecycleOwner, controller)
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
        onDispose {
            controller.takePhoto = null
            camera.unbind()
        }
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
internal actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri == null) {
                onPicked(PickedPhoto.Cancelled)
            } else {
                scope.launch {
                    val jpeg = readPicked(context, uri)?.let { compressForUpload(it) }
                    onPicked(if (jpeg == null) PickedPhoto.Unreadable else PickedPhoto.Chosen(jpeg))
                }
            }
        }
    return remember(picker) {
        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
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
