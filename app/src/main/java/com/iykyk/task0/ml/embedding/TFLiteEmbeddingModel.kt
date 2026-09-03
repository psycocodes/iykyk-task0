package com.iykyk.task0.ml.embedding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

private const val TAG = "TFLiteEmbeddingModel"

/**
 * TensorFlow Lite inference engine running MobileFaceNet for 128D/512D facial embedding generation.
 *
 * Preprocesses 112x112 pixel inputs with standard normalization and applies L2 normalization
 * to output embedding feature vectors.
 *
 * @param context Application context used for opening asset files.
 */
class TFLiteEmbeddingModel(private val context: Context) : EmbeddingModel {
    private var interpreter: Interpreter? = null
    private var embeddingDim = 128

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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MobileFaceNet: ${e.message}", e)
        }
    }

    /**
     * Executes TFLite model inference on a normalized 112x112 face crop, returning an L2-normalized vector.
     */
    override suspend fun generateEmbedding(bitmap: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        val activeInterpreter = interpreter ?: return@withContext null
        return@withContext try {
            val resized = if (bitmap.width == 112 && bitmap.height == 112) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, 112, 112, true)
            }

            val inputBuffer = preprocessBitmap(resized)
            val outputArray = Array(1) { FloatArray(embeddingDim) }
            activeInterpreter.run(inputBuffer, outputArray)

            l2Normalize(outputArray[0])
        } catch (e: Exception) {
            Log.e(TAG, "Embedding inference error: ${e.message}", e)
            null
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

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) sumSquares += (v * v)
        val norm = sqrt(sumSquares).toFloat().coerceAtLeast(1e-10f)
        for (i in vector.indices) {
            vector[i] /= norm
        }
        return vector
    }

    /**
     * Releases TensorFlow Lite native interpreter and memory allocations.
     */
    override fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreter: ${e.message}")
        }
    }
}
