package com.sawwere.yoloapp.core.domain.category.usecase

import com.sawwere.yoloapp.core.data.entity.Category

interface UpdateCategory {
    suspend fun updateCategory(category: Category)
}