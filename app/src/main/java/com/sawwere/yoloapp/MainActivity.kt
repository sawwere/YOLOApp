package com.sawwere.yoloapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import com.sawwere.yoloapp.core.image.DrawImages
import com.sawwere.yoloapp.core.image.ImageUtils
import com.sawwere.yoloapp.ui.camera.CameraScreen
import com.sawwere.yoloapp.ui.camera.CameraScreenViewModel
import com.sawwere.yoloapp.ui.camera.navigation.CAMERA_SCREEN_ROUTE
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenNavigation
import com.sawwere.yoloapp.ui.category.detail.CategoryDetailScreen
import com.sawwere.yoloapp.ui.category.detail.navigation.CATEGORY_DETAILS_SCREEN_ROUTE
import com.sawwere.yoloapp.ui.category.detail.navigation.CATEGORY_ID_ARG
import com.sawwere.yoloapp.ui.category.detail.navigation.CategoryDetailsNavigation
import com.sawwere.yoloapp.ui.category.list.CategoriesListScreen
import com.sawwere.yoloapp.ui.category.list.navigation.CATEGORIES_LIST_SCREEN_ROUTE
import com.sawwere.yoloapp.ui.theme.YOLOAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), DetectionComponent.InstanceSegmentationListener {
    // Camera components
    private lateinit var detectionComponent: DetectionComponent
    private lateinit var drawImages: DrawImages
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var segmentedBitmap: Bitmap? by mutableStateOf(null)
    private var originalBitmap: Bitmap? by mutableStateOf(null)
    private lateinit var viewModel: CameraScreenViewModel

    // Repositories
    @Inject
    lateinit var appRepository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) {
            Log.i("MainActivity", "OpenCV loaded successfully")
        } else {
            Log.e("MainActivity", "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
            return
        }

        enableEdgeToEdge()

        drawImages = DrawImages(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()

        viewModel = CameraScreenViewModel(
            appRepository,
        )


        detectionComponent = DetectionComponent(
            context = applicationContext,
            modelPath = "yolov8s_float16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        setContent {
            YOLOAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = CATEGORIES_LIST_SCREEN_ROUTE
                ) {
                    composable(CATEGORIES_LIST_SCREEN_ROUTE) {
                        CategoriesListScreen(
                            onCategoryClick = { categoryId ->
                                navController.navigate(CategoryDetailsNavigation.passId(categoryId))
                            }
                        )
                    }
                    composable(
                        route = CATEGORY_DETAILS_SCREEN_ROUTE,
                        arguments = listOf(navArgument(CATEGORY_ID_ARG) { type = NavType.LongType })
                    ) { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getLong(CATEGORY_ID_ARG) ?: 0L
                        CategoryDetailScreen(
                            onBackClick = { navController.popBackStack() },
                            onAddPhotoClick = {
                                navController.navigate(CameraScreenNavigation.passId(categoryId))
                            },
                            onCheckClick = {
                                Toast.makeText(this@MainActivity, "Запуск проверки...", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    composable(
                        route = CAMERA_SCREEN_ROUTE,
                        arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
                        CameraScreen(
                            onBackClick = { navController.popBackStack() },
                            onCaptureClick = {
                                captureCurrentFrame(categoryId)
                            },
                            viewModel = viewModel,
                            segmentedBitmap = segmentedBitmap
                        )
                    }
                }
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
                viewModel.setupZoomState(camera!!)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureCurrentFrame(categoryId: Long) {
        val original = originalBitmap ?: run {
            Toast.makeText(this, getString(R.string.no_image), Toast.LENGTH_SHORT).show()
            return
        }

        // Создаем комбинированное изображение для сохранения в галерею
        val bitmapToSave = original.copy(original.config!!, true)
        viewModel.onCapture(bitmapToSave, categoryId)
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
        viewModel.updateDetections(results)

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
        )
    }
}
