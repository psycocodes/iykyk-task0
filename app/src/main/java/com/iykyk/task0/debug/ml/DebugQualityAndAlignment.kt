package com.iykyk.task0.debug.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object DebugQualityAndAlignment {

    const val ALIGNED_FACE_SIZE = 112

    fun calculateLaplacianVariance(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return 0f

        val step = 2 // Sample every 2nd pixel for speed
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1 step step) {
            for (x in 1 until width - 1 step step) {
                val idx = y * width + x
                val center = luminance(pixels[idx])
                val left = luminance(pixels[idx - 1])
                val right = luminance(pixels[idx + 1])
                val top = luminance(pixels[idx - width])
                val bottom = luminance(pixels[idx + width])

                val laplacian = abs(4.0 * center - left - right - top - bottom)
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return variance.toFloat().coerceAtLeast(0f)
    }

    fun calculateSobelSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return 0f

        val step = 2
        var totalMag = 0.0
        var count = 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1 step step) {
            for (x in 1 until width - 1 step step) {
                val p00 = luminance(pixels[(y - 1) * width + (x - 1)])
                val p01 = luminance(pixels[(y - 1) * width + x])
                val p02 = luminance(pixels[(y - 1) * width + (x + 1)])
                val p10 = luminance(pixels[y * width + (x - 1)])
                val p12 = luminance(pixels[y * width + (x + 1)])
                val p20 = luminance(pixels[(y + 1) * width + (x - 1)])
                val p21 = luminance(pixels[(y + 1) * width + x])
                val p22 = luminance(pixels[(y + 1) * width + (x + 1)])

                val gx = (p02 + 2.0 * p12 + p22) - (p00 + 2.0 * p10 + p20)
                val gy = (p20 + 2.0 * p21 + p22) - (p00 + 2.0 * p01 + p02)

                totalMag += sqrt(gx * gx + gy * gy)
                count++
            }
        }

        return if (count > 0) (totalMag / count).toFloat() * 100f else 0f
    }

    private fun luminance(color: Int): Double {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    fun cropSquarePortrait(bitmap: Bitmap, box: Rect): Bitmap {
        val marginX = (box.width() * 0.20f).toInt()
        val marginY = (box.height() * 0.20f).toInt()

        val rawLeft = (box.left - marginX).coerceIn(0, bitmap.width - 1)
        val rawTop = (box.top - marginY).coerceIn(0, bitmap.height - 1)
        val rawRight = (box.right + marginX).coerceIn(rawLeft + 1, bitmap.width)
        val rawBottom = (box.bottom + marginY).coerceIn(rawTop + 1, bitmap.height)

        val w = rawRight - rawLeft
        val h = rawBottom - rawTop
        val size = maxOf(w, h)

        val centerX = rawLeft + w / 2
        val centerY = rawTop + h / 2

        val squareLeft = (centerX - size / 2).coerceIn(0, (bitmap.width - size).coerceAtLeast(0))
        val squareTop = (centerY - size / 2).coerceIn(0, (bitmap.height - size).coerceAtLeast(0))
        val actualDim = minOf(size, bitmap.width - squareLeft, bitmap.height - squareTop).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, squareLeft, squareTop, actualDim, actualDim)
    }

    fun alignFace(bitmap: Bitmap, face: Face, enabled: Boolean = true): Bitmap {
        val squareCrop = cropSquarePortrait(bitmap, face.boundingBox)

        if (!enabled) {
            return Bitmap.createScaledBitmap(squareCrop, ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE, true)
        }

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEye != null && rightEye != null) {
            val deltaX = (leftEye.x - rightEye.x).toDouble()
            val deltaY = (leftEye.y - rightEye.y).toDouble()
            val angleDegrees = Math.toDegrees(atan2(deltaY, deltaX)).toFloat()

            if (abs(angleDegrees) in 2.0f..40.0f) {
                val matrix = Matrix().apply {
                    postRotate(-angleDegrees, squareCrop.width / 2f, squareCrop.height / 2f)
                }
                val rotated = Bitmap.createBitmap(squareCrop, 0, 0, squareCrop.width, squareCrop.height, matrix, true)
                return Bitmap.createScaledBitmap(rotated, ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE, true)
            }
        }

        return Bitmap.createScaledBitmap(squareCrop, ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE, true)
    }
}
