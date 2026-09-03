package com.iykyk.task0.ml.clustering

import android.graphics.Bitmap
import android.util.Log
import com.iykyk.task0.ml.models.FaceCluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "IYKYK_ML"

/**
 * Online clustering engine grouping face feature vectors by cosine similarity.
 *
 * Evaluates candidates against dynamically maintained centroid representations.
 */
class CosineClusteringEngine : ClusteringEngine {

    /**
     * Clusters face feature embeddings based on cosine similarity exceeding a given threshold.
     *
     * @param faces List of paired portrait bitmaps and 512D/128D float feature vectors.
     * @param threshold Minimum cosine similarity required to merge into an existing identity cluster.
     * @return List of identified FaceCluster instances.
     */
    override suspend fun clusterEmbeddings(
        faces: List<Pair<Bitmap, FloatArray>>,
        threshold: Float
    ): List<FaceCluster> = withContext(Dispatchers.Default) {
        val clusters = mutableListOf<FaceCluster>()
        var nextClusterId = 1

        Log.i(TAG, "=== Starting Cosine Similarity Clustering ===")
        Log.i(TAG, "Total incoming face embeddings: ${faces.size}, Similarity Threshold: $threshold")

        for ((index, facePair) in faces.withIndex()) {
            val (bitmap, embedding) = facePair
            var bestCluster: FaceCluster? = null
            var maxSim = -1.0f

            val similarityLog = StringBuilder()
            similarityLog.append("  [Face #$index (${bitmap.width}x${bitmap.height})]: ")

            if (clusters.isEmpty()) {
                similarityLog.append("No existing clusters -> ")
            } else {
                for (cluster in clusters) {
                    val sim = EmbeddingMath.cosineSimilarity(embedding, cluster.centroid)
                    similarityLog.append("Cluster #${cluster.clusterId}(sim=${"%.4f".format(sim)}), ")
                    if (sim >= threshold && sim > maxSim) {
                        maxSim = sim
                        bestCluster = cluster
                    }
                }
            }

            if (bestCluster != null) {
                bestCluster.faces.add(bitmap to embedding)
                bestCluster.centroid = EmbeddingMath.computeCentroid(bestCluster.faces.map { it.second })
                similarityLog.append("==> MERGED into Cluster #${bestCluster.clusterId} (sim=${"%.4f".format(maxSim)} >= threshold $threshold, new size=${bestCluster.faces.size})")
            } else {
                val newCluster = FaceCluster(
                    clusterId = nextClusterId++,
                    faces = mutableListOf(bitmap to embedding),
                    centroid = embedding.copyOf()
                )
                clusters.add(newCluster)
                similarityLog.append("==> NO MATCH >= $threshold. CREATED Cluster #${newCluster.clusterId}")
            }

            Log.i(TAG, similarityLog.toString())
        }

        Log.i(TAG, "=== Clustering Results: ${clusters.size} Unique Clusters Identified ===")
        for (cluster in clusters) {
            Log.i(TAG, "  * Cluster #${cluster.clusterId}: ${cluster.faces.size} face portrait(s)")
        }

        return@withContext clusters
    }
}
