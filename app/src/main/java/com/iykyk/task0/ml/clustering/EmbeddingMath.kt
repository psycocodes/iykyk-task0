package com.iykyk.task0.ml.clustering

import kotlin.math.sqrt

object EmbeddingMath {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += (a[i] * b[i])
            normA += (a[i] * a[i])
            normB += (b[i] * b[i])
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-9) (dot / denom).toFloat() else 0f
    }

    fun computeCentroid(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        val dim = embeddings[0].size
        val centroid = FloatArray(dim)
        for (emb in embeddings) {
            for (i in 0 until dim) {
                centroid[i] += emb[i]
            }
        }
        val count = embeddings.size.toFloat()
        var sumSquares = 0.0
        for (i in 0 until dim) {
            centroid[i] /= count
            sumSquares += (centroid[i] * centroid[i])
        }
        val norm = sqrt(sumSquares).toFloat().coerceAtLeast(1e-10f)
        for (i in 0 until dim) {
            centroid[i] /= norm
        }
        return centroid
    }
}
