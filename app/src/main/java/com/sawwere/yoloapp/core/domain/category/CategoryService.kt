package com.sawwere.yoloapp.core.domain.category

import com.sawwere.yoloapp.core.data.dao.PhotoDao
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.domain.category.usecase.DeleteCategory
import com.sawwere.yoloapp.core.domain.category.usecase.GetAllCategories
import com.sawwere.yoloapp.core.domain.category.usecase.GetCategory
import com.sawwere.yoloapp.core.domain.category.usecase.InsertCategory
import com.sawwere.yoloapp.core.domain.category.usecase.UpdateCategory
import com.sawwere.yoloapp.core.domain.photo.PhotoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val photoService: PhotoService,
    private val photoDao: PhotoDao
): DeleteCategory,
    InsertCategory, GetAllCategories,
    GetCategory, UpdateCategory {
    override suspend fun deleteCategory(id: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val category = categoryRepository.getCategoryById(id)
                if (category != null) {
                    val photos = photoDao.getPhotosByCategory(id).first()

                    photos.forEach { photo ->
                        photoService.deletePhoto(photo.id)
                    }

                    categoryRepository.deleteCategory(id)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun insert(name: String): Long {
        return categoryRepository.insertCategory(name)
    }

    override suspend fun getAllCategories(): List<CategoryWithPhotos> = withContext(Dispatchers.IO) {
        val categories = categoryRepository.getAllCategories().first()
        categories.mapNotNull { category ->
            categoryRepository.getCategoryWithPhotos(category.id)
        }
    }

    override suspend fun getById(categoryId: Long): Category? {
        return categoryRepository.getCategoryById(categoryId)
    }

    override suspend fun updateCategory(category: Category) {
        return categoryRepository.updateCategory(category)
    }

    override suspend fun getCategoryWithPhotos(cateryId: Long): CategoryWithPhotos? {
        return categoryRepository.getCategoryWithPhotos(cateryId)
    }
}