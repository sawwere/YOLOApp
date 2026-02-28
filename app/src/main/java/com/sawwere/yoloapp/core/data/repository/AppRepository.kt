package com.sawwere.yoloapp.core.data.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import com.sawwere.yoloapp.core.data.dao.AppDao
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.entity.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class AppRepository(
    private val appDao: AppDao,
    private val mediaStoreRepository: MediaStoreRepository
) {

    // Category operations
    suspend fun insertCategory(name: String): Long {
        return withContext(Dispatchers.IO) {
            val category = Category(name = name)
            appDao.insertCategory(category)
        }
    }

    fun getAllCategories(): Flow<List<Category>> = appDao.getAllCategories()

    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?> =
        appDao.getCategoryWithPhotos(categoryId)

    suspend fun getCategoryById(categoryId: Long): Category? =
        withContext(Dispatchers.IO) {
            appDao.getCategoryById(categoryId)
        }

    // Photo operations
    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>> =
        appDao.getPhotosByCategory(categoryId)

    suspend fun insertPhoto(
        categoryId: Long,
        bitmap: Bitmap,
        description: String = ""
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

    suspend fun getPhotoCountInCategory(categoryId: Long): Int =
        withContext(Dispatchers.IO) {
            appDao.getPhotoCountInCategory(categoryId)
        }

    suspend fun getCategoryTotalSize(categoryId: Long): Long {
        return withContext(Dispatchers.IO) {
            val photos = getPhotosByCategory(categoryId).first()
            photos.sumOf { it.fileSize }
        }
    }

    suspend fun getLatestPhotoInCategory(categoryId: Long): Photo? {
        return withContext(Dispatchers.IO) {
            val photos = getPhotosByCategory(categoryId).first()
            photos.maxByOrNull { it.createdAt }
        }
    }

    suspend fun deletePhoto(photoId: Long): Boolean {
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

    suspend fun deleteCategory(categoryId: Long): Boolean {
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

    suspend fun recalculateChecksumForCategory(
        categoryId: Long
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        try {
            val photos = appDao.getPhotosByCategory(categoryId).first()
            if (photos.isEmpty()) {
                return@withContext Result.failure(Exception("Нет фотографий в категории"))
            }

            val md = MessageDigest.getInstance("MD5")
            val targetSize = 128
            val vectors = mutableListOf<FloatArray>()

            for (photo in photos) {
                val uri = Uri.parse(photo.imageUri)
                mediaStoreRepository.loadImageAsStream(uri) { stream ->
                    val imageBytes = stream.readBytes()
                    val hash = md.digest(imageBytes) // 16 байт

                    // Преобразуем 16 байт в 128 float путем повторения и нормировки
                    val floatVector = FloatArray(targetSize)
                    for (i in 0 until targetSize) {
                        val b = hash[i % hash.size].toInt() and 0xFF // 0..255
                        floatVector[i] = b / 255.0f // от 0 до 1
                    }
                    vectors.add(floatVector)
                }
            }

            val avgVector = FloatArray(targetSize) { 0f }
            for (i in 0 until targetSize) {
                var sum = 0f
                for (vec in vectors) {
                    sum += vec[i]
                }
                avgVector[i] = sum / vectors.size
            }

            val category = appDao.getCategoryById(categoryId)
                ?: throw Exception("Категория не найдена")
            val updatedCategory = category.copy(checksum = avgVector)
            appDao.updateCategory(updatedCategory)

            Result.success(avgVector)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}