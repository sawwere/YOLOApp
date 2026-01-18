package com.sawwere.yoloapp.core.image

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

class ImageProcessor {

    companion object {
        private const val TARGET_SIZE = 224
        private const val ADAPTIVE_THRESH_BLOCK_SIZE = 31
        private const val ADAPTIVE_THRESH_C = 10
        private const val MEDIAN_BLUR_SIZE = 3
        private const val MORPH_KERNEL_SIZE = 3
    }

    fun processDocumentImage(bitmap: Bitmap): Bitmap {
        return try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY)

            val binaryMat = Mat()
            Imgproc.adaptiveThreshold(
                grayMat,
                binaryMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                ADAPTIVE_THRESH_BLOCK_SIZE,
                ADAPTIVE_THRESH_C.toDouble()
            )

            val denoisedMat = removeNoise(binaryMat)
            val documentMat = findDocument(denoisedMat, grayMat)
            val normalizedMat = normalizeTo224(documentMat)

            val resultBitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(normalizedMat, resultBitmap)

            srcMat.release()
            grayMat.release()
            binaryMat.release()
            denoisedMat.release()
            documentMat.release()
            normalizedMat.release()

            resultBitmap
        } catch (e: Exception) {
            Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLACK)
            }
        }
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

    private fun findDocument(binaryMat: Mat, grayMat: Mat): Mat {
        try {
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            if (contours.isEmpty()) {
                return grayMat.clone()
            }

            var maxArea = 0.0
            var maxContour: MatOfPoint? = null

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > maxArea) {
                    maxArea = area
                    maxContour = contour
                }
            }

            if (maxContour == null) {
                return grayMat.clone()
            }

            val rect = Imgproc.boundingRect(maxContour)

            val padding = 10
            val x = max(rect.x - padding, 0)
            val y = max(rect.y - padding, 0)
            val right = min(rect.x + rect.width + padding, grayMat.cols())
            val bottom = min(rect.y + rect.height + padding, grayMat.rows())

            val width = right - x
            val height = bottom - y

            if (width <= 0 || height <= 0) {
                return grayMat.clone()
            }

            val paddedRect = Rect(x, y, width, height)

            val document = Mat(grayMat, paddedRect)

            hierarchy.release()
            contours.forEach { it.release() }

            return document
        } catch (e: Exception) {
            return grayMat.clone()
        }
    }

    private fun normalizeTo224(inputMat: Mat): Mat {
        if (inputMat.empty() || inputMat.rows() <= 0 || inputMat.cols() <= 0) {
            return Mat(TARGET_SIZE, TARGET_SIZE, CvType.CV_8UC1, Scalar(255.0))
        }

        try {
            val height = inputMat.rows()
            val width = inputMat.cols()

            val maxSide = max(height, width).toDouble()

            val scale = TARGET_SIZE.toDouble() / maxSide

            val newHeight = (height * scale).toInt()
            val newWidth = (width * scale).toInt()

            val resized = Mat()
            Imgproc.resize(inputMat, resized, Size(newWidth.toDouble(), newHeight.toDouble()))
            val squareMat = Mat(TARGET_SIZE, TARGET_SIZE, CvType.CV_8UC1, Scalar(255.0))

            val xOffset = (TARGET_SIZE - newWidth) / 2
            val yOffset = (TARGET_SIZE - newHeight) / 2

            if (xOffset >= 0 && yOffset >= 0 &&
                xOffset + newWidth <= TARGET_SIZE &&
                yOffset + newHeight <= TARGET_SIZE) {

                val roi = Rect(xOffset, yOffset, newWidth, newHeight)
                val destinationROI = squareMat.submat(roi)
                resized.copyTo(destinationROI)
                destinationROI.release()
            } else {
                val copyWidth = min(newWidth, TARGET_SIZE)
                val copyHeight = min(newHeight, TARGET_SIZE)
                val roi = Rect(0, 0, copyWidth, copyHeight)
                val destinationROI = squareMat.submat(roi)
                val sourceROI = resized.submat(Rect(0, 0, copyWidth, copyHeight))
                sourceROI.copyTo(destinationROI)
                sourceROI.release()
                destinationROI.release()
            }

            resized.release()
            return squareMat
        } catch (e: Exception) {
            return Mat(TARGET_SIZE, TARGET_SIZE, CvType.CV_8UC1, Scalar(255.0))
        }
    }

    fun processDocumentImageEnhanced(bitmap: Bitmap): Bitmap {
        return try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGB2GRAY)

            // 2. Улучшение контраста (CLAHE - Contrast Limited Adaptive Histogram Equalization)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            val enhancedMat = Mat()
            clahe.apply(grayMat, enhancedMat)

            // 3. Гауссово размытие для уменьшения шума
            val blurredMat = Mat()
            Imgproc.GaussianBlur(enhancedMat, blurredMat, Size(5.0, 5.0), 0.0)

            // 4. Адаптивная бинаризация с Otsu
            val binaryMat = Mat()
            Imgproc.adaptiveThreshold(
                blurredMat,
                binaryMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                11,
                2.0
            )

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val morphedMat = Mat()
            Imgproc.morphologyEx(binaryMat, morphedMat, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(morphedMat, morphedMat, Imgproc.MORPH_OPEN, kernel)

            val documentMat = findDocument(morphedMat, blurredMat)

            val documentBinary = Mat()
            Imgproc.threshold(documentMat, documentBinary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

            val normalizedMat = normalizeTo224(documentBinary)

            val resultBitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(normalizedMat, resultBitmap)

            srcMat.release()
            grayMat.release()
            enhancedMat.release()
            blurredMat.release()
            binaryMat.release()
            kernel.release()
            morphedMat.release()
            documentMat.release()
            documentBinary.release()
            normalizedMat.release()
            //clahe.release()

            resultBitmap
        } catch (e: Exception) {
            processDocumentImage(bitmap)
        }
    }
}