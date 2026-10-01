package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.math.max

@Composable
internal actual fun CameraViewfinder(
    controller: CameraController,
    modifier: Modifier,
) {
    val camera = remember { MenuCamera() }
    DisposableEffect(camera) {
        controller.takePhoto = { camera.takeUploadReadyPhoto() }
        controller.requestAccess = { camera.requestAccess(controller) }
        camera.start(controller)
        onDispose {
            controller.takePhoto = null
            camera.stop()
        }
    }
    UIKitView(
        factory = { camera.preview },
        modifier = modifier,
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
    )
}

/**
 * An AVFoundation photo session with a live preview. Session work runs off the main thread, as
 * AVFoundation requires; state changes are reported back on the main thread.
 */
private class MenuCamera {
    private val session = AVCaptureSession()
    private val output = AVCapturePhotoOutput()
    private var configured = false
    private var inFlight: PhotoDelegate? = null

    val preview = PreviewView(session)

    fun start(controller: CameraController) {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> configureAndRun(controller)
            AVAuthorizationStatusNotDetermined -> requestAccess(controller)
            else -> controller.state = CameraState.PermissionDenied
        }
    }

    fun requestAccess(controller: CameraController) {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) != AVAuthorizationStatusNotDetermined) {
            // iOS asks only once; after that the switch lives in Settings.
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
            }
            return
        }
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            dispatch_async(dispatch_get_main_queue()) {
                if (granted) configureAndRun(controller) else controller.state = CameraState.PermissionDenied
            }
        }
    }

    private fun configureAndRun(controller: CameraController) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val ready = configured || configure()
            if (ready) session.startRunning()
            dispatch_async(dispatch_get_main_queue()) {
                controller.state = if (ready) CameraState.Ready else CameraState.Unavailable
            }
        }
    }

    private fun configure(): Boolean {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false
        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetPhoto
        val ok = session.canAddInput(input) && session.canAddOutput(output)
        if (ok) {
            session.addInput(input)
            session.addOutput(output)
        }
        session.commitConfiguration()
        configured = ok
        return ok
    }

    fun stop() {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            if (session.isRunning()) session.stopRunning()
        }
    }

    suspend fun takeUploadReadyPhoto(): ByteArray? {
        if (!session.isRunning()) return null
        val result = CompletableDeferred<NSData?>()
        val delegate = PhotoDelegate { result.complete(it) }
        inFlight = delegate // AVFoundation holds its delegate weakly.
        output.capturePhotoWithSettings(AVCapturePhotoSettings.photoSettings(), delegate)
        val data = result.await()
        inFlight = null
        return data?.toByteArray()?.let { compressForUpload(it) }
    }
}

private class PhotoDelegate(
    private val onPhoto: (NSData?) -> Unit,
) : NSObject(),
    AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        if (error != null) println("MenuLango capture failed: ${error.localizedDescription}")
        onPhoto(if (error == null) didFinishProcessingPhoto.fileDataRepresentation() else null)
    }
}

/** Hosts the preview layer and keeps it sized to the view as Compose lays it out. */
private class PreviewView(
    session: AVCaptureSession,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val previewLayer =
        AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}

@Composable
internal actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): (maxPhotos: Int?) -> Unit {
    val delegate =
        remember {
            PickerDelegate(
                onCancel = { onPicked(PickedPhoto.Cancelled) },
                onPicked = { loaded ->
                    // Its own scope: the photos keep loading after the camera gives way to the menu.
                    val pages =
                        CoroutineScope(
                            Dispatchers.Default,
                        ).async { loaded.await().mapNotNull { compressForUpload(it) } }
                    onPicked(PickedPhoto.Chosen(pages))
                },
            )
        }
    // The picker runs in its own process, which iOS starts on first use: the first tap on the
    // gallery used to hang for a moment. Built once, unseen, soon after the camera screen settles,
    // so the process is already running when the diner reaches for it.
    LaunchedEffect(Unit) {
        if (pickerWarmed) return@LaunchedEffect
        delay(PICKER_WARM_DELAY_MS)
        pickerWarmed = true
        PHPickerViewController(PHPickerConfiguration().apply { filter = PHPickerFilter.imagesFilter }).view
    }
    return remember(delegate) {
        { maxPhotos ->
            val configuration =
                PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    // Zero is the picker's own "no limit".
                    selectionLimit = (maxPhotos ?: 0).toLong()
                    selection = PHPickerConfigurationSelectionOrdered
                }
            val picker = PHPickerViewController(configuration).apply { this.delegate = delegate }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** Once per launch is enough: after the first picker, iOS keeps its process around. */
private var pickerWarmed = false
private const val PICKER_WARM_DELAY_MS = 1_200L

/**
 * Reports the pick as soon as the picker closes, then loads every photo and hands them over
 * together, in the order they were picked.
 */
private class PickerDelegate(
    private val onCancel: () -> Unit,
    private val onPicked: (Deferred<List<ByteArray>>) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            onCancel()
            return
        }
        val all = CompletableDeferred<List<ByteArray>>()
        onPicked(all)
        val loaded = arrayOfNulls<ByteArray>(results.size)
        var remaining = results.size
        results.forEachIndexed { index, result ->
            result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (error != null) println("MenuLango photo picker: ${error.localizedDescription}")
                val bytes = data?.toByteArray() ?: ByteArray(0)
                dispatch_async(dispatch_get_main_queue()) {
                    loaded[index] = bytes
                    remaining--
                    if (remaining == 0) all.complete(loaded.map { it ?: ByteArray(0) })
                }
            }
        }
    }
}

internal actual suspend fun compressForUpload(encoded: ByteArray): ByteArray? =
    withContext(Dispatchers.Default) {
        if (encoded.isEmpty()) return@withContext null
        val image = UIImage.imageWithData(encoded.toNSData()) ?: return@withContext null
        val (width, height) = image.size.useContents { width to height }
        val longEdge = max(width, height)
        val scale = if (longEdge > UPLOAD_LONG_EDGE_PX) UPLOAD_LONG_EDGE_PX / longEdge else 1.0
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
        val target = CGSizeMake(width * scale, height * scale)
        // Drawing through the renderer applies the photo's orientation, so the upload is upright.
        val upright =
            UIGraphicsImageRenderer(size = target, format = format).imageWithActions { _ ->
                image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
            }
        UIImageJPEGRepresentation(upright, UPLOAD_JPEG_QUALITY / 100.0)?.toByteArray()
    }

private fun topViewController(): UIViewController? {
    val window =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
