package com.iykyk.task0.ml.processing

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.iykyk.task0.ml.config.MLPipelineConfig
import com.iykyk.task0.ml.detection.FaceDetector
import com.iykyk.task0.ml.detection.FaceQualityFilter
import com.iykyk.task0.ml.models.FaceFrame
import com.iykyk.task0.ml.models.FaceTrack
import com.iykyk.task0.debug.DebugSessionHolder
import com.iykyk.task0.ml.models.FaceQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "IYKYK_ML"

/**
 * Encapsulates the highest quality portrait sample extracted from a tracked face.
 *
 * @property faceCrop Tight square crop of the face with breathing room for hair and chin.
 * @property fullBitmap Full video frame bitmap from which the crop originated.
 * @property face MLKit Face metadata containing landmarks and bounding box.
 * @property sharpnessScore Quantitative Laplacian sharpness score.
 */
data class BestFaceSample(
    val faceCrop: Bitmap,
    val fullBitmap: Bitmap,
    val face: Face,
    val sharpnessScore: Float
)

/**
 * Real-time video frame face detector and temporal centroid tracker.
 *
 * Tracks individual face identities across consecutive camera frames, filters low quality
 * samples, and maintains the sharpest square crop for each tracked person.
 *
 * @param faceDetector Underlying MLKit face detection engine.
 * @param qualityFilter Quality filtering pipeline evaluating size, frontality, and sharpness.
 * @param config Configuration parameters governing tracking distances and thresholds.
 */
