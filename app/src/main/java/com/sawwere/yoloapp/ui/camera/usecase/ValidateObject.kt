package com.sawwere.yoloapp.ui.camera.usecase

import android.graphics.Bitmap

interface ValidateObject {
    operator fun invoke(image: Bitmap): ValidationResult

    enum class ValidationResult {
        SUCCESS,
        FORGERY
    }
}