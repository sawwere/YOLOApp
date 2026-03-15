package com.sawwere.yoloapp.ui.camera.usecase

import android.graphics.Bitmap
import com.sawwere.yoloapp.core.data.entity.Category

interface ValidateObject {
    operator fun invoke(image: Bitmap, category: Category): ValidationResult

    sealed class ValidationResult(
        val distance: Float,
        val confidence: Float
    ) {
      class Genuine(distance: Float, confidence: Float) : ValidationResult(
          distance, confidence
      )

        class Forgery(distance: Float, confidence: Float): ValidationResult(
            distance, confidence
        )

        class None(): ValidationResult(
            0f, 0f
        )
    }
}