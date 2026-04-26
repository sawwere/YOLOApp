package com.sawwere.yoloapp.core.domain.category.usecase

interface DeleteCategory {
    suspend fun deleteCategory(id: Long): Boolean
}