package com.sawwere.yoloapp.core.image

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    fun List<Array<FloatArray>>.clone(): List<Array<FloatArray>> {
        return this.map { array -> array.map { it.clone() }.toTypedArray() }
    }

    fun Array<FloatArray>.scaleMask(targetWidth: Int, targetHeight: Int): Array<FloatArray> {
        val originalHeight = this.size
        val originalWidth = this[0].size

        val xRatio = (originalWidth shl 16) / targetWidth
        val yRatio = (originalHeight shl 16) / targetHeight

        val output = Array(targetHeight) { FloatArray(targetWidth) }

        for (y in 0 until targetHeight) {
            val origY = (y * yRatio) ushr 16
            for (x in 0 until targetWidth) {
                val origX = (x * xRatio) ushr 16
                output[y][x] = this[origY][origX]
            }
        }

        return output
    }


    fun Array<FloatArray>.toMask(): Array<IntArray> =
        map { row -> row.map { if (it > 0) 1 else 0 }.toIntArray() }.toTypedArray()


    fun Array<IntArray>.smooth(kernel: Int) : Array<IntArray> {
        // Using Array because it is faster then List
        val maskFloat = Array(this.size) { i ->
            FloatArray(this[i].size) { j ->
                if (this[i][j] > 0) 1F else 0F
            }
        }
        val gaussianKernel = createGaussianKernel(kernel)
        val blurredImage = applyGaussianBlur(maskFloat, gaussianKernel)
        return thresholdImage(blurredImage)
    }

    fun extractRectSegment(
        originalBitmap: Bitmap,
        boundingBox: RectF
    ): Bitmap? {
        return try {
            val left = boundingBox.left.toInt()
            val top = boundingBox.top.toInt()
            val right = boundingBox.right.toInt()
            val bottom = boundingBox.bottom.toInt()

            // Проверяем, что координаты валидны
            if (left >= right || top >= bottom) {
                Log.w("SegmentExtraction", "Invalid bounding box coordinates")
                return null
            }

            // Проверяем границы с небольшим запасом
            val padding = 5
            val clampedLeft = max(left - padding, 0)
            val clampedTop = max(top - padding, 0)
            val clampedRight = min(right + padding, originalBitmap.width)
            val clampedBottom = min(bottom + padding, originalBitmap.height)

            val width = clampedRight - clampedLeft
            val height = clampedBottom - clampedTop

            if (width <= 0 || height <= 0) {
                Log.w("SegmentExtraction", "Invalid dimensions after clamping: $width x $height")
                return null
            }

            // Вырезаем область
            val segment = Bitmap.createBitmap(
                originalBitmap,
                clampedLeft, clampedTop, width, height
            )

            Log.d("SegmentExtraction", "Successfully extracted segment: ${segment.width}x${segment.height}")
            segment
        } catch (e: Exception) {
            Log.e("SegmentExtraction", "Error extracting object segment: ${e.message}", e)
            null
        }
    }

    private fun createGaussianKernel(size: Int): Array<FloatArray> {
        val sigma = 2F
        val kernel = Array(size) { FloatArray(size) }
        val mean = size / 2
        var sum = 0F

        for (x in 0 until size) {
            for (y in 0 until size) {
                kernel[x][y] = (1F / (2F * Math.PI.toFloat() * sigma * sigma)) * exp(
                    -((x - mean) * (x - mean) + (y - mean) * (y - mean)) / (2F * sigma * sigma)
                )
                sum += kernel[x][y]
            }
        }

        for (x in 0 until size) {
            for (y in 0 until size) {
                kernel[x][y] /= sum
            }
        }

        return kernel
    }

    private fun applyGaussianBlur(image: Array<FloatArray>, kernel: Array<FloatArray>): Array<FloatArray> {
        val height = image.size
        val width = image[0].size
        val kernelSize = kernel.size
        val offset = kernelSize / 2
        val blurredImage = Array(height) { FloatArray(width) }

        for (i in image.indices) {
            for (j in image[i].indices) {
                if (i < offset || j < offset || i >= height - offset || j >= width - offset) {
                    blurredImage[i][j] = image[i][j]
                    continue
                }

                var sum = 0F
                for (ki in kernel.indices) {
                    for (kj in kernel[ki].indices) {
                        sum += image[i - offset + ki][j - offset + kj] * kernel[ki][kj]
                    }
                }
                blurredImage[i][j] = sum
            }
        }

        return blurredImage
    }

    private fun thresholdImage(image: Array<FloatArray>): Array<IntArray> {
        val height = image.size
        val width = image[0].size
        return Array(height) { i ->
            IntArray(width) { j ->
                if (image[i][j] > 0.9F) 1 else 0
            }
        }
    }
}