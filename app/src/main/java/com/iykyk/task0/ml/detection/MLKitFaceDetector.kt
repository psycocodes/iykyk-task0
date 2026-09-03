package com.iykyk.task0.ml.detection

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MLKitFaceDetector"

/**
 * ML Kit Face Detector:
 * Configured with ACCURATE mode, landmark detection, and a sensitive minFaceSize so faces at any distance are detected.
 */
class MLKitFaceDetector : FaceDetector {
    private val detector: com.google.mlkit.vision.face.FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.02f) // Detect faces down to ~2% of frame (reliable for medium/far distance)
            .build()
        detector = FaceDetection.getClient(options)
        Log.d(TAG, "Initialized MLKitFaceDetector with minFaceSize 0.02f")
    }

    override suspend fun detectFaces(bitmap: Bitmap, rotationDegrees: Int): List<Face> = withContext(Dispatchers.Default) {
        return@withContext try {
            val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
            val task = detector.process(inputImage)
            Tasks.await(task)
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}", e)
            emptyList()
        }
    }

    override fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing detector: ${e.message}")
        }
    }
}
