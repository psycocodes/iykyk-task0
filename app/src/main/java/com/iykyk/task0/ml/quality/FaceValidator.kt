package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

interface FaceValidator {
    fun isValid(bitmap: Bitmap, face: Face, frameWidth: Int, frameHeight: Int): Boolean
    fun getReason(): String
}
