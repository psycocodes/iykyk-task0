package com.iykyk.task0.ml.processing

/**
 * High-level coordinator grouping real-time detection and batch processing stages.
 *
 * @property detector Real-time face tracking engine.
 * @property processor Post-recording clustering and embedding engine.
 */
class FaceProcessingPipeline(
    val detector: Detector,
    val processor: Processor
) {
    /**
     * Resets active detection tracks and prepares the pipeline for a new recording.
     */
    suspend fun reset() {
        detector.clear()
    }
}
