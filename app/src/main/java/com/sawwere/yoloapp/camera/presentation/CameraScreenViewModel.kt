package com.sawwere.yoloapp.camera.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraScreenViewModel {
    private val _preProcessTime = MutableStateFlow(0L)
    val preProcessTime = _preProcessTime.asStateFlow()

    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime = _inferenceTime.asStateFlow()

    private val _postProcessTime = MutableStateFlow(0L)
    val postProcessTime  = _postProcessTime.asStateFlow()


    fun updateTimers(
        preProcessTime: Long,
        inferenceTime: Long,
        postProcessTime: Long,
    ) {
        _inferenceTime.update { inferenceTime }
        _preProcessTime.update { preProcessTime }
        _postProcessTime.update { postProcessTime }
    }

}