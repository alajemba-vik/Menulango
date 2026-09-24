package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
internal actual fun rememberPhotoPicker(onPicked: (PickedPhoto) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val delegate =
        remember {
            PickerDelegate { data ->
                scope.launch {
                    if (data == null) {
                        onPicked(PickedPhoto.Cancelled)
                    } else {
                        val jpeg = compressForUpload(data)
                        onPicked(if (jpeg == null) PickedPhoto.Unreadable else PickedPhoto.Chosen(jpeg))
                    }
                }
            }
        }
    return remember(delegate) {
        {
            val configuration =
                PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 1
                }
            val picker = PHPickerViewController(configuration).apply { this.delegate = delegate }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(
    private val onData: (ByteArray?) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onData(null)
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
            if (error != null) println("MenuLango photo picker: ${error.localizedDescription}")
            val bytes = data?.toByteArray() ?: ByteArray(0)
            dispatch_async(dispatch_get_main_queue()) { onData(bytes) }
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
