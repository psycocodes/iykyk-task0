package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.face.Face
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Sobel Edge Sharpness Validator.
 */
class SharpnessValidator(
    private val minSharpness: Float = 600f
) : FaceValidator {
    private var lastReason = ""

    override fun isValid(bitmap: Bitmap, face: Face, frameWidth: Int, frameHeight: Int): Boolean {
        val crop = cropFaceRegion(bitmap, face)
        val score = calculateSobelSharpness(crop)
        if (score < minSharpness) {
            lastReason = "Low edge sharpness: ${"%.1f".format(score)} < $minSharpness"
            return false
        }
        return true
    }

    override fun getReason(): String = lastReason

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

    private fun cropFaceRegion(bitmap: Bitmap, face: Face): Bitmap {
        val box = face.boundingBox
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun luminance(color: Int): Double {
        return 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
    }
}
