package com.iykyk.task0.debug.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.iykyk.task0.ml.clustering.EmbeddingMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "DebugTFLiteModel"

class DebugTFLiteModel(context: Context) {
    private var interpreter: Interpreter? = null
    private val inferenceMutex = Mutex()
    var embeddingDim = 192
        private set

    init {
        try {
            val assetManager = context.assets
            val modelBytes = assetManager.open("mobilefacenet.tflite").use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(modelBytes)
                rewind()
            }
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(buffer, options)

            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            if (outputShape != null && outputShape.isNotEmpty()) {
                embeddingDim = outputShape.last()
            }
            Log.d(TAG, "Loaded mobilefacenet.tflite successfully. Output dim=$embeddingDim")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MobileFaceNet: ${e.message}", e)
        }
    }

    suspend fun generateEmbedding(bitmap: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            val activeInterpreter = interpreter ?: return@withLock null
            return@withLock try {
                val resized = if (bitmap.width == 112 && bitmap.height == 112) {
                    bitmap
                } else {
                    Bitmap.createScaledBitmap(bitmap, 112, 112, true)
                }

                val inputBuffer = preprocessBitmap(resized)
                val outputArray = Array(1) { FloatArray(embeddingDim) }
                activeInterpreter.run(inputBuffer, outputArray)

                EmbeddingMath.l2Normalize(outputArray[0])
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}", e)
                null
            }
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }
        val pixels = IntArray(112 * 112)
        bitmap.getPixels(pixels, 0, 112, 0, 0, 112, 112)

        for (pixel in pixels) {
            val r = (Color.red(pixel) - 127.5f) / 128.0f
            val g = (Color.green(pixel) - 127.5f) / 128.0f
            val b = (Color.blue(pixel) - 127.5f) / 128.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
        return buffer
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (_: Exception) {}
    }

    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float = EmbeddingMath.cosineSimilarity(a, b)
        fun computeCentroid(embeddings: List<FloatArray>): FloatArray = EmbeddingMath.computeCentroid(embeddings)
        fun findMedoidIndex(embeddings: List<FloatArray>): Int = EmbeddingMath.findMedoidIndex(embeddings)
    }
}
