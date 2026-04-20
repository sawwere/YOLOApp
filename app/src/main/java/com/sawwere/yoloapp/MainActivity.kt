package com.sawwere.yoloapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sawwere.yoloapp.core.detection.DetectionComponent
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var detectionComponent: DetectionComponent
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
                                viewModel.captureCurrentFrame()
                            },
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionComponent.close()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
