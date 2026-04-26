package com.sawwere.yoloapp.core.domain.photo

import android.graphics.Bitmap
import android.net.Uri
import com.sawwere.yoloapp.core.data.entity.Photo
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    suspend fun getAllByCategory(categoryId: Long): Flow<List<Photo>>

    suspend fun getById(id: Long): Photo?

    suspend fun insert(
        categoryId: Long,
        bitmap: Bitmap,
        description: String = ""
    ): Result<Uri>

    suspend fun insert(
        categoryId: Long,
        imageUri: Uri,
        description: String = "Imported"
    ): Result<Uri>

    suspend fun delete(photoId: Long): Boolean

    suspend fun markAllPhotosAsProcessed(categoryId: Long)

}