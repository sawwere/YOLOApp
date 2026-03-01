package com.sawwere.yoloapp.core.domain.image

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
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
}