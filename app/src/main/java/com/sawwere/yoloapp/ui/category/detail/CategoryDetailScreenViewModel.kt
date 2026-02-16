package com.sawwere.yoloapp.ui.category.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoryDetailScreenViewModel(
    private val categoryId: Long,
    private val repository: AppRepository
) : ViewModel() {

    private val _categoryWithPhotos = MutableStateFlow<CategoryWithPhotos?>(null)
    val categoryWithPhotos: StateFlow<CategoryWithPhotos?> = _categoryWithPhotos.asStateFlow()

    init {
        loadCategoryWithPhotos()
    }

    private fun loadCategoryWithPhotos() {
        viewModelScope.launch {
            repository.getCategoryWithPhotos(categoryId).collect { data ->
                _categoryWithPhotos.value = data
            }
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            val success = repository.deletePhoto(photoId)
            if (success) {
                loadCategoryWithPhotos()
            }
        }
    }

    companion object {
        fun provideFactory(
            categoryId: Long,
            repository: AppRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(CategoryDetailScreenViewModel::class.java)) {
                    return CategoryDetailScreenViewModel(categoryId, repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}