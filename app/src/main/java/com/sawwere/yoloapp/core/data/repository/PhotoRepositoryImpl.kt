package com.sawwere.yoloapp.core.data.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toFile
import com.sawwere.yoloapp.core.data.dao.PhotoDao
import com.sawwere.yoloapp.core.data.entity.Photo
import com.sawwere.yoloapp.core.domain.photo.PhotoRepository
import com.sawwere.yoloapp.core.domain.category.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PhotoRepositoryImpl @Inject constructor(
    private val photoDao: PhotoDao,
    private val categoryRepository: CategoryRepository,
    private val mediaStoreRepository: MediaStoreRepositoryImpl
): PhotoRepository {
    override suspend fun getAllByCategory(categoryId: Long): Flow<List<Photo>> =
        photoDao.getPhotosByCategory(categoryId)

    override suspend fun getById(id: Long): Photo? {
        return photoDao.getPhotoById(id)
    }

    override suspend fun insert(
        categoryId: Long,
        bitmap: Bitmap,
        description: String
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                val category = categoryRepository.getCategoryById(categoryId)
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

                photoDao.insertPhoto(photo)
                Result.success(imageUri)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun insert(
        categoryId: Long,
        imageUri: Uri,
        description: String
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            val image = mediaStoreRepository.loadImageFromPublicStorage(imageUri)
                ?: return@withContext Result.failure(NoSuchFileException(imageUri.toFile()))
            insert(
                categoryId = categoryId,
                bitmap = image,
                description = description
            )
        }
    }

    override suspend fun delete(photoId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val photo = photoDao.getPhotoById(photoId)
                if (photo != null) {
                    val uri = Uri.parse(photo.imageUri)
                    mediaStoreRepository.deleteImageFromPublicStorage(uri)

                    photoDao.deletePhoto(photo)
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
            photoDao.markAllPhotosAsProcessed(categoryId)
        }
    }
}