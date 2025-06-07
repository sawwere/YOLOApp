package com.sawwere.yolov11app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sawwere.yolov11app.camera.presentation.CameraScreen
import com.sawwere.yolov11app.ui.theme.YOLOv11AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), InstanceSegmentation.InstanceSegmentationListener {

    private lateinit var instanceSegmentation: InstanceSegmentation
    private lateinit var drawImages: DrawImages
    private lateinit var cameraExecutor: ExecutorService

    private var camera: Camera? = null
    private var segmentedBitmap: Bitmap? by mutableStateOf(null)
    private var originalBitmap: Bitmap? by mutableStateOf(null)

    // UI states
    private var preProcessTime by mutableStateOf("0")
    private var inferenceTime by mutableStateOf("0")
    private var postProcessTime by mutableStateOf("0")
    private var zoomProgress by mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        drawImages = DrawImages(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()

        instanceSegmentation = InstanceSegmentation(
            context = applicationContext,
            modelPath = "yolo11n-seg_float16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        setContent {
            YOLOv11AppTheme {
                CameraScreen(
                    preProcessTime = preProcessTime,
                    inferenceTime = inferenceTime,
                    postProcessTime = postProcessTime,
                    segmentedBitmap = segmentedBitmap,
                    zoomProgress = zoomProgress,
                    onZoomChanged = { newProgress ->
                        zoomProgress = newProgress
                        updateCameraZoom()
                    },
                    onCaptureClick = {
                        saveCombinedImage()
                    }
                )
            }
        }

        checkPermission()
    }

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val aspectRatio = AspectRatio.RATIO_4_3

            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                setupZoomState()
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomState() {
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value
            zoomState?.let {
                // Initialize zoom progress based on current camera zoom
                zoomProgress = ((it.zoomRatio - it.minZoomRatio) /
                        (it.maxZoomRatio - it.minZoomRatio) * 10).toFloat()
            }
        }
    }

    private fun updateCameraZoom() {
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value ?: return
            val minZoom = zoomState.minZoomRatio
            val maxZoom = zoomState.maxZoomRatio

            val newZoomRatio = minZoom + (zoomProgress / 10f) * (maxZoom - minZoom)
            cam.cameraControl.setZoomRatio(newZoomRatio)
        }
    }

    private fun saveCombinedImage() {
        val original = originalBitmap ?: return
        val segmented = segmentedBitmap ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val combinedBitmap = Bitmap.createBitmap(
                    original.width,
                    original.height,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = android.graphics.Canvas(combinedBitmap)
                canvas.drawBitmap(original, 0f, 0f, null)
                canvas.drawBitmap(segmented, 0f, 0f, null)

                val photoDirectory = File(
                    getExternalFilesDir(null),
                    "Download"
                ).apply { mkdirs() }

                val timestamp = System.currentTimeMillis()
                val photoFile = File(photoDirectory, "combined_image_$timestamp.jpg")

                FileOutputStream(photoFile).use { out ->
                    combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    out.flush()
                }

                addImageToGallery(photoFile)

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Image saved: ${photoFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("CameraX", "Error saving image: ${e.message}", e)
            }
        }
    }




    private fun addImageToGallery(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("CameraX", "Error adding image to gallery", e)
        }
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<SegmentationResult>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        this.preProcessTime = preProcessTime.toString()
        this.inferenceTime = interfaceTime.toString()
        this.postProcessTime = postProcessTime.toString()

        if (results.isNotEmpty()) {
            segmentedBitmap = drawImages(results)
        } else {
            segmentedBitmap = null
        }
    }

    override fun onEmpty() {
        segmentedBitmap = null
    }

    override fun onError(error: String) {
        Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceSegmentation.close()
        cameraExecutor.shutdown()
    }

    private fun checkPermission() = lifecycleScope.launch(Dispatchers.IO) {
        val isGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            // Camera will be started in Compose when PreviewView is available
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map.all { it.value }) {
            // Permissions granted, camera will start in Compose
        } else {
            Toast.makeText(baseContext, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            imageProxy.use {
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            originalBitmap = rotatedBitmap
            instanceSegmentation.invoke(rotatedBitmap)
        }
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}