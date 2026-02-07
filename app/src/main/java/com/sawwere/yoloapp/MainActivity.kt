package com.sawwere.yoloapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sawwere.yoloapp.camera.presentation.CameraScreen
import com.sawwere.yoloapp.camera.presentation.CameraScreenViewModel
import com.sawwere.yoloapp.core.config.SaveConfig
import com.sawwere.yoloapp.core.data.AppDatabase
import com.sawwere.yoloapp.core.data.repository.AppRepository
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.image.DrawImages
import com.sawwere.yoloapp.core.image.ImageProcessor
import com.sawwere.yoloapp.core.data.repository.MediaStoreRepository
import com.sawwere.yoloapp.core.system.VibrationComponent
import com.sawwere.yoloapp.ui.main.MainViewModel
import com.sawwere.yoloapp.ui.theme.YOLOAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity(), DetectionComponent.InstanceSegmentationListener {

    private lateinit var detectionComponent: DetectionComponent
    private lateinit var drawImages: DrawImages
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var vibrationComponent: VibrationComponent

    private var camera: Camera? = null
    private var segmentedBitmap: Bitmap? by mutableStateOf(null)
    private var originalBitmap: Bitmap? by mutableStateOf(null)

    private var capturedDetections: List<DetectionComponent.Detection> by mutableStateOf(emptyList())
    private var capturedOriginalBitmap: Bitmap? by mutableStateOf(null)

    private lateinit var vibrator : Vibrator

    private lateinit var viewModel : CameraScreenViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var mediaStoreRepository: MediaStoreRepository
    private lateinit var appRepository: AppRepository
    private var currentCategoryId: Long = 0

    // Для навигации между экранами
    private var showCategoriesScreen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) {
            Log.i("MainActivity", "OpenCV loaded successfully")
        } else {
            Log.e("MainActivity", "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
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

        // Инициализация репозиториев и ViewModel для работы с категориями и сохранением
        initializeRepositoriesAndViewModels()

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
                if (showCategoriesScreen) {
                    CategoriesScreenWrapper(
                        mainViewModel = mainViewModel,
                        onCategorySelected = { categoryId ->
                            currentCategoryId = categoryId
                            showCategoriesScreen = false
                        },
                        onAddCategory = { showAddCategoryDialog = true },
                        onBack = { showCategoriesScreen = false }
                    )
                } else {
                    // Показываем камеру
                    CameraScreen(
                        viewModel = viewModel,
                        segmentedBitmap = segmentedBitmap,
                        onCaptureClick = {
                            if (currentCategoryId == 0L) {
                                // Если категория не выбрана, показываем экран выбора
                                showCategoriesScreen = true
                                Toast.makeText(
                                    this@MainActivity,
                                    "Сначала выберите категорию",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                captureCurrentFrame()
                                vibrationComponent.triggerHapticFeedback()
                            }
                        }
                    )
                }
            }
        }

        checkPermission()
    }

    private fun initializeRepositoriesAndViewModels() {
        // Инициализация репозиториев
        mediaStoreRepository = MediaStoreRepository(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        appRepository = AppRepository(database.appDao(), mediaStoreRepository)

        // Инициализация ViewModel
        mainViewModel = MainViewModel(appRepository)

        // Создаем категорию по умолчанию
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Проверяем, есть ли уже категории
                val categories = appRepository.getAllCategories().first()
                if (categories.isEmpty()) {
                    // Создаем категорию по умолчанию
                    currentCategoryId = appRepository.insertCategory("Обнаруженные объекты")
                } else {
                    // Используем первую категорию
                    currentCategoryId = categories.first().id
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing categories: ${e.message}")
            }
        }
    }


    private suspend fun saveImageWithMetadata(bitmap: Bitmap, description: String): Uri? {
        return try {
            // Получаем категорию для сохранения
            val category = appRepository.getCategoryById(currentCategoryId)
            if (category == null) {
                Log.e("SaveImage", "Category not found: $currentCategoryId")
                return null
            }

            // Сохраняем изображение через MediaStoreRepository
            val uri = mediaStoreRepository.saveImageToPublicStorage(
                bitmap = bitmap,
                categoryName = category.name,
                description = description
            )

            if (uri != null) {
                // Сохраняем информацию о фото в базу данных
                appRepository.insertPhoto(currentCategoryId, bitmap, description)
                Log.d("SaveImage", "Image saved successfully: $uri")
            } else {
                Log.e("SaveImage", "Failed to save image to MediaStore")
            }

            uri
        } catch (e: Exception) {
            Log.e("SaveImage", "Error saving image: ${e.message}", e)
            null
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
        val detectionCount = capturedDetections.size

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

        // Сохраняем в галерею с использованием MediaStoreRepository
        lifecycleScope.launch(Dispatchers.IO) {
            val description = "Обнаружено объектов: $detectionCount"
            val uri = saveImageWithMetadata(bitmapToSave, description)

            withContext(Dispatchers.Main) {
                if (uri != null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Изображение сохранено в категорию",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка сохранения изображения",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // Освобождаем bitmap после сохранения
            bitmapToSave.recycle()
        }

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

        viewModel.clearAllSegments()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("SegmentDebug", "Processing ${capturedDetections.size} detections")

                // Обрабатываем каждый обнаруженный объект
                for ((index, detection) in capturedDetections.withIndex()) {
                    try {
                        Log.d("SegmentDebug", "Processing detection $index")

                        val croppedSegment = extractObjectSegment(capturedOriginalBitmap!!, detection)
                        Log.d("SegmentDebug", "Cropped segment for object $index: ${croppedSegment?.width}x${croppedSegment?.height}")

                        if (croppedSegment != null) {
                            val processedBitmap = processSingleSegment(croppedSegment)
                            Log.d("SegmentDebug", "Processed bitmap for object $index: ${processedBitmap?.width}x${processedBitmap?.height}")

                            if (processedBitmap != null) {
                                withContext(Dispatchers.Main) {
                                    viewModel.addProcessedSegment(processedBitmap)
                                    Log.d("SegmentDebug", "Added segment $index to ViewModel")
                                }

                                // Сохраняем отдельный сегмент как отдельное фото
                                saveIndividualSegment(processedBitmap, index)
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

                withContext(Dispatchers.Main) {
                    Log.d("SegmentDebug", "Final segment count in ViewModel: ${viewModel.processedSegments.size}")
                    viewModel.updateDetectionInfo(capturedDetections.size)

                    if (viewModel.processedSegments.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "Не удалось обработать ни одного сегмента",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Обработано ${viewModel.processedSegments.size} объектов",
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

    private suspend fun saveIndividualSegment(bitmap: Bitmap, index: Int) {
        try {
            val category = appRepository.getCategoryById(currentCategoryId)
            if (category != null) {
                val description = "Сегмент объекта $index из категории ${category.name}"
                saveImageWithMetadata(bitmap, description)
            }
        } catch (e: Exception) {
            Log.e("SaveSegment", "Error saving segment $index: ${e.message}")
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
        segmentedBitmap = if (results.isEmpty() || originalBitmap == null) {
            null
        } else {
            drawImages(
                imageWidth = originalBitmap!!.width,
                imageHeight = originalBitmap!!.height,
                results = results
            )
        }

        // Сохраняем текущие детекции для возможного захвата
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
            // Здесь можно добавить дополнительную обработку сегментов, если нужно
            segmentBitmap
        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error in segment processing: ${e.message}")
            null
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

// Компонент для отображения экрана категорий в Compose
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreenWrapper(
    mainViewModel: MainViewModel,
    onCategorySelected: (Long) -> Unit,
    onAddCategory: () -> Unit,
    onBack: () -> Unit
) {
    val categories by mainViewModel.allCategories.collectAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    // Здесь должен быть ваш компосейбл экрана категорий
    // Для примера я создам простой экран

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выберите категорию") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Добавить категорию"
                )
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
               Text("Нет категорий. Добавьте первую!")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding)
            ) {
                items(categories) { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { onCategorySelected(category.id) },
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.h6
                            )
                            Text(
                                text = "Создано: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(
                                    Date(category.createdAt)
                                )}",
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог добавления категории
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новая категория") },
            text = {
                var categoryName by remember { mutableStateOf("") }

                Column {
                    TextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Название категории") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Здесь должна быть логика добавления категории
                        showAddDialog = false
                    }
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}


var showAddCategoryDialog by mutableStateOf(false)