package com.sawwere.yoloapp.ui.camera

import androidx.lifecycle.SavedStateHandle
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Camera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.domain.image.DrawImages
import com.sawwere.yoloapp.core.domain.image.ImageProcessor
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import com.sawwere.yoloapp.core.domain.image.ImageUtils
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenMode
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CameraScreenUIState(
    val preProcessTime: Long = 0L,
    val inferenceTime: Long = 0L,
    val postProcessTime: Long = 0L,
    val debugMode: Boolean = false,
    val zoomProgress: Float = 0f,
    val drawMode: String = CameraScreenMode.ADD.value
)

@HiltViewModel
class CameraScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appRepository: AppRepository,
    private val imageProcessor: ImageProcessor,
    private val drawImages: DrawImages,
    private val detectionComponent: DetectionComponent,
): ViewModel(), DetectionComponent.InstanceSegmentationListener {
    init {
        detectionComponent.subscrube(this)
    }

    private lateinit var camera: Camera

    private val _uiState = MutableStateFlow(CameraScreenUIState())
    val uiState: StateFlow<CameraScreenUIState> = _uiState.asStateFlow()

    var drawMode: String = savedStateHandle[CameraScreenNavigation.MODE_ARG]
        ?: CameraScreenMode.ADD.value.also {
            Log.w(
                TAG,
                " ${CameraScreenNavigation.MODE_ARG} argument not passed, " +
                        "switching to default value: ${CameraScreenMode.ADD}"
            )
        }
        set(value) {
            field = value
            _uiState.update { it.copy(drawMode = value) }
        }

    var segmentedBitmap: Bitmap? by mutableStateOf(null)

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap get() = _capturedBitmap.value

    private val _detectedBoxes = MutableStateFlow<List<DetectionComponent.Detection>>(emptyList())
    val detectedBoxes: StateFlow<List<DetectionComponent.Detection>> = _detectedBoxes.asStateFlow()
    // Список обработанных сегментов
    private val _processedSegments = mutableStateOf<List<Bitmap>>(emptyList())
    val processedSegments: List<Bitmap> get() = _processedSegments.value

    // Текущий индекс отображаемого сегмента
    private val _currentSegmentIndex = mutableIntStateOf(0)
    val currentSegmentIndex: Int get() = _currentSegmentIndex.intValue

    // Количество найденных объектов
    private val _detectedObjectsCount = MutableStateFlow(0)
    val detectedObjectsCount = _detectedObjectsCount.asStateFlow()

    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f

    private fun updateTimers(
        preProcessTime: Long,
        inferenceTime: Long,
        postProcessTime: Long
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                preProcessTime = preProcessTime,
                inferenceTime = inferenceTime,
                postProcessTime = postProcessTime
            )
        }
    }

    fun toggleDebugMode() {
        _uiState.update { it.copy(debugMode = !it.debugMode) }
    }

    fun onCapture(bitmap: Bitmap, categoryId: Long) {
        _capturedBitmap.value = bitmap
        Log.i(
            TAG,
            "width=${capturedBitmap!!.width} height=${capturedBitmap!!.height}"
        )
        val capturedBoxes = detectedBoxes.value
        viewModelScope.launch(Dispatchers.IO) {
            val bitmapCopy = bitmap.copy(bitmap.config!!, true)
            try {
                appRepository.insertPhoto(categoryId, bitmapCopy)
                processCapturedSegments(
                    categoryId = categoryId,
                    capturedOriginalBitmap = bitmapCopy,
                    detectionBoxes = capturedBoxes
                )
            } finally {
                if (!bitmapCopy.isRecycled) {
                    bitmapCopy.recycle()
                }
            }

        }
    }

    private suspend fun processCapturedSegments(
        capturedOriginalBitmap: Bitmap,
        categoryId: Long,
        detectionBoxes: List<DetectionComponent.Detection>
    ) {
        Log.d(TAG, "Starting segment processing...")

        clearAllSegments()
        if (detectionBoxes.isEmpty()) {
            Log.d(TAG, "No captured data to process")
        }

        try {
            for ((index, detection) in detectionBoxes.withIndex()) {
                try {
                    Log.d(TAG, "Processing detection $index")

                    val croppedSegment = ImageUtils.extractRectSegment(capturedOriginalBitmap, detection.bbox)

                    if (croppedSegment != null) {
                        addProcessedSegment(croppedSegment.copy(croppedSegment.config!!, true), categoryId)
                        val processedBitmap = processSingleSegment(croppedSegment)
                        Log.d(
                            TAG,
                            "Processed bitmap for object $index: ${processedBitmap.width}x${processedBitmap.height}"
                        )
                        addProcessedSegment(processedBitmap, categoryId)

                        croppedSegment.recycle()
                    } else {
                        Log.w(TAG, "Cropped segment is null for object $index")
                        Log.w(TAG, "BBox: [${detection.bbox.left}, ${detection.bbox.top}, ${detection.bbox.right}, ${detection.bbox.bottom}]")
                        Log.w(TAG, "Image size: ${capturedOriginalBitmap.width}x${capturedOriginalBitmap.height}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing object $index: ${e.message}", e)
                }
            }

            withContext(Dispatchers.Main) {
                Log.d(TAG, "Final segment count in ViewModel: ${processedSegments.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing captured segments: ${e.message}", e)
        }
    }

    private fun processSingleSegment(segmentBitmap: Bitmap): Bitmap {
        return imageProcessor.processDocumentImageEnhanced(
            segmentBitmap.copy(segmentBitmap.config!!, true)
        )
    }

    override fun onCleared() {
        super.onCleared()
        _capturedBitmap.value?.takeIf { !it.isRecycled }?.recycle()
    }

    private fun addProcessedSegment(bitmap: Bitmap, categoryId: Long) {
        Log.d(TAG, "Adding segment. Current count: ${_processedSegments.value.size}")
        _processedSegments.value += bitmap
        Log.d(TAG, "Segment added. New count: ${_processedSegments.value.size}")

        val bitmapCopy = bitmap.copy(bitmap.config!!, true) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appRepository.insertPhoto(categoryId, bitmap)
            } finally {
                // Safely recycle the copy after saving (if not already recycled)
                if (!bitmapCopy.isRecycled) {
                    bitmapCopy.recycle()
                }
            }
        }
    }

    fun clearAllSegments() {
        _processedSegments.value.forEach {
            try {
                it.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling bitmap", e)
            }
        }
        _processedSegments.value = emptyList()
        _currentSegmentIndex.intValue = 0
        Log.d(TAG, "All segments cleared")
    }

    fun nextSegment() {
        if (processedSegments.size > 1) {
            _currentSegmentIndex.intValue = (currentSegmentIndex + 1) % processedSegments.size
        }
    }

    fun previousSegment() {
        if (_processedSegments.value.size > 1) {
            _currentSegmentIndex.intValue = if (_currentSegmentIndex.intValue - 1 >= 0) {
                _currentSegmentIndex.intValue - 1
            } else {
                _processedSegments.value.size - 1
            }
        }
    }

    fun setSegmentIndex(index: Int) {
        if (index in 0 until _processedSegments.value.size) {
            _currentSegmentIndex.intValue = index
        }
    }

    fun updateDetections(detections: List<DetectionComponent.Detection>) {
        _detectedBoxes.value = detections
        _detectedObjectsCount.value = detections.size
    }

    fun setupZoomState(camera: Camera) {
        this.camera = camera

        val zoomState = camera.cameraInfo.zoomState.value
        zoomState?.let {
            minZoomRatio = zoomState.minZoomRatio
            maxZoomRatio = zoomState.maxZoomRatio
            val initialProgress = calculateZoomProgress(zoomState.zoomRatio)
            _uiState.update { it.copy(zoomProgress = initialProgress) }
        }
    }

    fun updateCameraZoom(newZoomValue: Float) {
        _uiState.update { it.copy(zoomProgress = newZoomValue) }
        camera.let { cam ->
            val newZoomRatio = minZoomRatio + (newZoomValue / 10f) * (maxZoomRatio - minZoomRatio)
            cam.cameraControl.setZoomRatio(newZoomRatio)
        }
    }

    fun handlePinchZoom(scaleFactor: Float) {
        camera.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value ?: return
            val currentZoom = zoomState.zoomRatio
            val newZoom = currentZoom * scaleFactor
            val clampedZoom = newZoom.coerceIn(minZoomRatio, maxZoomRatio)
            cam.cameraControl.setZoomRatio(clampedZoom)
            val newProgress = calculateZoomProgress(clampedZoom)
            _uiState.update { it.copy(zoomProgress = newProgress) }
        }
    }

    private fun calculateZoomProgress(zoomRatio: Float): Float {
        return if (maxZoomRatio > minZoomRatio) {
            ((zoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)) * 10f
        } else {
            0f
        }
    }

    companion object {
        private const val TAG = "CameraScreenViewModel"
    }

    override fun onError(error: String) {
        Log.e(TAG, error) // TODO
    }

    override fun onEmpty() {
        segmentedBitmap = null
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<DetectionComponent.Detection>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        updateTimers(
            preProcessTime = preProcessTime,
            inferenceTime = interfaceTime,
            postProcessTime = postProcessTime
        )
        updateDetections(results)

        // Создаем сегментированное изображение для отображения в реальном времени
        segmentedBitmap = if (results.isEmpty()) {
            null
        } else {
            drawImages(
                imageWidth = 480,
                imageHeight = 640,
                results = results,
                drawOverlay = drawMode == CameraScreenMode.CHECK.value
            )
        }
    }
}