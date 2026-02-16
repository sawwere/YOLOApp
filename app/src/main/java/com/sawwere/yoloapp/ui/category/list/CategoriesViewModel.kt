package com.sawwere.yoloapp.ui.category.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.data.repository.AppRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CategoriesListViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _categoriesWithCount = MutableStateFlow<List<CategoryWithCount>>(emptyList())
    val categoriesWithCount: StateFlow<List<CategoryWithCount>> = _categoriesWithCount.asStateFlow()

    init {
        loadCategoriesWithCount()
    }

    private fun loadCategoriesWithCount() {
        viewModelScope.launch {
            repository.getAllCategories().collect { categories ->
                val counts = categories.map { category ->
                    async { category.id to repository.getPhotoCountInCategory(category.id) }
                }.awaitAll().toMap()
                _categoriesWithCount.value = categories.map { category ->
                    CategoryWithCount(category, counts[category.id] ?: 0)
                }
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(name)
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
        }
    }

    companion object {
        fun provideFactory(repository: AppRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CategoriesListViewModel::class.java)) {
                        return CategoriesListViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}