package com.sawwere.yoloapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sawwere.yoloapp.camera.presentation.CameraScreen
import com.sawwere.yoloapp.camera.presentation.CameraScreenViewModel
import com.sawwere.yoloapp.core.config.SaveConfig
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.image.DrawImages
import com.sawwere.yoloapp.core.image.ImageProcessor
import com.sawwere.yoloapp.core.repository.MediaStoreRepository
import com.sawwere.yoloapp.core.system.VibrationComponent
import com.sawwere.yoloapp.ui.theme.YOLOAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity(), DetectionComponent.InstanceSegmentationListener {

    private lateinit var detectionComponent: DetectionComponent
    private lateinit var drawImages: DrawImages
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var vibrationComponent: VibrationComponent
    private lateinit var imageProcessor: ImageProcessor

    private var camera: Camera? = null
    private var segmentedBitmap: Bitmap? by mutableStateOf(null)
    private var originalBitmap: Bitmap? by mutableStateOf(null)

    private var capturedDetections: List<DetectionComponent.Detection> by mutableStateOf(emptyList())
    private var capturedOriginalBitmap: Bitmap? by mutableStateOf(null)

    private lateinit var vibrator : Vibrator

    private lateinit var viewModel : CameraScreenViewModel
    private val mediaStoreRepository = MediaStoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) {
            Log.i("MainActivity", "OpenCV loaded successfully")
        } else {
            Log.e("MainActivity", "OpenCV initialization failed!")
            (Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG)).show()
            return
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        enableEdgeToEdge()

        drawImages = DrawImages(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()
        viewModel = CameraScreenViewModel()
        imageProcessor = ImageProcessor()

        detectionComponent = DetectionComponent(
            context = applicationContext,
            modelPath = "yolov8s_float16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        vibrationComponent = VibrationComponent(vibrator)

        setContent {
            YOLOAppTheme {
                CameraScreen(
                    viewModel = this.viewModel,
                    segmentedBitmap = segmentedBitmap,
                    onCaptureClick = {
                        captureCurrentFrame()
                        vibrationComponent.triggerHapticFeedback()
                    }
                )
            }
        }

        checkPermission()
    }

    private fun saveToGallery(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Убедимся, что у нас есть разрешение на запись
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                ) {
                    // Запросить разрешение, если нужно
                    withContext(Dispatchers.Main) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            REQUEST_WRITE_PERMISSION
                        )
                    }
                    // Освобождаем bitmap, если не можем сохранить
                    bitmap.recycle()
                    return@launch
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "capture_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${SaveConfig.folderName}")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                    }
                }

                val resolver = applicationContext.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    try {
                        resolver.openOutputStream(it)?.use { outputStream ->
                            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    contentValues.clear()
                                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                    resolver.update(uri, contentValues, null, null)
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Изображение сохранено в галерею",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Log.d("GallerySave", "Image saved successfully: $uri")
                                }
                            } else {
                                throw IOException("Failed to compress bitmap")
                            }
                        } ?: throw IOException("Failed to open output stream")
                    } catch (e: Exception) {
                        // Удаляем запись если произошла ошибка
                        resolver.delete(uri, null, null)
                        throw e
                    } finally {
                        // Освобождаем bitmap после сохранения
                        bitmap.recycle()
                    }
                } ?: run {
                    bitmap.recycle()
                    throw IOException("Failed to create new MediaStore record")
                }

            } catch (e: Exception) {
                Log.e("GallerySave", "Error saving image: ${e.message}", e)
                // Освобождаем bitmap при ошибке
                bitmap.recycle()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка сохранения: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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
                viewModel.setupZoomState(camera!!)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureCurrentFrame() {
        val original = originalBitmap ?: run {
            Toast.makeText(this, getString(R.string.no_image), Toast.LENGTH_SHORT).show()
            return
        }

        // Сохраняем текущие обнаруженные объекты и оригинальное изображение
        capturedOriginalBitmap = original

        // Создаем комбинированное изображение для сохранения в галерею
        val bitmapToSave = if (segmentedBitmap != null) {
            Bitmap.createBitmap(
                original.width,
                original.height,
                Bitmap.Config.ARGB_8888
            ).apply {
                val canvas = Canvas(this)
                canvas.drawBitmap(original, 0f, 0f, null)
                canvas.drawBitmap(segmentedBitmap!!, 0f, 0f, null)
            }
        } else {
            // Создаем копию оригинального изображения для сохранения
            original.copy(original.config!!, true)
        }

        // Сохраняем в галерею (передаем bitmap и управление им)
        saveToGallery(bitmapToSave)

        // Обрабатываем захваченные сегменты
        processCapturedSegments()
    }

    private fun processCapturedSegments() {
        Log.d("SegmentDebug", "Starting segment processing...")
        Log.d("SegmentDebug", "Captured bitmap: ${capturedOriginalBitmap != null}")
        Log.d("SegmentDebug", "Captured detections: ${capturedDetections.size}")

        if (capturedOriginalBitmap == null || capturedDetections.isEmpty()) {
            Log.d("SegmentDebug", "No captured data to process")
            viewModel.clearAllSegments()
            runOnUiThread {
                Toast.makeText(this, "Нет обнаруженных объектов для обработки", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Очищаем старые сегменты
        viewModel.clearAllSegments()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("SegmentDebug", "Processing ${capturedDetections.size} detections")

                // Обрабатываем каждый обнаруженный объект
                for ((index, detection) in capturedDetections.withIndex()) {
                    try {
                        Log.d("SegmentDebug", "Processing detection $index")

                        // Вырезаем область объекта
                        val croppedSegment = extractObjectSegment(capturedOriginalBitmap!!, detection)
                        Log.d("SegmentDebug", "Cropped segment for object $index: ${croppedSegment?.width}x${croppedSegment?.height}")

                        if (croppedSegment != null) {
                            // Обрабатываем вырезанный сегмент
                            val processedBitmap = processSingleSegment(croppedSegment)
                            Log.d("SegmentDebug", "Processed bitmap for object $index: ${processedBitmap?.width}x${processedBitmap?.height}")

                            if (processedBitmap != null) {
                                withContext(Dispatchers.Main) {
                                    // Добавляем обработанный сегмент в список
                                    viewModel.addProcessedSegment(processedBitmap)
                                    Log.d("SegmentDebug", "Added segment $index to ViewModel")

                                    // Обновляем счетчик найденных объектов
                                    viewModel.updateDetectionInfo(capturedDetections.size)

                                    if (index == 0) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Обработано ${capturedDetections.size} объектов",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Log.w("SegmentDebug", "Processed bitmap is null for object $index")
                            }

                            croppedSegment.recycle()
                        } else {
                            Log.w("SegmentDebug", "Cropped segment is null for object $index")
                            Log.w("SegmentDebug", "BBox: [${detection.bbox.left}, ${detection.bbox.top}, ${detection.bbox.right}, ${detection.bbox.bottom}]")
                            Log.w("SegmentDebug", "Image size: ${capturedOriginalBitmap!!.width}x${capturedOriginalBitmap!!.height}")
                        }
                    } catch (e: Exception) {
                        Log.e("SegmentDebug", "Error processing object $index: ${e.message}", e)
                    }
                }

                // Проверяем, есть ли сегменты в ViewModel
                withContext(Dispatchers.Main) {
                    Log.d("SegmentDebug", "Final segment count in ViewModel: ${viewModel.processedSegments.size}")
                    if (viewModel.processedSegments.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось обработать ни одного сегмента",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SegmentDebug", "Error processing captured segments: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка обработки сегментов: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<DetectionComponent.Detection>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        this.viewModel.updateTimers(
            preProcessTime = preProcessTime,
            inferenceTime = interfaceTime,
            postProcessTime = postProcessTime
        )

        // Обновляем информацию о текущих обнаруженных объектах
        viewModel.updateDetectionInfo(results.size)

        // Создаем сегментированное изображение для отображения в реальном времени
        segmentedBitmap = if (results.isEmpty()) {
            null
        } else {
            drawImages(
                imageWidth = originalBitmap!!.width,
                imageHeight = originalBitmap!!.height,
                results = results
            )
        }

        // Сохраняем текущие детекции для возможного захвата
        // (но обработку будем делать только при нажатии на кнопку)
        capturedDetections = results
    }


    private fun extractObjectSegment(
        originalBitmap: Bitmap,
        detection: DetectionComponent.Detection
    ): Bitmap? {
        return try {
            val boundingBox = detection.bbox

            // Координаты уже в пикселях из DetectionComponent
            val left = boundingBox.left.toInt()
            val top = boundingBox.top.toInt()
            val right = boundingBox.right.toInt()
            val bottom = boundingBox.bottom.toInt()

            Log.d("SegmentExtraction",
                "Extracting segment - BBox: [$left, $top, $right, $bottom], " +
                        "Image: ${originalBitmap.width}x${originalBitmap.height}")

            // Проверяем, что координаты валидны
            if (left >= right || top >= bottom) {
                Log.w("SegmentExtraction", "Invalid bounding box coordinates")
                return null
            }

            // Проверяем границы с небольшим запасом
            val padding = 5
            val clampedLeft = max(left - padding, 0)
            val clampedTop = max(top - padding, 0)
            val clampedRight = min(right + padding, originalBitmap.width)
            val clampedBottom = min(bottom + padding, originalBitmap.height)

            val width = clampedRight - clampedLeft
            val height = clampedBottom - clampedTop

            if (width <= 0 || height <= 0) {
                Log.w("SegmentExtraction", "Invalid dimensions after clamping: $width x $height")
                return null
            }

            // Вырезаем область
            val segment = Bitmap.createBitmap(
                originalBitmap,
                clampedLeft, clampedTop, width, height
            )

            Log.d("SegmentExtraction", "Successfully extracted segment: ${segment.width}x${segment.height}")
            segment
        } catch (e: Exception) {
            Log.e("SegmentExtraction", "Error extracting object segment: ${e.message}", e)
            null
        }
    }
    private fun processSingleSegment(segmentBitmap: Bitmap): Bitmap? {
        return try {
            imageProcessor.processDocumentImageEnhanced(segmentBitmap)
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error in segment processing: ${e.message}")
            return null
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
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private const val REQUEST_WRITE_PERMISSION = 101
    }
}