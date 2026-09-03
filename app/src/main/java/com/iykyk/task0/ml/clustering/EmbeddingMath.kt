package com.iykyk.task0.ml.clustering

import kotlin.math.sqrt

/**
 * Mathematical utilities for normalized embeddings and clustering operations.
 */
object EmbeddingMath {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) {
            return 0f
        }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            val va = if (a[i].isNaN() || a[i].isInfinite()) 0.0 else a[i].toDouble()
            val vb = if (b[i].isNaN() || b[i].isInfinite()) 0.0 else b[i].toDouble()
            dotProduct += (va * vb)
            normA += (va * va)
            normB += (vb * vb)
        }

        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 1e-10 || dotProduct.isNaN()) return 0f
        return (dotProduct / denom).toFloat().coerceIn(-1f, 1f)
    }

    fun computeCentroid(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) {
            return FloatArray(0)
        }

        val dim = embeddings[0].size
        val centroid = FloatArray(dim)

        for (embedding in embeddings) {
            for (i in 0 until dim) {
                val v = if (embedding[i].isNaN() || embedding[i].isInfinite()) 0f else embedding[i]
                centroid[i] += v
            }
        }

        val count = embeddings.size.toFloat()
        for (i in 0 until dim) {
            centroid[i] /= count
        }

        return l2Normalize(centroid)
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0

        for (v in vector) {
            val valid = if (v.isNaN() || v.isInfinite()) 0.0 else v.toDouble()
            sumSquares += (valid * valid)
        }

        val norm = sqrt(sumSquares).toFloat().coerceAtLeast(1e-10f)

        for (i in vector.indices) {
            val valid = if (vector[i].isNaN() || vector[i].isInfinite()) 0f else vector[i]
            vector[i] = valid / norm
        }

        return vector
    }

    fun findMedoidIndex(embeddings: List<FloatArray>): Int {
        if (embeddings.isEmpty()) return -1
        if (embeddings.size == 1) return 0

        val centroid = computeCentroid(embeddings)
        var bestIdx = 0
        var bestSim = -1f

        for ((idx, emb) in embeddings.withIndex()) {
            val sim = cosineSimilarity(emb, centroid)
            if (sim > bestSim) {
                bestSim = sim
                bestIdx = idx
            }
        }

        return bestIdx
    }
}
