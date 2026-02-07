package com.sawwere.yoloapp.ui.main

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: AppRepository) : ViewModel() {

    val allCategories = repository.getAllCategories()
    val allCategoriesWithPhotos = repository.getAllCategoriesWithFirstPhoto()

    private val _currentCategoryImages = MutableStateFlow<List<Uri>>(emptyList())
    val currentCategoryImages: StateFlow<List<Uri>> = _currentCategoryImages

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun getCategoryWithPhotos(categoryId: Long): Flow<CategoryWithPhotos?> =
        repository.getCategoryWithPhotos(categoryId)

    suspend fun addCategory(name: String): Long {
        return try {
            repository.insertCategory(name)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to add category: ${e.message}"
            throw e
        }
    }

    suspend fun addPhotoToCategory(categoryId: Long, bitmap: Bitmap, description: String = ""): Uri? {
        return try {
            val (_, uri) = repository.insertPhoto(categoryId, bitmap, description)
            loadCategoryImages(categoryId)
            uri
        } catch (e: Exception) {
            _errorMessage.value = "Failed to add photo: ${e.message}"
            null
        }
    }

    suspend fun loadCategoryImages(categoryId: Long) {
        viewModelScope.launch {
            try {
                val images = repository.getCategoryImageUris(categoryId)
                _currentCategoryImages.value = images
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load images: ${e.message}"
            }
        }
    }

    suspend fun deletePhoto(photoId: Long, categoryId: Long) {
        viewModelScope.launch {
            try {
                val photo = repository.getPhotoById(photoId) ?: return@launch
                repository.deletePhoto(photo)
                loadCategoryImages(categoryId)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete photo: ${e.message}"
            }
        }
    }

    suspend fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                val category = repository.getCategoryById(categoryId) ?: return@launch
                repository.deleteCategory(category)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete category: ${e.message}"
            }
        }
    }

    suspend fun loadImageBitmap(uri: Uri): Bitmap? {
        return try {
            repository.loadImageBitmap(uri)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load image: ${e.message}"
            null
        }
    }

    suspend fun loadImageThumbnail(uri: Uri): Bitmap? {
        return try {
            repository.loadImageThumbnail(uri)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load thumbnail: ${e.message}"
            null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Получаем форматированный размер категории
    suspend fun getFormattedCategorySize(categoryId: Long): String {
        return try {
            val sizeInBytes = repository.getCategoryTotalSize(categoryId)

            when {
                sizeInBytes < 1024 -> "$sizeInBytes B"
                sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
                else -> String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0))
            }
        } catch (e: Exception) {
            "Unknown size"
        }
    }
}