package com.iykyk.task0.ml.clustering

/**
 * Common data structure representing a cluster of items with a centroid embedding.
 */
data class IdentityCluster<T>(
    val clusterId: Int,
    val members: MutableList<T>,
    var centroid: FloatArray
)

/**
 * Unified clustering engine for facial embeddings across both live recording and debug inspection.
 */
object IdentityClustering {

    fun <T> cluster(
        items: List<T>,
        embeddingSelector: (T) -> FloatArray?,
        threshold: Float,
        onLog: ((String) -> Unit)? = null
    ): List<IdentityCluster<T>> {
        if (items.isEmpty()) return emptyList()

        val validItems = items.mapNotNull { item ->
            val emb = embeddingSelector(item)
            if (emb != null) item to emb else null
        }
        if (validItems.isEmpty()) return emptyList()

        val clusters = mutableListOf<IdentityCluster<T>>()
        var nextId = 1

        // Phase 1: Sequential centroid assignment
        for ((item, emb) in validItems) {
            var bestCluster: IdentityCluster<T>? = null
            var maxSim = -1f

            for (cluster in clusters) {
                val sim = EmbeddingMath.cosineSimilarity(emb, cluster.centroid)
                if (sim >= threshold && sim > maxSim) {
                    maxSim = sim
                    bestCluster = cluster
                }
            }

            if (bestCluster != null) {
                bestCluster.members.add(item)
                val allEmbs = bestCluster.members.mapNotNull { embeddingSelector(it) }
                bestCluster.centroid = EmbeddingMath.computeCentroid(allEmbs)
                onLog?.invoke("Assigned item to Cluster #${bestCluster.clusterId} (sim=${"%.3f".format(maxSim)})")
            } else {
                val newCluster = IdentityCluster(nextId++, mutableListOf(item), emb.copyOf())
                clusters.add(newCluster)
                onLog?.invoke("Created Cluster #${newCluster.clusterId} (max_sim=${if (maxSim >= 0) "%.3f".format(maxSim) else "none"} < $threshold)")
            }
        }

        // Phase 2: Post-clustering merge of clusters with centroid similarity >= threshold
        var iter = 0
        while (iter < clusters.size) {
            iter++
            var bestI = -1
            var bestJ = -1
            var bestSim = -1f

            for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val sim = EmbeddingMath.cosineSimilarity(clusters[i].centroid, clusters[j].centroid)
                    if (sim >= threshold && sim > bestSim) {
                        bestSim = sim
                        bestI = i
                        bestJ = j
                    }
                }
            }

            if (bestI != -1 && bestJ != -1) {
                onLog?.invoke("Merging Cluster #${clusters[bestI].clusterId} + Cluster #${clusters[bestJ].clusterId} (sim=${"%.3f".format(bestSim)})")
                clusters[bestI].members.addAll(clusters[bestJ].members)
                val allEmbs = clusters[bestI].members.mapNotNull { embeddingSelector(it) }
                clusters[bestI].centroid = EmbeddingMath.computeCentroid(allEmbs)
                clusters.removeAt(bestJ)
            } else {
                break
            }
        }

        // Phase 3: Refine centroids using the medoid sample
        for (cluster in clusters) {
            val embs = cluster.members.mapNotNull { embeddingSelector(it) }
            val medoidIdx = EmbeddingMath.findMedoidIndex(embs)
            if (medoidIdx in embs.indices) {
                cluster.centroid = embs[medoidIdx].copyOf()
            }
        }

        return clusters
    }
}
