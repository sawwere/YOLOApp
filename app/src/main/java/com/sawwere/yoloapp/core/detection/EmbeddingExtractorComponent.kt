package com.sawwere.yoloapp.core.detection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddingExtractorComponent(
    context: Context,
    modelPath: String,
) {
    private var interpreter: Interpreter
    private var tensorWidth = 0
    private var tensorHeight = 0

    private val embeddingSize = 128


    init {
        val options = Interpreter.Options()
        options.setNumThreads(4)

        val model = FileUtil.loadMappedFile(context, modelPath)
        val version = org.tensorflow.lite.TensorFlowLite.version()
        Log.d("TFLite", "Version: $version")
        interpreter = Interpreter(model, options)

        // Получаем информацию о входном тензоре
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor?.shape()
        val inputType = inputTensor?.dataType()

        // Получаем информацию о выходном тензоре
        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor?.shape()
        val outputType = outputTensor?.dataType()

        when (inputType) {
            DataType.FLOAT32 -> Log.d("Model", "Using FP32 input")
            DataType.UINT8 -> Log.d("Model", "Using UINT8 quantized input")
            else -> Log.e("Model", "Unsupported input type: $inputType")
        }

        when (outputType) {
            DataType.FLOAT32 -> Log.d("Model", "Using FP32 output")
            DataType.UINT8 -> Log.d("Model", "Using UINT8 quantized output")
            else -> Log.e("Model", "Unsupported output type: $outputType")
        }

        // Определяем формат входного тензора (поддержка NCHW и NHWC)
        if (inputShape != null) {
            when {
                inputShape.size == 4 && inputShape[1] == 3 -> {
                    // NCHW format: [1, 3, height, width]
                    tensorHeight = inputShape[2]
                    tensorWidth = inputShape[3]
                    Log.d("Model", "Input format: NCHW, size: ${tensorWidth}x$tensorHeight")
                }
                inputShape.size == 4 -> {
                    // NHWC format: [1, height, width, channels]
                    tensorHeight = inputShape[1]
                    tensorWidth = inputShape[2]
                    Log.d("Model", "Input format: NHWC, size: ${tensorWidth}x$tensorHeight, channels: ${inputShape[3]}")
                }
                else -> {
                    Log.e("Model", "Unexpected input shape: ${inputShape.joinToString()}")
                }
            }
        }

        if (outputShape != null) {
            Log.d("Model", "Output shape: ${outputShape.joinToString()}")
            if (outputShape.size >= 2) {
                val expectedSize = outputShape[1]
                if (expectedSize != embeddingSize) {
                    Log.w("Model", "Output size $expectedSize differs from expected $embeddingSize")
                }
            }
        }
    }

    /**
     * Получить эмбеддинг для одного изображения
     */
    fun getEmbedding(bitmap: Bitmap): FloatArray {
        Log.d(
            "TAG",
            "Processed bitmap for object: ${bitmap.width}x${bitmap.height} ${bitmap.byteCount}"
        )
        var preProcessTime = SystemClock.uptimeMillis()

        val inputBuffer = preProcess(bitmap)

        preProcessTime = SystemClock.uptimeMillis() - preProcessTime
        Log.d("Performance", "Preprocessing time: ${preProcessTime}ms")

        var inferenceTime = SystemClock.uptimeMillis()

        val outputBuffer = Array(1) { FloatArray(embeddingSize) }

        interpreter.run(inputBuffer, outputBuffer)

        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        Log.d("Performance", "Inference time: ${inferenceTime}ms")

        return outputBuffer[0]
    }

    /**
     * Предобработка изображения перед подачей в модель
     */
    private fun preProcess(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = if (bitmap.width == tensorWidth && bitmap.height == tensorHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, tensorWidth, tensorHeight, true)
        }

        val byteBuffer = ByteBuffer.allocateDirect(4 * tensorWidth * tensorHeight)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(tensorWidth * tensorHeight)
        resizedBitmap.getPixels(pixels, 0, tensorWidth, 0, 0, tensorWidth, tensorHeight)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // Стандартная формула яркости (Y = 0.299R + 0.587G + 0.114B)
            val gray = 0.299f * r + 0.587f * g + 0.114f * b

            byteBuffer.putFloat(gray)
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    /**
     * Закрыть интерпретатор при уничтожении компонента
     */
    fun close() {
        interpreter.close()
    }
}
