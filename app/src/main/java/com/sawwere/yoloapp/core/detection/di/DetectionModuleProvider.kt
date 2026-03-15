package com.sawwere.yoloapp.core.detection.di

import android.content.Context
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.detection.EmbeddingExtractorComponent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DetectionModuleProvider {
    @Provides
    @Singleton
    fun provideDetectionComponent(
        @ApplicationContext context: Context,
    ): DetectionComponent = DetectionComponent(
        context = context,
        modelPath = "yolov8s_float16.tflite",
        labelPath = null,
    )

    @Provides
    @Singleton
    fun provideEmbeddingExtractorComponent(
        @ApplicationContext context: Context,
    ): EmbeddingExtractorComponent = EmbeddingExtractorComponent(
        context = context,
        modelPath = "siamese_model.tflite"
    )
}