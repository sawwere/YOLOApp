package com.sawwere.yoloapp.core.domain.di

import android.content.Context
import com.sawwere.yoloapp.core.domain.image.DrawImages
import com.sawwere.yoloapp.core.domain.image.ImageProcessor
import com.sawwere.yoloapp.core.domain.service.usecase.RecalculateChecksumImpl
import com.sawwere.yoloapp.ui.category.detail.usecase.RecalculateChecksum
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    abstract fun bindRecalculateChecksum(
        impl: RecalculateChecksumImpl
    ): RecalculateChecksum
}

@Module
@InstallIn(SingletonComponent::class)
class DomainModuleProvider {

    @Provides
    fun provideImageProcessor(): ImageProcessor = ImageProcessor()

    @Provides
    fun provideDrawImages(
        @ApplicationContext context: Context
    ): DrawImages = DrawImages(context)
}