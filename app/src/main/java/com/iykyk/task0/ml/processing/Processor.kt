package com.iykyk.task0.ml.processing

import android.graphics.Bitmap
import android.util.Log
import com.iykyk.task0.ml.clustering.ClusteringEngine
import com.iykyk.task0.ml.clustering.RepresentativeSelector
import com.iykyk.task0.ml.config.MLPipelineConfig
import com.iykyk.task0.ml.embedding.EmbeddingGenerator
import com.iykyk.task0.ml.models.ClusterOutput
import com.iykyk.task0.ml.models.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "IYKYK_ML"

/**
 * Batch processor responsible for generating MobileFaceNet embeddings, clustering identities,
 * and selecting representative portraits for the collage.
 *
 * Emits incremental progress via [processingProgress] StateFlow.
 *
 * @param embeddingGenerator Generator computing TFLite face embeddings.
 * @param clusteringEngine Engine grouping face samples using cosine similarity.
 * @param representativeSelector Selector picking the sharpest portrait per cluster.
 * @param config Configuration parameters and boolean feature switches.
 * @param dispatcher Coroutine dispatcher executing batch operations.
 */
class Processor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val clusteringEngine: ClusteringEngine,
    private val representativeSelector: RepresentativeSelector,
    private val config: MLPipelineConfig = MLPipelineConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _processingProgress = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingProgress: StateFlow<ProcessingState> = _processingProgress.asStateFlow()

    private var currentJob: Job? = null

    /**
     * Processes detected face samples, extracting embeddings and clustering them into unique identities.
     *
     * @param detectedSamples List of tracked face samples gathered during recording.
     * @return ClusterOutput containing representative bitmaps and cluster statistics.
     */
    suspend fun processFaces(
        detectedSamples: List<BestFaceSample>
    ): ClusterOutput = withContext(dispatcher) {
        currentJob = coroutineContext[Job]
        val total = detectedSamples.size

        Log.i(TAG, "=== Processor: Starting Batch Face Processing ===")
        Log.i(
            TAG,
            "Input: $total face samples from tracking | enableClustering=${config.enableClustering} | similarityThreshold=${config.similarityThreshold}"
        )

        if (total == 0) {
            Log.w(TAG, "Processor: 0 face samples provided. Returning empty output.")
            _processingProgress.value = ProcessingState.Complete(emptyList())
            return@withContext ClusterOutput(emptyList(), emptyList(), 0, false)
        }

        if (!config.enableClustering) {
            Log.i(TAG, "Processor: Clustering DISABLED. Directly returning all $total samples.")
            val directList = detectedSamples.map { it.faceCrop }
            _processingProgress.value = ProcessingState.Complete(directList)
            return@withContext ClusterOutput(
                clusters = emptyList(),
                representativeBitmaps = directList,
                totalProcessed = directList.size,
                wasCancelled = false
            )
        }

        val embeddings = mutableListOf<Pair<Bitmap, FloatArray>>()
        var wasCancelled = false

        try {
            for ((index, sample) in detectedSamples.withIndex()) {
                if (currentJob?.isCancelled == true) {
                    wasCancelled = true
                    Log.w(TAG, "Processor: Processing job cancelled at sample #$index")
                    break
                }

                val embedding = embeddingGenerator.generateEmbedding(sample.fullBitmap, sample.face)
                if (embedding != null) {
                    embeddings.add(sample.faceCrop to embedding)
                    Log.d(TAG, "  [Embedding #$index]: Generated successfully (vector dim=${embedding.size})")
                } else {
                    Log.w(TAG, "  [Embedding #$index]: FAILED to generate embedding for sample #$index!")
                }

                _processingProgress.value = ProcessingState.Embedding(index + 1, total)
            }

            Log.i(TAG, "Processor: Completed embeddings (${embeddings.size}/$total succeeded). Starting clustering...")
            _processingProgress.value = ProcessingState.Clustering

            val clusters = clusteringEngine.clusterEmbeddings(
                embeddings,
                threshold = config.similarityThreshold
            )

            _processingProgress.value = ProcessingState.SelectingRepresentatives
            Log.i(TAG, "Processor: Selecting representative portraits for ${clusters.size} cluster(s)...")

            val representatives = clusters.map { cluster ->
                representativeSelector.selectRepresentative(cluster)
            }

            Log.i(TAG, "Processor: All processing complete. ${representatives.size} final face portrait(s) selected.")
            _processingProgress.value = ProcessingState.Complete(representatives)

            return@withContext ClusterOutput(
                clusters = clusters,
                representativeBitmaps = representatives,
                totalProcessed = embeddings.size,
                wasCancelled = wasCancelled
            )
        } catch (e: CancellationException) {
            Log.w(TAG, "Processor: CancellationException caught.")
            _processingProgress.value = ProcessingState.Cancelled
            return@withContext ClusterOutput(emptyList(), emptyList(), embeddings.size, true)
        } catch (e: Exception) {
            Log.e(TAG, "Processor: Error during processing: ${e.message}", e)
            _processingProgress.value = ProcessingState.Error(e.message ?: "Processing error")
            return@withContext ClusterOutput(emptyList(), emptyList(), embeddings.size, false)
        }
    }

    /**
     * Cancels any currently executing background processing job.
     */
    fun cancelProcessing() {
        currentJob?.cancel()
    }
}
