package com.sawwere.yoloapp.core.domain.service.usecase

import android.net.Uri
import com.sawwere.yoloapp.core.detection.EmbeddingExtractorComponent
import com.sawwere.yoloapp.core.domain.category.exception.EmptyCategoryException
import com.sawwere.yoloapp.core.domain.category.usecase.GetCategory
import com.sawwere.yoloapp.core.domain.category.usecase.UpdateCategory
import com.sawwere.yoloapp.core.domain.photo.PhotoRepository
import com.sawwere.yoloapp.core.domain.repository.MediaStoreRepository
import com.sawwere.yoloapp.ui.category.detail.usecase.RecalculateChecksum
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecalculateChecksumImpl @Inject constructor(
    private val embeddingExtractorComponent: EmbeddingExtractorComponent,
    private val repository: PhotoRepository,
    private val getCategory: GetCategory,
    private val updateCategory: UpdateCategory,
    private val mediaStoreRepository: MediaStoreRepository
) : RecalculateChecksum {

    override suspend operator fun invoke(categoryId: Long): Result<FloatArray> {
        return try {
            val photos = repository.getAllByCategory(categoryId).first()
            if (photos.isEmpty()) {
                return Result.failure(EmptyCategoryException(categoryId))
            }

            val vectors = mutableListOf<FloatArray>()

            for (photo in photos) {
                val uri = Uri.parse(photo.imageUri)
                mediaStoreRepository.loadImageFromPublicStorage(uri)?.let {
                    vectors.add(embeddingExtractorComponent.getEmbedding(it))
                }
            }

            val avgVector = FloatArray(vectors.first().size) { 0f }
            for (i in avgVector.indices) {
                var sum = 0f
                for (vec in vectors) {
                    sum += vec[i]
                }
                avgVector[i] = sum / vectors.size
            }

            val category = getCategory.getById(categoryId)
                ?: throw NoSuchElementException("Catefory with id '$categoryId' not found")
            val updatedCategory = category.copy(checksum = avgVector)
            updateCategory.updateCategory(updatedCategory)
            repository.markAllPhotosAsProcessed(categoryId)

            Result.success(avgVector)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}