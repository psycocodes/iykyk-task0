package com.iykyk.task0.ml.embedding

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.atan2

class FaceAligner(
    val enabled: Boolean = true
) {
    companion object {
        const val ALIGNED_FACE_SIZE = 112 // MobileFaceNet standard input dimension
    }

    /**
     * Aligns a face horizontally using eye landmarks and scales to standard 112x112 size.
     *
     * Extracts a 1:1 square crop around the face with 20% margin to prevent distortion,
     * levels the eye line horizontally around the center of the crop, and scales to 112x112.
     */
    fun alignFace(bitmap: Bitmap, face: Face): Bitmap {
        val squareCrop = cropSquarePortrait(bitmap, face.boundingBox)

        if (!enabled) {
            return Bitmap.createScaledBitmap(squareCrop, ALIGNED_FACE_SIZE, ALIGNED_FACE_SIZE, true)
        }

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEye != null && rightEye != null) {
            // Note: From viewer perspective, leftEye is on the right (higher X), rightEye is on the left (lower X).
            val deltaX = (leftEye.x - rightEye.x).toDouble()
            val deltaY = (leftEye.y - rightEye.y).toDouble()
            val angleDegrees = Math.toDegrees(atan2(deltaY, deltaX)).toFloat()

            // Level the eyes: rotate by -angleDegrees around the center of the square crop
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

    private fun cropSquarePortrait(bitmap: Bitmap, box: Rect): Bitmap {
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
}
