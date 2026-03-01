package com.sawwere.yoloapp.core.domain.service.usecase

import android.net.Uri
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import com.sawwere.yoloapp.core.domain.repository.MediaStoreRepository
import com.sawwere.yoloapp.ui.category.detail.usecase.RecalculateChecksum
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecalculateChecksumImpl @Inject constructor(
    private val repository: AppRepository,
    private val mediaStoreRepository: MediaStoreRepository
) : RecalculateChecksum {

    override suspend operator fun invoke(categoryId: Long): Result<FloatArray> {
        return try {
            val photos = repository.getPhotosByCategory(categoryId).first()
            if (photos.isEmpty()) {
                return Result.failure(Exception("Нет фотографий в категории"))
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

            val category = repository.getCategoryById(categoryId)
                ?: throw Exception("Категория не найдена")
            val updatedCategory = category.copy(checksum = avgVector)
            repository.updateCategory(updatedCategory)

            Result.success(avgVector)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}