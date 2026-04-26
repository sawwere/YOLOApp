package com.sawwere.yoloapp.core.domain.di

import android.content.Context
import com.sawwere.yoloapp.core.data.dao.PhotoDao
import com.sawwere.yoloapp.core.domain.image.DrawImages
import com.sawwere.yoloapp.core.domain.image.ImageProcessor
import com.sawwere.yoloapp.core.domain.photo.PhotoRepository
import com.sawwere.yoloapp.core.domain.category.CategoryRepository
import com.sawwere.yoloapp.core.domain.repository.MediaStoreRepository
import com.sawwere.yoloapp.core.domain.category.CategoryService
import com.sawwere.yoloapp.core.domain.photo.PhotoService
import com.sawwere.yoloapp.core.domain.category.usecase.DeleteCategory
import com.sawwere.yoloapp.core.domain.category.usecase.GetAllCategories
import com.sawwere.yoloapp.core.domain.category.usecase.GetCategory
import com.sawwere.yoloapp.core.domain.category.usecase.InsertCategory
import com.sawwere.yoloapp.core.domain.category.usecase.UpdateCategory
import com.sawwere.yoloapp.core.domain.service.usecase.RecalculateChecksumImpl
import com.sawwere.yoloapp.core.domain.service.usecase.ValidateObjectImpl
import com.sawwere.yoloapp.ui.camera.usecase.ValidateObject
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

    @Binds
    abstract fun bindValidateObject(
        impl: ValidateObjectImpl
    ): ValidateObject

    @Binds
    abstract fun bindDeleteCategory(
        impl: CategoryService
    ): DeleteCategory

    @Binds
    abstract fun bindInsertCategory(
        impl: CategoryService
    ): InsertCategory

    @Binds
    abstract fun bindGetAllCategories(
        impl: CategoryService
    ): GetAllCategories

    @Binds
    abstract fun bindUpdateCategory(
        impl: CategoryService
    ): UpdateCategory

    @Binds
    abstract fun bindGetCategory(
        impl: CategoryService
    ): GetCategory
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

    @Provides
    fun provideCategoryService(
        categoryRepository: CategoryRepository,
        photoService: PhotoService,
        photoDao: PhotoDao
    ): CategoryService = CategoryService(
        categoryRepository,
        photoService = photoService,
        photoDao = photoDao,
    )

    @Provides
    fun providePhotoService(
        mediaStoreRepository: MediaStoreRepository,
        photoRepository: PhotoRepository
    ): PhotoService = PhotoService(
        mediaStoreRepository = mediaStoreRepository,
        photoRepository = photoRepository
    )
}