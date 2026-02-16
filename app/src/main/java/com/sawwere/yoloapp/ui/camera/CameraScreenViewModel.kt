package com.sawwere.yoloapp.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Camera
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraScreenViewModel {
    private lateinit var camera: Camera

    private val _preProcessTime = MutableStateFlow(0L)
    val preProcessTime = _preProcessTime.asStateFlow()

    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime = _inferenceTime.asStateFlow()

    private val _postProcessTime = MutableStateFlow(0L)
    val postProcessTime = _postProcessTime.asStateFlow()

    private val _zoomProgress = MutableStateFlow(0f)
    val zoomProgress = _zoomProgress.asStateFlow()

    // Состояние для режима отладки
    private val _debugMode = MutableStateFlow(false)
    val debugMode = _debugMode.asStateFlow()

    // Список обработанных сегментов
    private val _processedSegments = mutableStateOf<List<Bitmap>>(emptyList())
    val processedSegments: List<Bitmap> get() = _processedSegments.value

    // Текущий индекс отображаемого сегмента
    private val _currentSegmentIndex = mutableStateOf(0)
    val currentSegmentIndex: Int get() = _currentSegmentIndex.value

    // Количество найденных объектов
    private val _detectedObjectsCount = MutableStateFlow(0)
    val detectedObjectsCount = _detectedObjectsCount.asStateFlow()

    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f

    fun updateTimers(
        preProcessTime: Long,
        inferenceTime: Long,
        postProcessTime: Long,
    ) {
        _inferenceTime.update { inferenceTime }
        _preProcessTime.update { preProcessTime }
        _postProcessTime.update { postProcessTime }
    }

    fun toggleDebugMode() {
        _debugMode.update { !it }
    }

    fun addProcessedSegment(bitmap: Bitmap) {
        Log.d("ViewModel", "Adding segment. Current count: ${_processedSegments.value.size}")
        // Создаем новый список с добавленным элементом
        _processedSegments.value += bitmap
        Log.d("ViewModel", "Segment added. New count: ${_processedSegments.value.size}")
    }

    fun clearAllSegments() {
        Log.d("ViewModel", "Clearing all segments. Count: ${_processedSegments.value.size}")
        _processedSegments.value.forEach {
            try {
                it.recycle()
            } catch (e: Exception) {
                Log.e("ViewModel", "Error recycling bitmap", e)
            }
        }
        _processedSegments.value = emptyList()
        _currentSegmentIndex.value = 0
        Log.d("ViewModel", "All segments cleared")
    }

    fun nextSegment() {
        if (_processedSegments.value.size > 1) {
            _currentSegmentIndex.value = (_currentSegmentIndex.value + 1) % _processedSegments.value.size
            Log.d("ViewModel", "Next segment. New index: ${_currentSegmentIndex.value}")
        }
    }

    fun previousSegment() {
        if (_processedSegments.value.size > 1) {
            _currentSegmentIndex.value = if (_currentSegmentIndex.value - 1 >= 0) {
                _currentSegmentIndex.value - 1
            } else {
                _processedSegments.value.size - 1
            }
            Log.d("ViewModel", "Previous segment. New index: ${_currentSegmentIndex.value}")
        }
    }

    fun setSegmentIndex(index: Int) {
        if (index in 0 until _processedSegments.value.size) {
            _currentSegmentIndex.value = index
            Log.d("ViewModel", "Set segment index to: $index")
        }
    }

    fun updateDetectionInfo(resultsCount: Int) {
        _detectedObjectsCount.value = resultsCount
    }

    fun setupZoomState(camera: Camera) {
        this.camera = camera

        val zoomState = camera.cameraInfo.zoomState.value
        zoomState?.let {
            minZoomRatio = zoomState.minZoomRatio
            maxZoomRatio = zoomState.maxZoomRatio
            _zoomProgress.update { calculateZoomProgress(zoomState.zoomRatio) }
        }
    }

    fun updateCameraZoom(newZoomValue: Float) {
        _zoomProgress.update { newZoomValue }
        camera.let { cam ->
            val newZoomRatio = minZoomRatio + (_zoomProgress.value / 10f) * (maxZoomRatio - minZoomRatio)
            cam.cameraControl.setZoomRatio(newZoomRatio)
        }
    }

    fun handlePinchZoom(scaleFactor: Float) {
        camera.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value ?: return
            val currentZoom = zoomState.zoomRatio
            val newZoom = currentZoom * scaleFactor

            // Ограничиваем зум минимальным/максимальным значением
            val clampedZoom = newZoom.coerceIn(minZoomRatio, maxZoomRatio)

            // Обновляем состояние зума
            cam.cameraControl.setZoomRatio(clampedZoom)
            _zoomProgress.update { calculateZoomProgress(clampedZoom) }
        }
    }

    private fun calculateZoomProgress(zoomRatio: Float): Float {
        return if (maxZoomRatio > minZoomRatio) {
            ((zoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)) * 10f
        } else {
            0f
        }
    }
}