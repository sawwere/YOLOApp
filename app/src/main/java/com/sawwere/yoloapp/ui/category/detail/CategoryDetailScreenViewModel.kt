package com.sawwere.yoloapp.ui.category.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.domain.photo.PhotoRepository
import com.sawwere.yoloapp.core.domain.category.CategoryRepository
import com.sawwere.yoloapp.ui.category.detail.navigation.CATEGORY_ID_ARG
import com.sawwere.yoloapp.ui.category.detail.usecase.RecalculateChecksum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


sealed class RecalculateState {
    object Idle : RecalculateState()
    object InProgress : RecalculateState()
    data class Success(val checksum: FloatArray) : RecalculateState()
    data class Error(val message: String) : RecalculateState()
}

data class CategoryDetailScreenUIState(
    val isFabMenuExpanded:Boolean = false,
    val recalculateState: RecalculateState = RecalculateState.Idle
)

@HiltViewModel
class CategoryDetailScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PhotoRepository,
    private val categoryRepository: CategoryRepository,
    private val recalculateChecksum: RecalculateChecksum
) : ViewModel() {
    private val categoryId: Long = savedStateHandle[CATEGORY_ID_ARG]
        ?: throw IllegalArgumentException("$CATEGORY_ID_ARG argument missing")


    private val _categoryWithPhotos = MutableStateFlow<CategoryWithPhotos?>(null)
    val categoryWithPhotos: StateFlow<CategoryWithPhotos?> = _categoryWithPhotos.asStateFlow()

    init {
        loadCategoryWithPhotos()
    }

    private val _uiState = MutableStateFlow(CategoryDetailScreenUIState())
    val uiState: StateFlow<CategoryDetailScreenUIState> = _uiState.asStateFlow()

    private fun loadCategoryWithPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = categoryRepository.getCategoryWithPhotos(categoryId)
            _categoryWithPhotos.value = data
        }
    }

    fun toggleFabMenu() {
        _uiState.update { currentState ->
            currentState.copy(
                isFabMenuExpanded = !currentState.isFabMenuExpanded
            )
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            val success = repository.delete(photoId)
            if (success) {
                loadCategoryWithPhotos()
            }
        }
    }

    fun addPhoto(photoUri: Uri) {
        viewModelScope.launch {
            val result = addPhotoFromUri(photoUri)
            loadCategoryWithPhotos()
        }
    }

    private suspend fun addPhotoFromUri(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = repository.insert(categoryId, uri, "Imported from the gallery")
                result.isSuccess
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun updateRecalculateState(value: RecalculateState) {
        _uiState.update { currentState ->
            currentState.copy(
                recalculateState = value
            )
        }
    }

    fun recalculateChecksum() {
        viewModelScope.launch {
            updateRecalculateState(RecalculateState.InProgress)
            val result = recalculateChecksum(categoryId)
            updateRecalculateState(
                when {
                    result.isSuccess -> RecalculateState.Success(result.getOrNull()!!)
                    else -> RecalculateState.Error(result.exceptionOrNull()?.message
                        ?: "Unknown error")
                }
            )
            // Через 2 секунды сбрасываем в Idle
            delay(2000)
            updateRecalculateState( RecalculateState.Idle)
        }
    }
}