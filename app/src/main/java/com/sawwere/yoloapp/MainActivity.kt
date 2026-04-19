package com.sawwere.yoloapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sawwere.yoloapp.core.config.CAMERA_SMALL_HEIGHT
import com.sawwere.yoloapp.core.config.CAMERA_SMALL_WIDTH
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.domain.image.ImageUtils.imageProxyToBitmapWithRotation
import com.sawwere.yoloapp.core.domain.image.ImageUtils.scaleRect
import com.sawwere.yoloapp.ui.camera.CameraPermissionScreen
import com.sawwere.yoloapp.ui.camera.CameraScreenViewModel
import com.sawwere.yoloapp.ui.camera.navigation.CAMERA_SCREEN_ROUTE
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenMode
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenNavigation
import com.sawwere.yoloapp.ui.category.detail.CategoryDetailScreen
import com.sawwere.yoloapp.ui.category.detail.navigation.CATEGORY_DETAILS_SCREEN_ROUTE
import com.sawwere.yoloapp.ui.category.detail.navigation.CATEGORY_ID_ARG
import com.sawwere.yoloapp.ui.category.detail.navigation.CategoryDetailsNavigation
import com.sawwere.yoloapp.ui.category.list.CategoriesListScreen
import com.sawwere.yoloapp.ui.category.list.navigation.CATEGORIES_LIST_SCREEN_ROUTE
import com.sawwere.yoloapp.ui.theme.YOLOAppTheme
import dagger.hilt.android.AndroidEntryPoint
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var detectionComponent: DetectionComponent
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private val viewModel: CameraScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        enableEdgeToEdge()
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
                                navController.navigate(
                                    CameraScreenNavigation.passArgs(
                                        categoryId,
                                        CameraScreenMode.ADD
                                ))
                            },
                            onCheckClick = {
                                navController.navigate(
                                    CameraScreenNavigation.passArgs(
                                        categoryId,
                                        CameraScreenMode.CHECK
                                ))
                            }
                        )
                    }
                    composable(
                        route = CAMERA_SCREEN_ROUTE,
                        arguments = listOf(
                            navArgument(CameraScreenNavigation.CATEGORY_ID_ARG) { type = NavType.LongType },
                            navArgument(CameraScreenNavigation.MODE_ARG) { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val categoryId = backStackEntry.arguments?.getLong(CATEGORY_ID_ARG) ?: 0L
                        val mode = backStackEntry.arguments
                            ?.getString(CameraScreenNavigation.MODE_ARG)
                            ?: CameraScreenMode.ADD.value
                        viewModel.drawMode = mode
                        viewModel.categoryId = categoryId
                        CameraPermissionScreen(
                            onBackClick = { navController.popBackStack() },
                            onCaptureClick = {
                                captureCurrentFrame(categoryId)
                            },
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val aspectRatio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            // Preview
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
            // ImageAnalysis (CAMERA_SMALL_HEIGHTxCAMERA_SMALL_WIDTH)
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
            // ImageCapture (высокое разрешение, 4:3)
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectRatio).build())
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer,
                    imageCapture
                )
                viewModel.setupZoomState(camera)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureCurrentFrame(categoryId: Long) {
        if (!::imageCapture.isInitialized) return

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val largeBitmap = imageProxyToBitmapWithRotation(image)
                    image.close()

                    val detections = viewModel.detectedBoxes.value
                    if (detections.isNotEmpty()) {
                        val scaledBoxes = detections.map { detection ->
                            val scaledRect = scaleRect(
                                detection.bbox,
                                CAMERA_SMALL_WIDTH to CAMERA_SMALL_HEIGHT,
                                largeBitmap.width to largeBitmap.height
                            )
                            detection.copy(bbox = scaledRect)
                        }
                        viewModel.onCapture(largeBitmap, categoryId, scaledBoxes)
                    } else {
                        Toast.makeText(this@MainActivity,
                            getString(R.string.no_image), Toast.LENGTH_SHORT).show()
                        largeBitmap.recycle()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    Toast.makeText(this@MainActivity,
                        getString(R.string.capture_error), Toast.LENGTH_SHORT
                    ).show()
                }
            })
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

            detectionComponent.invoke(rotatedBitmap)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
