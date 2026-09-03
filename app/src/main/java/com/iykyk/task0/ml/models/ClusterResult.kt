package com.iykyk.task0.ml.models

import android.graphics.Bitmap

data class FaceCluster(
    val clusterId: Int,
    val faces: MutableList<Pair<Bitmap, FloatArray>>,
    var centroid: FloatArray
)

data class ClusterOutput(
    val clusters: List<FaceCluster>,
    val representativeBitmaps: List<Bitmap>,
    val totalProcessed: Int,
    val wasCancelled: Boolean
)
