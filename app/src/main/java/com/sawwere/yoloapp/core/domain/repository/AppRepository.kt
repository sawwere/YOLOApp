package com.sawwere.yoloapp.core.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.entity.Photo
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    // Category operations
    suspend fun insertCategory(name: String): Long

    suspend fun updateCategory(category: Category)

    fun getAllCategories(): Flow<List<Category>>

    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?>

    suspend fun getCategoryById(categoryId: Long): Category?

    suspend fun deleteCategory(categoryId: Long): Boolean

    // Photo operations
    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>>

    suspend fun insertPhoto(
        categoryId: Long,
        bitmap: Bitmap,
        description: String = ""
    ): Result<Uri>

    suspend fun getPhotoCountInCategory(categoryId: Long): Int

    suspend fun getLatestPhotoInCategory(categoryId: Long): Photo?

    suspend fun deletePhoto(photoId: Long): Boolean

    suspend fun markAllPhotosAsProcessed(categoryId: Long)

}