package com.iykyk.task0.ml.clustering

import android.graphics.Bitmap
import android.util.Log
import com.iykyk.task0.ml.models.FaceCluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "IYKYK_ML"

/**
 * Cosine similarity clustering engine for facial embeddings.
 */
class CosineClusteringEngine : ClusteringEngine {

    override suspend fun clusterEmbeddings(
        faces: List<Pair<Bitmap, FloatArray>>,
        threshold: Float
    ): List<FaceCluster> = withContext(Dispatchers.Default) {
        Log.i(TAG, "Starting Cosine Similarity Clustering: ${faces.size} faces, threshold=$threshold")

        if (faces.isEmpty()) return@withContext emptyList()

        val clusters = IdentityClustering.cluster(
            items = faces,
            embeddingSelector = { it.second },
            threshold = threshold,
            onLog = { Log.d(TAG, "  [Cluster] $it") }
        )

        val result = clusters.map { c ->
            FaceCluster(
                clusterId = c.clusterId,
                faces = c.members,
                centroid = c.centroid
            )
        }

        Log.i(TAG, "Clustering complete: ${result.size} unique identities formed")
        return@withContext result
    }
}
