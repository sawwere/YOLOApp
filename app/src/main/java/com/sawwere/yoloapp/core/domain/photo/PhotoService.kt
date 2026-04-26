package com.sawwere.yoloapp.core.domain.photo

import android.net.Uri
import com.sawwere.yoloapp.core.domain.repository.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoService(
    private val mediaStoreRepository: MediaStoreRepository,
    private val photoRepository: PhotoRepository
) {
    suspend fun deletePhoto(photoId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val photo = photoRepository.getById(photoId)
                if (photo != null) {
                    val uri = Uri.parse(photo.imageUri)
                    mediaStoreRepository.deleteImageFromPublicStorage(uri)

                    photoRepository.delete(photoId)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        private const val TAG = "PhotoService"
    }
}