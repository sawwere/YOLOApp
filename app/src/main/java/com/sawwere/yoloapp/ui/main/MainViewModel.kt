package com.sawwere.yoloapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.entity.Photo
import com.sawwere.yoloapp.core.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(
    private val appRepository: AppRepository
) : ViewModel() {

    val allCategories: Flow<List<Category>> = appRepository.getAllCategories()

    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?> {
        return appRepository.getCategoryWithPhotos(categoryId)
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            appRepository.insertCategory(name)
        }
    }

    fun getPhotosByCategory(categoryId: Long): Flow<List<Photo>> {
        return appRepository.getPhotosByCategory(categoryId)
    }

    suspend fun getCategoryTotalSize(categoryId: Long): String {
        val sizeInBytes = appRepository.getCategoryTotalSize(categoryId)

        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            else -> {
                val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                String.format("%.1f MB", sizeInMB)
            }
        }
    }

    suspend fun getLatestPhotoInCategory(categoryId: Long): Photo? {
        return appRepository.getLatestPhotoInCategory(categoryId)
    }

    suspend fun updatePhotoDescription(photoId: Long, description: String): Boolean {
        return appRepository.updatePhotoDescription(photoId, description)
    }

    suspend fun deletePhoto(photoId: Long): Boolean {
        return appRepository.deletePhoto(photoId)
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            appRepository.deleteCategory(categoryId)
        }
    }
}