package com.iykyk.task0.ml.clustering

import android.graphics.Bitmap
import android.util.Log
import com.iykyk.task0.ml.models.FaceCluster
import com.iykyk.task0.ml.quality.SharpnessValidator

private const val TAG = "IYKYK_ML"

/**
 * Strategy interface for selecting a single representative face portrait from a cluster.
 */
interface RepresentativeSelector {
    /**
     * Evaluates face samples in a cluster and returns the most representative portrait bitmap.
     */
    fun selectRepresentative(cluster: FaceCluster): Bitmap
}

/**
 * Quality-driven representative face selector combining Sobel sharpness, resolution size,
 * and centroid embedding proximity.
 *
 * @param sharpnessValidator Validator used to compute edge magnitude sharpness.
 */
class QualityBasedRepresentativeSelector(
    private val sharpnessValidator: SharpnessValidator
) : RepresentativeSelector {

    override fun selectRepresentative(cluster: FaceCluster): Bitmap {
        if (cluster.faces.isEmpty()) throw IllegalArgumentException("Cluster cannot be empty")
        if (cluster.faces.size == 1) {
            Log.d(TAG, "  [Cluster #${cluster.clusterId}]: Single face member. Selected as representative.")
            return cluster.faces[0].first
        }

        var bestBitmap = cluster.faces[0].first
        var bestScore = Float.NEGATIVE_INFINITY
        var bestIndex = 0

        for ((index, facePair) in cluster.faces.withIndex()) {
            val (bitmap, embedding) = facePair
            var score = 0f
            val sharpness = sharpnessValidator.calculateSobelSharpness(bitmap)
            val sizeScore = (bitmap.width * bitmap.height).toFloat() / 10000f
            val similarity = EmbeddingMath.cosineSimilarity(embedding, cluster.centroid)

            score += (sharpness / 1000f)
            score += sizeScore
            score += (similarity * 100f)

            Log.d(
                TAG,
                "  [Cluster #${cluster.clusterId} Candidate #$index]: score=${"%.2f".format(score)} (sharpness=${"%.1f".format(sharpness)}, size=${bitmap.width}x${bitmap.height}, centroidSim=${"%.4f".format(similarity)})"
            )

            if (score > bestScore) {
                bestScore = score
                bestBitmap = bitmap
                bestIndex = index
            }
        }

        Log.i(
            TAG,
            "  [Cluster #${cluster.clusterId} WINNER]: Candidate #$bestIndex selected as representative (bestScore=${"%.2f".format(bestScore)})"
        )
        return bestBitmap
    }
}
