package com.sawwere.yoloapp.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.sawwere.yoloapp.core.data.dao.AppDao
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.entity.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(
    private val appDao: AppDao,
    private val mediaStoreRepository: MediaStoreRepository
) {

    // Category operations
    suspend fun insertCategory(name: String): Long {
        val category = Category(name = name)
        return appDao.insertCategory(category)
    }

    fun getAllCategories(): Flow<List<Category>> = appDao.getAllCategories()

    fun getAllCategoriesWithFirstPhoto(): Flow<List<CategoryWithPhotos>> =
        appDao.getAllCategoriesWithFirstPhoto()

    suspend fun getCategoryById(categoryId: Long): Category? =
        appDao.getCategoryById(categoryId)

    suspend fun deleteCategory(category: Category) {
        withContext(Dispatchers.IO) {
            // Получаем все фото категории
            val photos = appDao.getPhotosByCategory(category.id).first()

            // Удаляем изображения из MediaStore
            photos.forEach { photo ->
                try {
                    val uri = Uri.parse(photo.imageUri)
                    mediaStoreRepository.deleteImageFromPublicStorage(uri)
                } catch (e: Exception) {
                    // Логируем ошибку, но продолжаем удаление
                }
            }

            // Удаляем категорию из БД (фото удалятся каскадно)
            appDao.deleteCategory(category)
        }
    }

    // Photo operations
    suspend fun insertPhoto(
        categoryId: Long,
        bitmap: Bitmap,
        description: String = ""
    ): Pair<Long, Uri?> {
        return withContext(Dispatchers.IO) {
            try {
                val category = getCategoryById(categoryId)
                    ?: throw Exception("Category not found")

                // Сохраняем изображение в публичное хранилище
                val imageUri = mediaStoreRepository.saveImageToPublicStorage(
                    bitmap = bitmap,
                    categoryName = category.name,
                    description = description
                )

                if (imageUri == null) {
                    throw Exception("Failed to save image to storage")
                }

                // Сохраняем информацию о фото в БД
                val photo = Photo(
                    imageUri = imageUri.toString(),
                    categoryId = categoryId,
                    description = description,
                    fileName = imageUri.lastPathSegment ?: "unknown",
                    fileSize = bitmap.byteCount.toLong()
                )

                val photoId = appDao.insertPhoto(photo)
                Pair(photoId, imageUri)

            } catch (e: Exception) {
                throw Exception("Failed to insert photo: ${e.message}", e)
            }
        }
    }

    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?> =
        appDao.getCategoryWithPhotos(categoryId)

    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>> =
        appDao.getPhotosByCategory(categoryId)

    suspend fun deletePhoto(photo: Photo) {
        withContext(Dispatchers.IO) {
            try {
                // Удаляем файл из MediaStore
                val uri = Uri.parse(photo.imageUri)
                mediaStoreRepository.deleteImageFromPublicStorage(uri)

                // Удаляем запись из БД
                appDao.deletePhoto(photo)
            } catch (e: Exception) {
                throw Exception("Failed to delete photo: ${e.message}", e)
            }
        }
    }

    // Методы для работы с изображениями

    suspend fun loadImageBitmap(imageUri: Uri): Bitmap? =
        mediaStoreRepository.loadImageFromPublicStorage(imageUri)

    suspend fun loadImageThumbnail(imageUri: Uri, size: Int = 400): Bitmap? =
        mediaStoreRepository.loadThumbnail(imageUri, size)

    suspend fun getImagesInCategory(categoryName: String): List<Uri> =
        mediaStoreRepository.getImagesInCategory(categoryName)

    suspend fun getCategoryImageUris(categoryId: Long): List<Uri> {
        return withContext(Dispatchers.IO) {
            val photos = appDao.getPhotosByCategory(categoryId).first()
            photos.mapNotNull { photo ->
                try {
                    Uri.parse(photo.imageUri)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun updatePhotoDescription(photoId: Long, description: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Получаем фото из БД
                val photo = appDao.getPhotoById(photoId) ?: return@withContext false

                // Обновляем описание в MediaStore
                val uri = Uri.parse(photo.imageUri)
                val updatedInMediaStore = mediaStoreRepository.updateImageDescription(uri, description)

                if (updatedInMediaStore) {
                    // Обновляем описание в БД
                    val updatedPhoto = photo.copy(description = description)
                    appDao.updatePhoto(updatedPhoto)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    // Получаем общий размер всех фото в категории
    suspend fun getCategoryTotalSize(categoryId: Long): Long {
        return withContext(Dispatchers.IO) {
            val photos = appDao.getPhotosByCategory(categoryId).first()
            photos.sumOf { it.fileSize }
        }
    }

    // Получаем последнее фото в категории
    suspend fun getLatestPhotoInCategory(categoryId: Long): Photo? {
        return withContext(Dispatchers.IO) {
            val photos = appDao.getPhotosByCategory(categoryId).first()
            photos.maxByOrNull { it.createdAt }
        }
    }
}