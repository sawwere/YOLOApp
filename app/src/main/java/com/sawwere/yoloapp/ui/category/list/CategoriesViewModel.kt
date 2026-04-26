package com.sawwere.yoloapp.ui.category.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.domain.category.usecase.DeleteCategory
import com.sawwere.yoloapp.core.domain.category.usecase.GetAllCategories
import com.sawwere.yoloapp.core.domain.category.usecase.InsertCategory
import com.sawwere.yoloapp.ui.category.list.model.CategoryWithCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesListViewModel @Inject constructor(
    private val getAllCategories: GetAllCategories,
    private val insertCategory: InsertCategory,
    private val deleteCategory: DeleteCategory,
) : ViewModel() {

    private val _categoriesWithCount = MutableStateFlow<List<CategoryWithCount>>(emptyList())
    val categoriesWithCount: StateFlow<List<CategoryWithCount>> = _categoriesWithCount.asStateFlow()

    init {
        loadCategoriesWithCount()
    }

    internal fun loadCategoriesWithCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val categoriesWithPhotos = getAllCategories.getAllCategories()
            val categoriesWithCount = categoriesWithPhotos.map { categoryWithPhotos ->
                CategoryWithCount(
                    category = categoryWithPhotos.category,
                    photoCount = categoryWithPhotos.photos.size
                )
            }
            _categoriesWithCount.value = categoriesWithCount
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            insertCategory.insert(name)
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            deleteCategory.deleteCategory(categoryId)
        }
    }
}