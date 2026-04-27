package com.sawwere.yoloapp.core.domain.category

import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun insertCategory(name: String): Long

    suspend fun updateCategory(category: Category)

    fun getAllCategories(): Flow<List<Category>>

    suspend fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?>

    suspend fun getCategoryById(categoryId: Long): Category?

    suspend fun deleteCategory(categoryId: Long)

    suspend fun getPhotoCountInCategory(categoryId: Long): Int
}