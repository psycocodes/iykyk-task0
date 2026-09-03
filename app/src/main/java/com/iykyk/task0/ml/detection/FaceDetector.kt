package com.iykyk.task0.ml.detection

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

interface FaceDetector {
    suspend fun detectFaces(bitmap: Bitmap, rotationDegrees: Int = 0): List<Face>
    fun close()
}
