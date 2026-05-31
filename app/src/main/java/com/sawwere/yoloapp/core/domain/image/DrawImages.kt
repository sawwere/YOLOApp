package com.sawwere.yoloapp.core.domain.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.sawwere.yoloapp.R
import com.sawwere.yoloapp.core.detection.DetectionComponent
import javax.inject.Singleton

@Singleton
class DrawImages(private val context: Context) {

    private val boxColors = listOf(
        R.color.overlay_green,
        R.color.overlay_orange,
        R.color.overlay_red,
        R.color.overlay_pink,
        R.color.overlay_cyan,
        R.color.overlay_purple,
        R.color.overlay_gray,
        R.color.overlay_teal,
        R.color.overlay_yellow,
    )

    private val boxStrokePaint = Paint().apply {
        strokeWidth = 4F
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // для полупрозрачной заливки
    private val boxFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // для текста
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    // для фона текста
    private val textBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    operator fun invoke(
        imageWidth: Int,
        imageHeight: Int,
        results: List<DetectionComponent.Detection>,
        drawOverlay: Boolean
    ) : Bitmap {
        val combined = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)


        for ((index, detection) in results.withIndex()) {
            val colorRes = boxColors[detection.classId % boxColors.size]
            val color = ContextCompat.getColor(context, colorRes)

            boxStrokePaint.color = color
            boxFillPaint.color = color
            textBackgroundPaint.color = color


            val text = "(${String.format("%.2f", detection.confidence)})"
            val textBounds = Rect()
            labelPaint.getTextBounds(text, 0, text.length, textBounds)

            val textX = detection.bbox.left
            val textY = detection.bbox.top

            if (drawOverlay) {
                canvas.drawRect(detection.bbox, boxFillPaint)

                canvas.drawText((index + 1).toString(), textX, textY, labelPaint)
            }

            canvas.drawRect(detection.bbox, boxStrokePaint)
        }

        return combined
    }
}