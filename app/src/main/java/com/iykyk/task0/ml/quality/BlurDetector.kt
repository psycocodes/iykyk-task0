package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * Fast, pure-Kotlin Laplacian variance blur detector:
 * Approximates variance of the Laplacian kernel across the face crop.
 */
class LaplacianBlurDetector(
    private val minBlurScore: Float = 120f
) : FaceValidator {
    private var lastReason = ""

    override fun isValid(bitmap: Bitmap, face: Face, frameWidth: Int, frameHeight: Int): Boolean {
        val cropBitmap = cropFaceRegion(bitmap, face)
        val variance = calculateLaplacianVariance(cropBitmap)
        if (variance < minBlurScore) {
            lastReason = "Blurry face crop: variance ${"%.1f".format(variance)} < $minBlurScore"
            return false
        }
        return true
    }

    override fun getReason(): String = lastReason

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

    private fun cropFaceRegion(bitmap: Bitmap, face: Face): Bitmap {
        val box = face.boundingBox
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun luminance(color: Int): Double {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
