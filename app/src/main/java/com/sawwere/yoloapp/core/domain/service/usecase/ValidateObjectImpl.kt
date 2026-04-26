package com.sawwere.yoloapp.core.domain.service.usecase

import android.graphics.Bitmap
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.detection.EmbeddingExtractorComponent
import com.sawwere.yoloapp.ui.camera.usecase.ValidateObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ValidateObjectImpl @Inject constructor(
    private val embeddingExtractorComponent: EmbeddingExtractorComponent
): ValidateObject {
    override fun invoke(image: Bitmap, category: Category): ValidateObject.ValidationResult {
        val embedding = embeddingExtractorComponent.getEmbedding(image)
        val categoryEmbedding = category.checksum
            ?: return ValidateObject.ValidationResult.None()
        return verifySignatures(embedding, categoryEmbedding)
    }

    private fun verifySignatures(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): ValidateObject.ValidationResult {
        val distance = calculateEuclideanDistance(embedding1, embedding2)
        val  confidence = 1.0f - (distance / 2.0f).coerceIn(0f, 1f)
        val isGenuine = distance < CONFIDENCE_THRESHOLD

        if (isGenuine) {
            return ValidateObject.ValidationResult.Genuine(distance, confidence)
        } else {
            return ValidateObject.ValidationResult.Forgery(distance, confidence)
        }
    }

    /**
     * Вычисление евклидова расстояния между двумя эмбеддингами
     */
    private fun calculateEuclideanDistance(emb1: FloatArray, emb2: FloatArray): Float {
        require(emb1.size == emb2.size) { "Embeddings must have same size" }

        var sum = 0.0f
        for (i in emb1.indices) {
            val diff = emb1[i] - emb2[i]
            sum += diff * diff
        }
        return sqrt(sum.toDouble()).toFloat()
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
    }
}