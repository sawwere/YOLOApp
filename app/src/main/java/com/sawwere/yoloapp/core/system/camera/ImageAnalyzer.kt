package com.sawwere.yoloapp.core.system.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class ImageAnalyzer(
    private vararg val analyzerListeners: AnalyzeFrame
) : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )

        imageProxy.use {
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        analyzerListeners.forEach { analyzeFrame ->
            analyzeFrame(rotatedBitmap)
        }
    }
}