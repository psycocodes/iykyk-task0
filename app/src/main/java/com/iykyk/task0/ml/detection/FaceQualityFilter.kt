package com.iykyk.task0.ml.detection

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.iykyk.task0.ml.models.FaceQuality
import com.iykyk.task0.ml.quality.FaceValidator
import com.iykyk.task0.ml.quality.LaplacianBlurDetector
import com.iykyk.task0.ml.quality.SharpnessValidator

/**
 * Executes a chain of modular validators across a detected face.
 */
class FaceQualityFilter(
    private val validators: List<FaceValidator>,
    private val blurDetector: LaplacianBlurDetector,
    private val sharpnessValidator: SharpnessValidator
) {
    fun filterQualityFaces(
        bitmap: Bitmap,
        face: Face,
        frameWidth: Int,
        frameHeight: Int
    ): FaceQuality {
        for (validator in validators) {
            if (!validator.isValid(bitmap, face, frameWidth, frameHeight)) {
                return FaceQuality(
                    isValid = false,
                    blurScore = 0f,
                    sharpnessScore = 0f,
                    yaw = face.headEulerAngleY,
                    pitch = face.headEulerAngleX,
                    roll = face.headEulerAngleZ,
                    failureReason = validator.getReason()
                )
            }
        }

        val crop = cropFaceRegion(bitmap, face)
        val blur = blurDetector.calculateLaplacianVariance(crop)
        val sharpness = sharpnessValidator.calculateSobelSharpness(crop)

        return FaceQuality(
            isValid = true,
            blurScore = blur,
            sharpnessScore = sharpness,
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            roll = face.headEulerAngleZ
        )
    }

    private fun cropFaceRegion(bitmap: Bitmap, face: Face): Bitmap {
        val box = face.boundingBox
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}
