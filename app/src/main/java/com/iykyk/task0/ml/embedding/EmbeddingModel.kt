package com.iykyk.task0.ml.embedding

import android.graphics.Bitmap

interface EmbeddingModel {
    suspend fun generateEmbedding(bitmap: Bitmap): FloatArray?
    fun close()
}
