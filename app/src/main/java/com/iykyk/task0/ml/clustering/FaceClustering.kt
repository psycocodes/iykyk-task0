package com.iykyk.task0.ml.clustering

import android.graphics.Bitmap
import com.iykyk.task0.ml.models.FaceCluster

interface ClusteringEngine {
    suspend fun clusterEmbeddings(
        faces: List<Pair<Bitmap, FloatArray>>,
        threshold: Float = 0.60f
    ): List<FaceCluster>
}
