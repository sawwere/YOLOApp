package com.sawwere.yoloapp.ui.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Camera
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sawwere.yoloapp.core.config.CAMERA_SMALL_HEIGHT
import com.sawwere.yoloapp.core.config.CAMERA_SMALL_WIDTH
import com.sawwere.yoloapp.core.config.EmulatorUtils
import com.sawwere.yoloapp.core.data.entity.Category
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.domain.image.DrawImages
import com.sawwere.yoloapp.core.domain.image.ImageProcessor
import com.sawwere.yoloapp.core.domain.image.ImageUtils
import com.sawwere.yoloapp.core.domain.repository.AppRepository
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenMode
import com.sawwere.yoloapp.ui.camera.navigation.CameraScreenNavigation
import com.sawwere.yoloapp.ui.camera.usecase.ValidateObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraScreenUIState(
    val preProcessTime: Long = 0L,
    val inferenceTime: Long = 0L,
    val postProcessTime: Long = 0L,
    val debugMode: Boolean = false,
    val zoomProgress: Float = 0f,
    val drawMode: String = CameraScreenMode.ADD.value,
    val errorMessage: String? = null
)

@HiltViewModel
class CameraScreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appRepository: AppRepository,
    private val imageProcessor: ImageProcessor,
    private val drawImages: DrawImages,
    private val detectionComponent: DetectionComponent,
    private val validateObject: ValidateObject
): ViewModel(), DetectionComponent.InstanceSegmentationListener {
    init {
        detectionComponent.subscrube(this)
    }

    private lateinit var camera: Camera

    private val _uiState = MutableStateFlow(CameraScreenUIState())
    val uiState: StateFlow<CameraScreenUIState> = _uiState.asStateFlow()

    private lateinit var category: Category
    var categoryId: Long = savedStateHandle[CameraScreenNavigation.CATEGORY_ID_ARG]
        ?: 0L
        set(value) {
            field = value
            viewModelScope.launch {
                category = appRepository.getCategoryById(field)!!
            }
        }

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
    private val _capturedSegments = mutableStateOf<List<Bitmap>>(emptyList())
    val capturedSegments: List<Bitmap> get() = _capturedSegments.value

    // Текущий индекс отображаемого сегмента
    private val _currentSegmentIndex = mutableIntStateOf(0)
    val currentSegmentIndex: Int get() = _currentSegmentIndex.intValue

    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f

    private fun updateDetectionState(
        preProcessTime: Long,
        inferenceTime: Long,
        postProcessTime: Long,
        detectionResults: List<DetectionComponent.Detection>
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                preProcessTime = preProcessTime,
                inferenceTime = inferenceTime,
                postProcessTime = postProcessTime
            )
        }
         _detectedBoxes.value = detectionResults
    }

    fun toggleDebugMode() {
        _uiState.update { it.copy(debugMode = !it.debugMode) }
    }

    fun onCapture(
        bitmap: Bitmap,
        categoryId: Long,
        scaledDetections: List<DetectionComponent.Detection>
    ) {
        _capturedBitmap.value = bitmap
        Log.i(
            TAG,
            "width=${capturedBitmap!!.width} height=${capturedBitmap!!.height}"
        )
        viewModelScope.launch(Dispatchers.IO) {
            val bitmapCopy = bitmap.copy(bitmap.config!!, true)
            if (EmulatorUtils.isEmulator()) {
                appRepository.insertPhoto(categoryId, bitmapCopy)
            }
            try {
                processCapturedSegments(
                    originalBitmap = bitmapCopy,
                    detectionBoxes = scaledDetections
                ).forEach {
                    addProcessedSegment(it, categoryId)
                }
            } catch (e: Exception) {
                setError(e.message ?: "Ошибка при обработке снимка")
            } finally {
                if (!bitmapCopy.isRecycled) {
                    bitmapCopy.recycle()
                }
            }

        }
    }

    private fun processCapturedSegments(
        originalBitmap: Bitmap,
        detectionBoxes: List<DetectionComponent.Detection>
    ): List<Bitmap> {
        Log.d(TAG, "Starting segment processing...")

        clearAllSegments()
        if (detectionBoxes.isEmpty()) {
            Log.w(TAG, "No captured data to process")
        }

        val result = mutableListOf<Bitmap>()
        try {
            for ((index, detection) in detectionBoxes.withIndex()) {
                try {
                    Log.d(TAG, "Processing detection $index")

                    val croppedSegment = ImageUtils.extractRectSegment(originalBitmap, detection.bbox)

                    if (uiState.value.debugMode) {
                        val x = croppedSegment.copy(croppedSegment.config!!, false)
                        viewModelScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
                            delay(500)
                            appRepository.insertPhoto(categoryId, x)
                            x.recycle()
                        }

                    }
                    val processedBitmap = processSingleSegment(croppedSegment)
                    Log.d(TAG, "Processed bitmap for object $index")
                    result.add(processedBitmap)

                    croppedSegment.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing object $index: ${e.message}", e)
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing captured segments: ${e.message}", e)
            setError("Error processing captured segments")
        }

        return result
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
        Log.d(TAG, "Adding segment. Current count: ${_capturedSegments.value.size}")
        _capturedSegments.value += bitmap
        Log.d(TAG, "Segment added. New count: ${_capturedSegments.value.size}")

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
        _capturedSegments.value.forEach {
            try {
                it.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling bitmap", e)
            }
        }
        _capturedSegments.value = emptyList()
        _currentSegmentIndex.intValue = 0
        Log.d(TAG, "All segments cleared")
    }

    fun nextSegment() {
        if (capturedSegments.size > 1) {
            _currentSegmentIndex.intValue = (currentSegmentIndex + 1) % capturedSegments.size
        }
    }

    fun previousSegment() {
        if (_capturedSegments.value.size > 1) {
            _currentSegmentIndex.intValue = if (_currentSegmentIndex.intValue - 1 >= 0) {
                _currentSegmentIndex.intValue - 1
            } else {
                _capturedSegments.value.size - 1
            }
        }
    }

    fun setSegmentIndex(index: Int) {
        if (index in 0 until _capturedSegments.value.size) {
            _currentSegmentIndex.intValue = index
        }
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

    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onError(error: String) {
        Log.e(TAG, error)
        setError(message = error)
    }

    override fun onEmpty() {
        segmentedBitmap = null
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<DetectionComponent.Detection>,
        preProcessTime: Long,
        postProcessTime: Long,
        originalBitmap: Bitmap
    ) {
        updateDetectionState(
            preProcessTime = preProcessTime,
            inferenceTime = interfaceTime,
            postProcessTime = postProcessTime,
            detectionResults = results
        )
        if (drawMode == CameraScreenMode.CHECK.value) {
            val processedDetections = processCapturedSegments(
                originalBitmap = originalBitmap,
                detectionBoxes = results
            )
            for ((index, detection) in processedDetections.withIndex()) {
                val res = validateObject(detection, category)
                results[index].classId = when(res) {
                    is ValidateObject.ValidationResult.Genuine -> 0
                    is ValidateObject.ValidationResult.Forgery -> 1
                    is ValidateObject.ValidationResult.None -> 2
                }
                Log.d(
                    TAG,
                    "index=$index, confidence=${res.confidence}, distance=${res.distance}"
                )
            }
        }

        segmentedBitmap = if (results.isEmpty()) {
            null
        } else {
            drawImages(
                imageWidth = CAMERA_SMALL_WIDTH,
                imageHeight = CAMERA_SMALL_HEIGHT,
                results = results,
                drawOverlay = drawMode == CameraScreenMode.CHECK.value
            )
        }
    }

    companion object {
        private const val TAG = "CameraScreenViewModel"
    }
}