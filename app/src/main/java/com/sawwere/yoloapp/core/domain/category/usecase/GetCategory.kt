package com.sawwere.yoloapp.core.domain.category.usecase

import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import kotlinx.coroutines.flow.Flow

interface GetCategory {
    suspend fun getById(categoryId: Long): Category?

    suspend fun getCategoryWithPhotos(cateryId: Long): Flow<CategoryWithPhotos?>
}