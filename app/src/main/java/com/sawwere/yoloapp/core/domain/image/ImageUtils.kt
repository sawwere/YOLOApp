package com.sawwere.yoloapp.core.domain.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    fun extractRectSegment(
        originalBitmap: Bitmap,
        boundingBox: RectF,
        padding: Int = 5
    ): Bitmap {
        val left = boundingBox.left.toInt()
        val top = boundingBox.top.toInt()
        val right = boundingBox.right.toInt()
        val bottom = boundingBox.bottom.toInt()

        require(left >= right && top >= bottom)
        require(padding >= 0)

        val clampedLeft = max(left - padding, 0)
        val clampedTop = max(top - padding, 0)
        val clampedRight = min(right + padding, originalBitmap.width)
        val clampedBottom = min(bottom + padding, originalBitmap.height)

        val width = clampedRight - clampedLeft
        val height = clampedBottom - clampedTop

        val segment = Bitmap.createBitmap(
            originalBitmap,
            clampedLeft, clampedTop, width, height
        )
        return segment
    }

    fun scaleRect(rect: RectF, fromSize: Pair<Int, Int>, toSize: Pair<Int, Int>): RectF {
        val scaleX = toSize.first.toFloat() / fromSize.first
        val scaleY = toSize.second.toFloat() / fromSize.second
        return RectF(
            rect.left * scaleX,
            rect.top * scaleY,
            rect.right * scaleX,
            rect.bottom * scaleY
        )
    }

    fun imageProxyToBitmapWithRotation(imageProxy: ImageProxy): Bitmap {
        val format = imageProxy.format
        val rotation = imageProxy.imageInfo.rotationDegrees

        val bitmap = when (format) {
            ImageFormat.JPEG -> {
                // JPEG: данные лежат в первом plane
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            ImageFormat.YUV_420_888 -> {
                // Конвертация YUV_420_888 → NV21 → JPEG → Bitmap
                val yBuffer = imageProxy.planes[0].buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer

                val ySize = yBuffer.remaining()
                val vSize = vBuffer.remaining()
                val uSize = uBuffer.remaining()

                val nv21 = ByteArray(ySize + vSize + uSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
                val jpegData = out.toByteArray()
                BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            }
            else -> throw IllegalArgumentException("Unsupported format: $format")
        }

        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { bitmap.recycle() }
        } else {
            bitmap
        }
    }
}