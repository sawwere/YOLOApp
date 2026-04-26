package com.sawwere.yoloapp.core.domain.category.usecase

import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos

interface GetCategory {
    suspend fun getById(categoryId: Long): Category?

    suspend fun getCategoryWithPhotos(cateryId: Long): CategoryWithPhotos?
}