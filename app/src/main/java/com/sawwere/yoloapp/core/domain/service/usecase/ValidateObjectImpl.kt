package com.sawwere.yoloapp.core.domain.service.usecase

import android.graphics.Bitmap
import com.sawwere.yoloapp.ui.camera.usecase.ValidateObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidateObjectImpl @Inject constructor(): ValidateObject {
    override fun invoke(image: Bitmap): ValidateObject.ValidationResult {
        return ValidateObject.ValidationResult.SUCCESS
    }
}