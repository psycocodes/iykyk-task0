package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.iykyk.task0.ml.models.FaceQuality

interface QualityScorer {
    fun assessQuality(bitmap: Bitmap, face: Face): FaceQuality
}
