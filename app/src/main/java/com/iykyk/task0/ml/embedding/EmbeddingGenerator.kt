package com.iykyk.task0.ml.embedding

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

class EmbeddingGenerator(
    private val embeddingModel: EmbeddingModel,
    private val faceAligner: FaceAligner
) {
    suspend fun generateEmbedding(bitmap: Bitmap, face: Face): FloatArray? {
        val alignedFace = faceAligner.alignFace(bitmap, face)
        return embeddingModel.generateEmbedding(alignedFace)
    }

    fun close() {
        embeddingModel.close()
    }
}
