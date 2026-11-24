package net.oxog.phonebox.deskpanel.jarvis.core.vision

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraManager(private val context: Context) {
    private var provider: ProcessCameraProvider? = null

    fun capture(owner: LifecycleOwner, onFrame: (Bitmap?) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            try {
                val cameras = future.get()
                provider = cameras
                val capture = ImageCapture.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                if (!cameras.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    onFrame(null)
                    return@addListener
                }
                cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
                capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = try { image.toBitmap() } catch (_: RuntimeException) { null }
                        finally { image.close(); cameras.unbind(capture) }
                        onFrame(bitmap)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        cameras.unbind(capture)
                        onFrame(null)
                    }
                })
            } catch (_: Exception) {
                onFrame(null)
            }
        }, executor)
    }

    fun shutdown() { provider?.unbindAll() }
}

