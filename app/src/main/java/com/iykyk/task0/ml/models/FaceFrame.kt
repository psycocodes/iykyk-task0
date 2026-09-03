package com.iykyk.task0.ml.models

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

data class FaceFrame(
    val timestampMs: Long,
    val faceCrop: Bitmap,
    val fullBitmap: Bitmap,
    val face: Face,
    val quality: FaceQuality
)
