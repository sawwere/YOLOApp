package com.sawwere.yoloapp.ui.category.list

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CategoriesViewModel : ViewModel() {

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _newCategoryName = MutableStateFlow("")
    val newCategoryName: StateFlow<String> = _newCategoryName

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog

    private val _categoryToDelete = MutableStateFlow<com.sawwere.yoloapp.core.data.entity.Category?>(null)
    val categoryToDelete: StateFlow<com.sawwere.yoloapp.core.data.entity.Category?> = _categoryToDelete

    fun showAddDialog() {
        _showAddDialog.value = true
        _newCategoryName.value = "" // Сбрасываем имя при открытии
    }

    fun hideAddDialog() {
        _showAddDialog.value = false
    }

    fun updateCategoryName(name: String) {
        _newCategoryName.value = name
    }

    fun showDeleteDialog(category: com.sawwere.yoloapp.core.data.entity.Category) {
        _categoryToDelete.value = category
        _showDeleteDialog.value = true
    }

    fun hideDeleteDialog() {
        _showDeleteDialog.value = false
        _categoryToDelete.value = null
    }

    fun getCurrentCategoryName(): String = _newCategoryName.value
}