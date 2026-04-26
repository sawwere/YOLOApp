package com.sawwere.yoloapp.core.domain.image

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ImageProcessor {
    companion object {
        private const val TARGET_SIZE = 224
        private const val ADAPTIVE_THRESH_BLOCK_SIZE = 15
        private const val ADAPTIVE_THRESH_C = 11.0
        private const val MEDIAN_BLUR_SIZE = 3
        private const val MORPH_KERNEL_SIZE = 3
    }

    private fun removeNoise(binaryMat: Mat): Mat {
        val medianFiltered = Mat()
        Imgproc.medianBlur(binaryMat, medianFiltered, MEDIAN_BLUR_SIZE)

        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(MORPH_KERNEL_SIZE.toDouble(), MORPH_KERNEL_SIZE.toDouble())
        )

        val opened = Mat()
        Imgproc.morphologyEx(medianFiltered, opened, Imgproc.MORPH_OPEN, kernel)

        val closed = Mat()
        Imgproc.morphologyEx(opened, closed, Imgproc.MORPH_CLOSE, kernel)

        medianFiltered.release()
        opened.release()
        kernel.release()

        return closed
    }

    private fun findDocument(binaryMat: Mat): Mat {
        // 1. Инверсия: текст становится белым на чёрном
        val inverted = Mat()
        Core.bitwise_not(binaryMat, inverted)

        val width = inverted.cols()
        val height = inverted.rows()
        val minBlackToWhiteRatio = 0.8

        // 2. Поиск связных компонент
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val numLabels = Imgproc.connectedComponentsWithStats(inverted, labels, stats, centroids, 8)

        val removeMask = Mat.zeros(inverted.size(), CvType.CV_8UC1)
        for (i in 1 until numLabels) {
            val x = stats.get(i, Imgproc.CC_STAT_LEFT)[0].toInt()
            val y = stats.get(i, Imgproc.CC_STAT_TOP)[0].toInt()
            val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val h = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()

            val touchesBorder = x == 0 || y == 0 || x + w == width || y + h == height
            if (!touchesBorder) continue

            val bboxTotal = w * h
            val blackPixels = bboxTotal - area

            if (blackPixels >= minBlackToWhiteRatio * area) {
                val componentMask = Mat.zeros(inverted.size(), CvType.CV_8UC1)
                Core.compare(labels, Scalar(i.toDouble()), componentMask, Core.CMP_EQ)
                Core.bitwise_or(removeMask, componentMask, removeMask)
                componentMask.release()
            }
        }

        // 4. Удаление помеченных компонент
        val withoutComponents = Mat()
        Core.bitwise_not(removeMask, removeMask)
        Core.bitwise_and(inverted, removeMask, inverted)

        // 5. Обратная инверсия
        Core.bitwise_not(inverted, inverted)

//        // 6. Поиск bounding box оставшегося текста
//        // Для этого временно инвертируем, чтобы текст был белым на чёрном
//        val textWhite = Mat()
//        Core.bitwise_not(inverted, textWhite)
//        val points = MatOfPoint()
//        Core.findNonZero(textWhite, points)
//        if (points.total() == 0L) {
//            // Если текста не осталось – возвращаем исходное изображение
//            return grayMat.clone()
//        }
//        val rect = Core.boundingRect(points)

        labels.release()
        stats.release()
        centroids.release()
        removeMask.release()
//        textWhite.release()
//        points.release()

        return inverted
    }

    private fun normalizeToSquare(inputMat: Mat, targetSize: Int): Mat {
        if (inputMat.empty() || inputMat.rows() <= 0 || inputMat.cols() <= 0) {
            return Mat(targetSize, targetSize, CvType.CV_8UC1, Scalar(255.0))
        }

        val height = inputMat.rows()
        val width = inputMat.cols()

        val maxSide = max(height, width).toDouble()

        val scale = targetSize.toDouble() / maxSide

        val newHeight = (height * scale).toInt()
        val newWidth = (width * scale).toInt()

        val resized = Mat()
        Imgproc.resize(inputMat, resized, Size(newWidth.toDouble(), newHeight.toDouble()))
        val squareMat = Mat(targetSize, targetSize, CvType.CV_8UC1, Scalar(255.0))

        val xOffset = (targetSize - newWidth) / 2
        val yOffset = (targetSize - newHeight) / 2

        val roi = Rect(xOffset, yOffset, newWidth, newHeight)
        val destinationROI = squareMat.submat(roi)
        resized.copyTo(destinationROI)
        destinationROI.release()

        resized.release()
        return squareMat
    }

    fun processDocumentImageEnhanced(bitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY)

        // 3. Гауссово размытие для уменьшения шума
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(3.0, 3.0), 0.0)

        // 4. Адаптивная бинаризация с Otsu
        val binaryMat = Mat()
        Imgproc.adaptiveThreshold(
            blurredMat,
            binaryMat,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            ADAPTIVE_THRESH_BLOCK_SIZE,
            ADAPTIVE_THRESH_C
        )

        val kernel = Mat(2, 2,  CvType.CV_8UC1, Scalar(1.0))
        val morphedMat = Mat()
        Imgproc.erode(binaryMat, morphedMat, kernel)

        val documentMat = findDocument(morphedMat)

        val normalizedMat = normalizeToSquare(documentMat, TARGET_SIZE)

        val resultBitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(normalizedMat, resultBitmap)

        srcMat.release()
        grayMat.release()
        blurredMat.release()
        binaryMat.release()
        kernel.release()
        morphedMat.release()
        documentMat.release()
        normalizedMat.release()

        return resultBitmap
    }
}