package com.sawwere.yoloapp.core.domain.category.usecase

import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos

interface GetAllCategories {
    suspend fun getAllCategories(): List<CategoryWithPhotos>
}