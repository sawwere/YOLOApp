package com.sawwere.yoloapp.core.system.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.view.PreviewView
import com.sawwere.yoloapp.core.config.aspectRatio
import com.sawwere.yoloapp.core.domain.image.ImageUtils.imageProxyToBitmapWithRotation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CameraController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageCapture: ImageCapture,
    private val imageAnalysis: ImageAnalysis,
    @Named("ImageAnalyzerExecutor") private val cameraExecutor: ExecutorService
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null

    fun startCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onSuccess: (Camera) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspectRatio)
                        .build()
                )
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
                camera?.let { onSuccess(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun capturePhoto(onResult: (Bitmap?) -> Unit) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmapWithRotation(image)
                    image.close()
                    onResult(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    onResult(null)
                }
            }
        )
    }

    fun setZoomRatio(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    fun getZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun getMinMaxZoom(): Pair<Float, Float> {
        val zoomState = camera?.cameraInfo?.zoomState?.value
        return (zoomState?.minZoomRatio ?: 1f) to (zoomState?.maxZoomRatio ?: 1f)
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraController"
    }
}