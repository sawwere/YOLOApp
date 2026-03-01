package com.sawwere.yoloapp.ui.category.detail.usecase

interface RecalculateChecksum {
    suspend operator fun invoke(categoryId: Long): Result<FloatArray>
}