package com.sawwere.yoloapp.ui.category.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.data.entity.CategoryWithPhotos
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import com.sawwere.yoloapp.ui.category.detail.navigation.CATEGORY_ID_ARG
import com.sawwere.yoloapp.ui.category.detail.usecase.RecalculateChecksum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed class RecalculateState {
    object Idle : RecalculateState()
    object InProgress : RecalculateState()
    data class Success(val checksum: FloatArray) : RecalculateState()
    data class Error(val message: String) : RecalculateState()
}

@HiltViewModel
class CategoryDetailScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppRepository,
    private val recalculateChecksum: RecalculateChecksum
) : ViewModel() {
    private val categoryId: Long = savedStateHandle[CATEGORY_ID_ARG]
        ?: throw IllegalArgumentException("$CATEGORY_ID_ARG argument missing")


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

    private val _recalculateState = MutableStateFlow<RecalculateState>(RecalculateState.Idle)
    val recalculateState: StateFlow<RecalculateState> = _recalculateState

    fun recalculateChecksum() {
        viewModelScope.launch {
            _recalculateState.value = RecalculateState.InProgress
            val result = recalculateChecksum(categoryId)
            _recalculateState.value = when {
                result.isSuccess -> RecalculateState.Success(result.getOrNull()!!)
                else -> RecalculateState.Error(result.exceptionOrNull()?.message
                    ?: "Неизвестная ошибка")
            }
            // Через 2 секунды сбрасываем в Idle
            delay(2000)
            _recalculateState.value = RecalculateState.Idle
        }
    }
}