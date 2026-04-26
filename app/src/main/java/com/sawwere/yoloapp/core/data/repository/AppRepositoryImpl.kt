package com.sawwere.yoloapp.core.data.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toFile
import com.sawwere.yoloapp.core.data.dao.AppDao
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.entity.Photo
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AppRepositoryImpl @Inject constructor(
    private val appDao: AppDao,
    private val mediaStoreRepository: MediaStoreRepositoryImpl
): AppRepository {

    // Category operations
    override suspend fun insertCategory(name: String): Long {
        return withContext(Dispatchers.IO) {
            val category = Category(name = name)
            appDao.insertCategory(category)
        }
    }

    override suspend fun updateCategory(category: Category) {
        return withContext(Dispatchers.IO) {
            appDao.updateCategory(category)
        }
    }

    override fun getAllCategories(): Flow<List<Category>> = appDao.getAllCategories()

    override fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?> =
        appDao.getCategoryWithPhotos(categoryId)

    override suspend fun deleteCategory(categoryId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val category = appDao.getCategoryById(categoryId)
                if (category != null) {
                    val photos = appDao.getPhotosByCategory(categoryId).first()

                    photos.forEach { photo ->
                        try {
                            val uri = Uri.parse(photo.imageUri)
                            mediaStoreRepository.deleteImageFromPublicStorage(uri)
                        } catch (e: Exception) {
                            // Логируем ошибку, но продолжаем удаление
                        }
                    }

                    appDao.deleteCategory(category)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun getCategoryById(categoryId: Long): Category? =
        withContext(Dispatchers.IO) {
            appDao.getCategoryById(categoryId)
        }

    // Photo operations
    override suspend fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>> =
        appDao.getPhotosByCategory(categoryId)

    override suspend fun insertPhoto(
        categoryId: Long,
        bitmap: Bitmap,
        description: String
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                val category = getCategoryById(categoryId)
                    ?: return@withContext Result.failure(Exception("Category not found"))

                val imageUri = mediaStoreRepository.saveImageToPublicStorage(
                    bitmap = bitmap,
                    categoryName = category.name,
                    description = description
                ) ?: return@withContext Result.failure(Exception("Failed to save image"))

                val photo = Photo(
                    imageUri = imageUri.toString(),
                    categoryId = categoryId,
                    description = description,
                    fileName = imageUri.lastPathSegment ?: "unknown",
                    fileSize = bitmap.byteCount.toLong()
                )

                appDao.insertPhoto(photo)
                Result.success(imageUri)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun insertPhoto(
        categoryId: Long,
        imageUri: Uri,
        description: String
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            val image = mediaStoreRepository.loadImageFromPublicStorage(imageUri)
                ?: return@withContext Result.failure(NoSuchFileException(imageUri.toFile()))
            insertPhoto(
                categoryId = categoryId,
                bitmap = image,
                description = description
            )
        }
    }

    override suspend fun getPhotoCountInCategory(categoryId: Long): Int =
        withContext(Dispatchers.IO) {
            appDao.getPhotoCountInCategory(categoryId)
        }

    override suspend fun getLatestPhotoInCategory(categoryId: Long): Photo? {
        return withContext(Dispatchers.IO) {
            val photos = getPhotosByCategory(categoryId).first()
            photos.maxByOrNull { it.createdAt }
        }
    }

    override suspend fun deletePhoto(photoId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val photo = appDao.getPhotoById(photoId)
                if (photo != null) {
                    val uri = Uri.parse(photo.imageUri)
                    mediaStoreRepository.deleteImageFromPublicStorage(uri)

                    appDao.deletePhoto(photo)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun markAllPhotosAsProcessed(categoryId: Long) {
        return withContext(Dispatchers.IO) {
            appDao.markAllPhotosAsProcessed(categoryId)
        }
    }
}