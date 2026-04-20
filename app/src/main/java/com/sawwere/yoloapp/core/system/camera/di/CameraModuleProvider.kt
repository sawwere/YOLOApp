package com.sawwere.yoloapp.core.system.camera.di

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.sawwere.yoloapp.core.config.aspectRatio
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.system.camera.ImageAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
class CameraModuleProvider {

    @Provides
    @Singleton
    fun provideImageAnalyzer(
        detectionComponent: DetectionComponent
    ): ImageAnalyzer = ImageAnalyzer(detectionComponent)

    @Provides
    @Singleton
    @Named("ImageAnalyzerExecutor")
    fun provideImageAnalyzerExecutorService(): ExecutorService = Executors.newSingleThreadExecutor()

    @Provides
    @Singleton
    fun provideImageAnalysis(
        @Named("ImageAnalyzerExecutor") cameraExecutor: ExecutorService,
        imageAnalyzer: ImageAnalyzer
    ): ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectRatio)
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { it.setAnalyzer(cameraExecutor, imageAnalyzer) }

    @Provides
    @Singleton
    fun provideImageCapture(): ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectRatio)
                .build()
        )
        .build()
}
