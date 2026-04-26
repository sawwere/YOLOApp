package com.sawwere.yoloapp.core.data.repository

import com.sawwere.yoloapp.core.data.dao.CategoryDao
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.domain.category.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
): CategoryRepository {

    override suspend fun insertCategory(name: String): Long {
        return withContext(Dispatchers.IO) {
            val category = Category(name = name)
            categoryDao.insertCategory(category)
        }
    }

    override suspend fun updateCategory(category: Category) {
        return withContext(Dispatchers.IO) {
            categoryDao.updateCategory(category)
        }
    }

    override fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    override suspend fun getCategoryWithPhotos(categoryId: Long): CategoryWithPhotos? =
        categoryDao.getCategoryWithPhotos(categoryId)

    override suspend fun deleteCategory(categoryId: Long) {
        return withContext(Dispatchers.IO) {
            val category = categoryDao.getCategoryById(categoryId) ?: return@withContext
           categoryDao.deleteCategory(category)
        }
    }

    override suspend fun getCategoryById(categoryId: Long): Category? =
        withContext(Dispatchers.IO) {
            categoryDao.getCategoryById(categoryId)
        }

    override suspend fun getPhotoCountInCategory(categoryId: Long): Int =
        withContext(Dispatchers.IO) {
            categoryDao.getPhotoCountInCategory(categoryId)
        }
}