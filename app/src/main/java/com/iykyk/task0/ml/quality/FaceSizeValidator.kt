package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

class FaceSizeValidator(private val minSize: Int = 50) : FaceValidator {
    private var lastReason = ""

    override fun isValid(bitmap: Bitmap, face: Face, frameWidth: Int, frameHeight: Int): Boolean {
        val width = face.boundingBox.width()
        val height = face.boundingBox.height()
        if (width < minSize || height < minSize) {
            lastReason = "Face size too small: ${width}x${height}px < ${minSize}px"
            return false
        }
        return true
    }

    override fun getReason(): String = lastReason
}
