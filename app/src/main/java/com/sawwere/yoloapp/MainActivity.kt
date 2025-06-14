package com.sawwere.yoloapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.lifecycle.lifecycleScope
import com.sawwere.yoloapp.camera.presentation.CameraScreen
import com.sawwere.yoloapp.ui.theme.YOLOAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), DetectionComponent.InstanceSegmentationListener {

    private lateinit var detectionComponent: DetectionComponent
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
    private var minZoomRatio by mutableFloatStateOf(1f)
    private var maxZoomRatio by mutableFloatStateOf(1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        drawImages = DrawImages(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()

        detectionComponent = DetectionComponent(
            context = applicationContext,
            //modelPath = "yolo11n-seg_float16.tflite",
            modelPath = "yolov8s_float16.tflite",
            //modelPath = "model_fp16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        setContent {
            YOLOAppTheme {
                CameraScreen(
                    preProcessTime = preProcessTime,
                    inferenceTime = inferenceTime,
                    postProcessTime = postProcessTime,
                    segmentedBitmap = segmentedBitmap,
                    zoomProgress = zoomProgress,
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    onZoomChanged = { newProgress ->
                        zoomProgress = newProgress
                        updateCameraZoom()
                    },
                    onZoomGesture = { scaleFactor ->
                        handlePinchZoom(scaleFactor)
                    },
                    onCaptureClick = {
                        saveCombinedImage()
                    }
                )
            }
        }

        checkPermission()
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

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val aspectRatio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            aspectRatio
                        ).build()
                )
                .build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            aspectRatio
                        ).build()
                )
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
                minZoomRatio = it.minZoomRatio
                maxZoomRatio = it.maxZoomRatio
                zoomProgress = calculateZoomProgress(it.zoomRatio)
            }
        }
    }

    private fun calculateZoomProgress(zoomRatio: Float): Float {
        return ((zoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)) * 10f
    }

    private fun updateCameraZoom() {
        camera?.let { cam ->
            val newZoomRatio = minZoomRatio + (zoomProgress / 10f) * (maxZoomRatio - minZoomRatio)
            cam.cameraControl.setZoomRatio(newZoomRatio)
        }
    }

    private fun handlePinchZoom(scaleFactor: Float) {
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value ?: return
            val currentZoom = zoomState.zoomRatio
            val newZoom = currentZoom * scaleFactor

            // Ограничиваем зум минимальным/максимальным значением
            val clampedZoom = newZoom.coerceIn(minZoomRatio, maxZoomRatio)

            // Обновляем состояние зума
            cam.cameraControl.setZoomRatio(clampedZoom)
            zoomProgress = calculateZoomProgress(clampedZoom)
        }
    }

    private fun saveCombinedImage() {
        val original = originalBitmap ?: run {
            Toast.makeText(this, getString(R.string.no_image), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmapToSave = if (segmentedBitmap != null) {
                    Bitmap.createBitmap(
                        original.width,
                        original.height,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        val canvas = android.graphics.Canvas(this)
                        canvas.drawBitmap(original, 0f, 0f, null)
                        canvas.drawBitmap(segmentedBitmap!!, 0f, 0f, null)
                    }
                } else {
                    original
                }

                saveBitmapToMediaStore(bitmapToSave)
            } catch (e: Exception) {
                Log.e("CameraX", "Error saving image: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.error_saving, e.message ?: "Unknown error"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap) {
        val contentResolver = applicationContext.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "combined_image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YOLOApp")
            } else {
                @Suppress("DEPRECATION")
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(directory, "/YOLOApp")
                if (!file.exists()) file.mkdirs()
                put(MediaStore.Images.Media.DATA, file.absolutePath + "/combined_image_${System.currentTimeMillis()}.jpg")
            }
        }

        try {
            val uri = contentResolver.insert(collection, contentValues) ?: throw IOException("Failed to create MediaStore entry")
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)) {
                    throw IOException("Failed to compress bitmap")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.image_saved),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e("CameraX", "Error saving to MediaStore", e)
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Error saving image: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

//    override fun onDetect(
//        interfaceTime: Long,
//        results: List<SegmentationResult>,
//        preProcessTime: Long,
//        postProcessTime: Long
//    ) {
//        this.preProcessTime = preProcessTime.toString()
//        this.inferenceTime = interfaceTime.toString()
//        this.postProcessTime = postProcessTime.toString()
//
//        segmentedBitmap = if (results.isNotEmpty()) {
//            drawImages(results)
//        } else {
//            null
//        }
//    }

    val boxPaint = Paint().apply {
        color = Color.valueOf(1.0f, 0f, 0f).toArgb()
        strokeWidth = 2F
        style = Paint.Style.STROKE
    }

    val labelPaint = Paint().apply {
        color = Color.valueOf(0.0f, 0f, 0f).toArgb()
        strokeWidth = 2F
        style = Paint.Style.STROKE
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<DetectionComponent.Detection>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        this.preProcessTime = preProcessTime.toString()
        this.inferenceTime = interfaceTime.toString()
        this.postProcessTime = postProcessTime.toString()

        segmentedBitmap = if (results.isEmpty()) {
            null
        } else {
            val combined = Bitmap.createBitmap(originalBitmap!!.width, originalBitmap!!.height, Bitmap.Config.ARGB_8888)
            results.forEach { detection ->
                combined.applyCanvas {
                    drawRect(detection.bbox, boxPaint)
                    drawText(detection.confidence.toString(), detection.bbox.left, detection.bbox.top, labelPaint)
                }
            }
            combined
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
        detectionComponent.close()
        cameraExecutor.shutdown()
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
            detectionComponent.invoke(rotatedBitmap)
        }
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}