class Detector(
    private val faceDetector: FaceDetector,
    private val qualityFilter: FaceQualityFilter,
    private val config: MLPipelineConfig = MLPipelineConfig()
) {
    private val faceTracks = mutableMapOf<String, FaceTrack>()
    private val directFaceSamples = mutableListOf<BestFaceSample>()
    private val mutex = Mutex()
    private var processedFrameCount = 0

    /**
     * Processes a single camera frame, detecting faces and updating temporal tracking trajectories.
     *
     * @param bitmap Upright video frame bitmap.
     * @param rotationDegrees Sensor rotation degrees applied to the frame.
     * @param frameTimeMs Timestamp of the video frame in milliseconds.
     * @return List of detected MLKit Face instances.
     */
    suspend fun processVideoFrame(
        bitmap: Bitmap,
        rotationDegrees: Int,
        frameTimeMs: Long
    ): List<Face> = withContext(Dispatchers.Default) {
        processedFrameCount++
        val detectedFaces = faceDetector.detectFaces(bitmap, rotationDegrees)
        if (detectedFaces.isEmpty()) {
            DebugSessionHolder.recordEmptyLiveFrame(processedFrameCount, frameTimeMs, bitmap)
            return@withContext emptyList()
        }

        val qualityFaceFrames = mutableListOf<FaceFrame>()
        val faceQualities = mutableListOf<Pair<Face, FaceQuality>>()

        for ((idx, face) in detectedFaces.withIndex()) {
            val quality = qualityFilter.filterQualityFaces(bitmap, face, bitmap.width, bitmap.height)
            faceQualities.add(face to quality)
            val crop = cropSquarePortrait(bitmap, face.boundingBox)
            val faceFrame = FaceFrame(frameTimeMs, crop, bitmap, face, quality)
            qualityFaceFrames.add(faceFrame)

            if (!config.enableTracking) {
                // Collect all detected face cuts into directFaceSamples; preprocessing in Processor filters edge clipping, blur, etc.
                mutex.withLock {
                    directFaceSamples.add(
                        BestFaceSample(
                            faceCrop = crop,
                            fullBitmap = bitmap,
                            face = face,
                            sharpnessScore = quality.sharpnessScore
                        )
                    )
                }
            }

            if (quality.isValid) {
                Log.d(
                    TAG,
                    "[Frame #$processedFrameCount] Face #$idx PASSED: yaw=${"%.1f".format(quality.yaw)}°, pitch=${"%.1f".format(quality.pitch)}°, sharpness=${"%.1f".format(quality.sharpnessScore)}, blur=${"%.1f".format(quality.blurScore)}, box=${face.boundingBox}"
                )
            } else {
                Log.w(
                    TAG,
                    "[Frame #$processedFrameCount] Face #$idx flagged for preprocessing filter: reason='${quality.failureReason}', box=${face.boundingBox}"
                )
            }
        }

        if (config.enableTracking && qualityFaceFrames.isNotEmpty()) {
            mutex.withLock {
                linkFacesAcrossFrames(qualityFaceFrames)
            }
        }

        DebugSessionHolder.recordLiveFrame(
            frameIndex = processedFrameCount,
            timestampMs = frameTimeMs,
            bitmap = bitmap,
            detectedFaces = faceQualities,
            cropExtractor = ::cropSquarePortrait
        )

        return@withContext detectedFaces
    }

    private fun cropSquarePortrait(bitmap: Bitmap, box: Rect): Bitmap {
        val marginX = (box.width() * 0.20f).toInt()
        val marginY = (box.height() * 0.20f).toInt()

        val rawLeft = (box.left - marginX).coerceIn(0, bitmap.width - 1)
        val rawTop = (box.top - marginY).coerceIn(0, bitmap.height - 1)
        val rawRight = (box.right + marginX).coerceIn(rawLeft + 1, bitmap.width)
        val rawBottom = (box.bottom + marginY).coerceIn(rawTop + 1, bitmap.height)

        val w = rawRight - rawLeft
        val h = rawBottom - rawTop
        val size = maxOf(w, h)

        val centerX = rawLeft + w / 2
        val centerY = rawTop + h / 2

        val squareLeft = (centerX - size / 2).coerceIn(0, (bitmap.width - size).coerceAtLeast(0))
        val squareTop = (centerY - size / 2).coerceIn(0, (bitmap.height - size).coerceAtLeast(0))
        val actualDim = minOf(size, bitmap.width - squareLeft, bitmap.height - squareTop).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, squareLeft, squareTop, actualDim, actualDim)
    }

    private fun linkFacesAcrossFrames(frames: List<FaceFrame>) {
        val availableTrackIds = faceTracks.keys.toMutableSet()
        val unassignedFrames = mutableListOf<FaceFrame>()

        for (frame in frames) {
            val centroid = getCentroid(frame.face)
            var bestTrackId: String? = null
            var minDistance = config.trackingCentroidThreshold

            for (trackId in availableTrackIds) {
                val track = faceTracks[trackId] ?: continue
                val lastFace = track.frames.lastOrNull()?.face ?: continue
                val lastCentroid = getCentroid(lastFace)
                val dist = distance(centroid, lastCentroid)
                if (dist < minDistance) {
                    minDistance = dist
                    bestTrackId = trackId
                }
            }

            if (bestTrackId != null) {
                val track = faceTracks[bestTrackId]!!
                track.frames.add(frame)
                availableTrackIds.remove(bestTrackId)

                if (track.frames.size > 5) {
                    val best = track.frames.maxByOrNull { it.quality.sharpnessScore }
                    track.frames.clear()
                    best?.let { track.frames.add(it) }
                }
                Log.d(
                    TAG,
                    "[Tracking] Matched face at (${"%.1f".format(centroid.x)}, ${"%.1f".format(centroid.y)}) to Track '$bestTrackId' (dist=${"%.1f".format(minDistance)}px < threshold ${config.trackingCentroidThreshold}px)"
                )
            } else {
                unassignedFrames.add(frame)
            }
        }

        for (frame in unassignedFrames) {
            val centroid = getCentroid(frame.face)
            val newTrackId = "track_${faceTracks.size + 1}_${UUID.randomUUID().toString().take(4)}"
            faceTracks[newTrackId] = FaceTrack(
                trackId = newTrackId,
                personId = "person_${faceTracks.size + 1}",
                frames = mutableListOf(frame)
            )
            Log.i(
                TAG,
                "[Tracking] Created NEW Track '$newTrackId' for face at (${"%.1f".format(centroid.x)}, ${"%.1f".format(centroid.y)}) (total tracks: ${faceTracks.size})"
            )
        }
    }

    private fun getCentroid(face: Face): PointF {
        val box = face.boundingBox
        return PointF(box.exactCenterX(), box.exactCenterY())
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    /**
     * Extracts the best quality representative portrait sample for each tracked person.
     *
     * @return List of BestFaceSample objects for downstream embedding and clustering.
     */
    suspend fun getBestFacesPerTrack(): List<BestFaceSample> = mutex.withLock {
        if (!config.enableTracking) {
            Log.i(TAG, "=== Yielded ${directFaceSamples.size} direct per-frame face samples (Tracking DISABLED) ===")
            return directFaceSamples.toList()
        }

        val result = mutableListOf<BestFaceSample>()
        Log.i(TAG, "=== Extracted Tracks Summary (${faceTracks.size} total tracks) ===")
        for ((trackId, track) in faceTracks) {
            if (track.frames.isNotEmpty()) {
                val best = track.frames.maxByOrNull { it.quality.sharpnessScore }
                if (best != null) {
                    result.add(
                        BestFaceSample(
                            faceCrop = best.faceCrop,
                            fullBitmap = best.fullBitmap,
                            face = best.face,
                            sharpnessScore = best.quality.sharpnessScore
                        )
                    )
                    Log.d(
                        TAG,
                        "  * Track '$trackId': ${track.frames.size} frame(s), best sample sharpness=${"%.1f".format(best.quality.sharpnessScore)}, cropSize=${best.faceCrop.width}x${best.faceCrop.height}"
                    )
                }
            } else {
                Log.w(TAG, "  * Track '$trackId': 0 frames (skipped)")
            }
        }
        Log.i(TAG, "=== Yielded ${result.size} face samples for ML processing ===")
        return result
    }

    /**
     * Returns the current count of active temporal face tracks or direct face samples.
     */
    suspend fun getTrackCount(): Int = mutex.withLock {
        return if (config.enableTracking) faceTracks.size else directFaceSamples.size
    }

    /**
     * Resets all internal tracking state, clearing active face tracks and direct samples.
     */
    suspend fun clear() = mutex.withLock {
        faceTracks.clear()
        directFaceSamples.clear()
        processedFrameCount = 0
        Log.i(TAG, "[Detector] Cleared all tracks and face samples, reset frame count.")
    }
}
