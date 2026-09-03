package com.iykyk.task0.debug.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.task0.debug.ExtractedVideoFrame
import com.iykyk.task0.ml.quality.EdgeClippingValidator
import com.iykyk.task0.ml.clustering.IdentityClustering
import com.iykyk.task0.ml.clustering.EmbeddingMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "IYKYK_DEBUG"

data class DebugConfig(
    val enableTracking: Boolean = false,
    val trackingCentroidThreshold: Float = 120f,
    val enableEdgeClippingFilter: Boolean = true,
    val maxMissingFaceRatio: Float = 0.75f,
    val minBorderMargin: Int = 20,
    val enableFrontalityFilter: Boolean = false,
    val enableBlurFilter: Boolean = false,
    val enableSharpnessFilter: Boolean = false,
    val enableFaceAlignment: Boolean = true,
    val maxYaw: Float = 55f,
    val maxPitch: Float = 45f,
    val maxRoll: Float = 45f,
    val minBlurScore: Float = 8f,
    val minSharpness: Float = 600f,
    val similarityThreshold: Float = 0.65f
)

class DebugPipelineRunner(private val context: Context) {
    private val tfliteModel = DebugTFLiteModel(context)
    private val mlKitDetector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.02f)
            .build()
        mlKitDetector = FaceDetection.getClient(options)
    }

    suspend fun runPipeline(
        frames: List<ExtractedVideoFrame>,
        config: DebugConfig,
        onLog: (String) -> Unit = {},
        onProgress: (status: String, progress: Float) -> Unit = { _, _ -> }
    ): DebugPipelineResult = withContext(Dispatchers.Default) {
        val pipelineStart = System.currentTimeMillis()
        val sampledFrameItems = mutableListOf<SampledFrameItem>()
        val frameFilterItems = mutableListOf<FrameFilterItem>()
        val boundingBoxCutItems = mutableListOf<BoundingBoxCutItem>()
        val allDetectedFaces = mutableListOf<DetectedFaceItem>()
        var faceIdCounter = 1

        val totalFrames = frames.size
        onLog("=== Step 1: Sampling & Detection on $totalFrames frames ===")
        Log.i(TAG, "=== Starting ML Pipeline on $totalFrames frames ===")

        for ((fIdx, frame) in frames.withIndex()) {
            onProgress("Checking Frame ${fIdx + 1}/$totalFrames for faces", (fIdx.toFloat() / totalFrames) * 0.40f)

            val inputImage = InputImage.fromBitmap(frame.bitmap, 0)
            val mlkitStart = System.currentTimeMillis()
            val faces = try {
                val task = mlKitDetector.process(inputImage)
                Tasks.await(task)
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit detection error on frame $fIdx: ${e.message}")
                emptyList<Face>()
            }
            val mlkitTime = System.currentTimeMillis() - mlkitStart

            val hasFaces = faces.isNotEmpty()

            sampledFrameItems.add(
                SampledFrameItem(
                    frameIndex = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    bitmap = frame.bitmap,
                    decodeTimeMs = frame.decodeTimeMs,
                    hasFaces = hasFaces,
                    faceCount = faces.size
                )
            )

            val frameFaces = mutableListOf<DetectedFaceItem>()

            if (hasFaces) {
                for (face in faces) {
                    val crop = DebugQualityAndAlignment.cropSquarePortrait(frame.bitmap, face.boundingBox)

                    boundingBoxCutItems.add(
                        BoundingBoxCutItem(
                            faceId = faceIdCounter,
                            frameIndex = frame.frameIndex,
                            timestampMs = frame.timestampMs,
                            boundingBox = face.boundingBox,
                            faceCrop = crop
                        )
                    )

                    val blur = DebugQualityAndAlignment.calculateLaplacianVariance(crop)
                    val sharpness = DebugQualityAndAlignment.calculateSobelSharpness(crop)

                    val yaw = face.headEulerAngleY
                    val pitch = face.headEulerAngleX
                    val roll = face.headEulerAngleZ

                    val box = face.boundingBox
                    val w = frame.bitmap.width
                    val h = frame.bitmap.height
                    val edgeValidator = EdgeClippingValidator(marginPx = config.minBorderMargin)
                    val isEdgeValid = edgeValidator.isValid(frame.bitmap, face, w, h)
                    val isEdgeClipped = !isEdgeValid
                    val edgeClipReason = if (isEdgeClipped) edgeValidator.getReason() else null

                    var isValid = true
                    var failureReason: String? = null

                    if (config.enableEdgeClippingFilter && isEdgeClipped) {
                        isValid = false
                        failureReason = "Edge clipped: $edgeClipReason"
                    }

                    if (isValid && config.enableFrontalityFilter) {
                        if (kotlin.math.abs(yaw) > config.maxYaw) {
                            isValid = false
                            failureReason = "Yaw: ${"%.1f".format(yaw)}° > ${config.maxYaw}°"
                        } else if (kotlin.math.abs(pitch) > config.maxPitch) {
                            isValid = false
                            failureReason = "Pitch: ${"%.1f".format(pitch)}° > ${config.maxPitch}°"
                        } else if (kotlin.math.abs(roll) > config.maxRoll) {
                            isValid = false
                            failureReason = "Roll: ${"%.1f".format(roll)}° > ${config.maxRoll}°"
                        }
                    }

                    if (isValid && config.enableBlurFilter && blur < config.minBlurScore) {
                        isValid = false
                        failureReason = "Blur: ${"%.1f".format(blur)} < ${config.minBlurScore}"
                    }

                    if (isValid && config.enableSharpnessFilter && sharpness < config.minSharpness) {
                        isValid = false
                        failureReason = "Sharpness: ${"%.1f".format(sharpness)} < ${config.minSharpness}"
                    }

                    val aligned112 = DebugQualityAndAlignment.alignFace(
                        frame.bitmap,
                        face,
                        enabled = config.enableFaceAlignment
                    )

                    val item = DetectedFaceItem(
                        id = faceIdCounter++,
                        frameIndex = frame.frameIndex,
                        timestampMs = frame.timestampMs,
                        boundingBox = face.boundingBox,
                        faceCrop = crop,
                        alignedCrop112 = aligned112,
                        fullBitmap = frame.bitmap,
                        mlKitFace = face,
                        yaw = yaw,
                        pitch = pitch,
                        roll = roll,
                        blurScore = blur,
                        sharpnessScore = sharpness,
                        isEdgeClipped = isEdgeClipped,
                        edgeClipReason = edgeClipReason,
                        isValid = isValid,
                        failureReason = failureReason
                    )
                    if (!isValid) {
                        Log.w(TAG, "  [Quality] Face #${item.id} (F#${item.frameIndex}) REJECTED: $failureReason")
                    }
                    frameFaces.add(item)
                    allDetectedFaces.add(item)
                }

                val annotatedFrame = drawBoundingBoxesOnFrame(frame.bitmap, frameFaces)
                frameFilterItems.add(
                    FrameFilterItem(
                        frameIndex = frame.frameIndex,
                        timestampMs = frame.timestampMs,
                        annotatedBitmap = annotatedFrame,
                        isAccepted = true,
                        faceCount = faces.size,
                        faces = frameFaces
                    )
                )

                val frameSummary = "[Frame #${frame.frameIndex}] ACCEPTED: ${faces.size} face(s) (MLKit: ${mlkitTime}ms)"
                onLog(frameSummary)
            } else {
                val annotatedEmpty = drawEmptyFrameBadge(frame.bitmap)
                frameFilterItems.add(
                    FrameFilterItem(
                        frameIndex = frame.frameIndex,
                        timestampMs = frame.timestampMs,
                        annotatedBitmap = annotatedEmpty,
                        isAccepted = false,
                        faceCount = 0,
                        faces = emptyList()
                    )
                )

                val frameSummary = "[Frame #${frame.frameIndex}] REJECTED: 0 faces detected (dropped)"
                onLog(frameSummary)
            }
        }

        val acceptedCount = frameFilterItems.count { it.isAccepted }
        val rejectedCount = frameFilterItems.count { !it.isAccepted }
        onLog("=== Step 2 Completed: $acceptedCount frames accepted, $rejectedCount empty frames dropped ===")
        onLog("=== Step 3 Completed: ${boundingBoxCutItems.size} total face cuts extracted ===")

        val validFaces = allDetectedFaces.filter { it.isValid }
        val (candidateFaces, debugTracks) = if (config.enableTracking) {
            val (cCandidates, tracks) = applyTemporalTracking(validFaces, config.trackingCentroidThreshold)
            onLog("=== Temporal Tracking: Formed ${tracks.size} tracks from ${validFaces.size} faces. Selected ${cCandidates.size} sharpest winners (pruned ${validFaces.size - cCandidates.size} duplicates) ===")
            cCandidates to tracks
        } else {
            onLog("=== Tracking OFF: Passing all ${validFaces.size} valid faces to embedding ===")
            validFaces to emptyList<DebugTrackItem>()
        }

        onLog("=== Step 4: Generating MobileFaceNet 192D Embeddings ===")
        val totalCandidates = candidateFaces.size
        for ((cIdx, faceItem) in candidateFaces.withIndex()) {
            onProgress("Embedding Face ${cIdx + 1}/$totalCandidates", 0.45f + ((cIdx.toFloat() / totalCandidates.coerceAtLeast(1)) * 0.35f))
            val embStart = System.currentTimeMillis()
            val inputCrop = faceItem.alignedCrop112 ?: faceItem.faceCrop
            faceItem.embedding = tfliteModel.generateEmbedding(inputCrop)
            val embTime = System.currentTimeMillis() - embStart

            val embMsg = "  -> Face #${faceItem.id}: 192D embedding computed in ${embTime}ms"
            onLog(embMsg)
        }

        onProgress("Step 5: 4-Phase clustering...", 0.85f)
        val validEmbeddedFaces = candidateFaces.filter { it.embedding != null }
        val clusterStart = System.currentTimeMillis()
        val (clusters, events) = performClustering(validEmbeddedFaces, config.similarityThreshold, onLog)
        val clusterTime = System.currentTimeMillis() - clusterStart
        onLog("=== Step 5: Created ${clusters.size} identity clusters in ${clusterTime}ms (threshold: ${config.similarityThreshold}) ===")

        onProgress("Selecting representatives and generating collage...", 0.95f)
        selectRepresentatives(clusters)
        val representatives = clusters.mapNotNull { it.representativeFace?.faceCrop }
        val collage = DebugCollageMaker.generateCollage(representatives)

        val totalPipelineTime = System.currentTimeMillis() - pipelineStart
        onLog("=== Complete pipeline finished in ${totalPipelineTime}ms (${"%.1f".format(totalPipelineTime / 1000f)}s) ===")

        onProgress("Complete!", 1.0f)

        return@withContext DebugPipelineResult(
            totalFramesProcessed = frames.size,
            acceptedFramesCount = acceptedCount,
            rejectedFramesCount = rejectedCount,
            totalFacesDetected = allDetectedFaces.size,
            validFacesCount = allDetectedFaces.count { it.isValid },
            rejectedFacesCount = allDetectedFaces.count { !it.isValid },
            sampledFrames = sampledFrameItems,
            frameFilters = frameFilterItems,
            boundingBoxCuts = boundingBoxCutItems,
            allDetectedFaces = allDetectedFaces,
            tracks = debugTracks,
            isTrackingEnabled = config.enableTracking,
            candidateFacesForEmbedding = candidateFaces,
            clusters = clusters,
            assignmentEvents = events,
            representativeBitmaps = representatives,
            collageBitmap = collage
        )
    }

    private fun drawEmptyFrameBadge(source: Bitmap): Bitmap {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC7F1D1D")
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (source.width * 0.035f).coerceIn(28f, 48f)
            isFakeBoldText = true
        }

        val badgeRect = RectF(20f, 20f, 20f + textPaint.measureText("0 Faces Detected (Dropped)") + 32f, 20f + textPaint.textSize + 24f)
        canvas.drawRoundRect(badgeRect, 8f, 8f, paint)
        canvas.drawText("0 Faces Detected (Dropped)", 36f, 20f + textPaint.textSize + 8f, textPaint)

        return annotated
    }

    private fun drawBoundingBoxesOnFrame(source: Bitmap, faces: List<DetectedFaceItem>): Bitmap {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (source.width * 0.005f).coerceAtLeast(4f)
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (source.width * 0.024f).coerceIn(24f, 44f)
            isFakeBoldText = true
        }

        for (face in faces) {
            val rect = RectF(face.boundingBox)
            val strokeColor = if (face.isValid) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            boxPaint.color = strokeColor

            canvas.drawRoundRect(rect, 8f, 8f, boxPaint)

            val label = if (face.isValid) {
                "Face #${face.id} [VALID]"
            } else {
                "Face #${face.id} [${face.failureReason ?: "REJECTED"}]"
            }

            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize
            val pad = 8f

            val labelLeft = rect.left.coerceAtLeast(0f)
            val labelTop = (rect.top - textHeight - pad * 2).coerceAtLeast(0f)
            val labelRight = (labelLeft + textWidth + pad * 2).coerceAtMost(source.width.toFloat())
            val labelBottom = labelTop + textHeight + pad * 2

            bgPaint.color = if (face.isValid) Color.parseColor("#CC065F46") else Color.parseColor("#CC991B1B")
            canvas.drawRoundRect(RectF(labelLeft, labelTop, labelRight, labelBottom), 6f, 6f, bgPaint)

            canvas.drawText(label, labelLeft + pad, labelBottom - pad - 2f, textPaint)
        }

        return annotated
    }

    suspend fun reclusterOnly(
        candidateFaces: List<DetectedFaceItem>,
        similarityThreshold: Float
    ): Triple<List<DebugClusterItem>, List<ClusterAssignmentEvent>, List<Bitmap>> {
        for (face in candidateFaces) {
            if (face.isValid && face.embedding == null) {
                val aligned = face.alignedCrop112 ?: DebugQualityAndAlignment.alignFace(
                    face.fullBitmap,
                    face.mlKitFace,
                    enabled = true
                )
                face.embedding = tfliteModel.generateEmbedding(aligned)
            }
        }
        val validEmbedded = candidateFaces.filter { it.isValid && it.embedding != null }
        val (clusters, events) = performClustering(validEmbedded, similarityThreshold)
        selectRepresentatives(clusters)
        val reps = clusters.mapNotNull { it.representativeFace?.faceCrop }
        return Triple(clusters, events, reps)
    }

    fun performClustering(
        faces: List<DetectedFaceItem>,
        threshold: Float,
        onLog: (String) -> Unit = {}
    ): Pair<List<DebugClusterItem>, List<ClusterAssignmentEvent>> {
        if (faces.isEmpty()) return emptyList<DebugClusterItem>() to emptyList<ClusterAssignmentEvent>()

        val events = mutableListOf<ClusterAssignmentEvent>()
        val rawClusters = IdentityClustering.cluster(
            items = faces,
            embeddingSelector = { it.embedding },
            threshold = threshold,
            onLog = { onLog("  [Cluster] $it") }
        )

        val clusters = rawClusters.map { rc ->
            DebugClusterItem(
                clusterId = rc.clusterId,
                memberFaces = rc.members,
                centroid = rc.centroid
            )
        }

        for (cluster in clusters) {
            for (face in cluster.memberFaces) {
                val sim = face.embedding?.let { EmbeddingMath.cosineSimilarity(it, cluster.centroid) } ?: 1.0f
                val exp = "Face #${face.id} (F#${face.frameIndex}) in Cluster #${cluster.clusterId} (centroid_sim=${"%.3f".format(sim)})"
                events.add(ClusterAssignmentEvent(face.id, face.frameIndex, face.timestampMs, cluster.clusterId, false, sim, exp))
            }
        }

        return clusters to events
    }

    private fun findOutlierIndex(faces: List<DetectedFaceItem>): Int {
        if (faces.size <= 1) return 0
        var outlierIdx = 0
        var minAvgSim = Float.MAX_VALUE

        for (i in faces.indices) {
            val e1 = faces[i].embedding ?: continue
            val avgSim = faces.indices
                .filter { it != i }
                .mapNotNull { j -> faces[j].embedding }
                .map { e2 -> DebugTFLiteModel.cosineSimilarity(e1, e2) }
                .average()
                .toFloat()

            if (avgSim < minAvgSim) {
                minAvgSim = avgSim
                outlierIdx = i
            }
        }
        return outlierIdx
    }

    private fun calculateInterSim(faces1: List<DetectedFaceItem>, faces2: List<DetectedFaceItem>): Float {
        val embs1 = faces1.mapNotNull { it.embedding }
        val embs2 = faces2.mapNotNull { it.embedding }
        if (embs1.isEmpty() || embs2.isEmpty()) return -1f
        var total = 0f
        var count = 0
        for (e1 in embs1) {
            for (e2 in embs2) {
                total += DebugTFLiteModel.cosineSimilarity(e1, e2)
                count++
            }
        }
        return if (count > 0) total / count else -1f
    }

    private fun selectRepresentatives(clusters: List<DebugClusterItem>) {
        for (cluster in clusters) {
            if (cluster.memberFaces.size == 1) {
                cluster.representativeFace = cluster.memberFaces[0]
                continue
            }

            var bestFace = cluster.memberFaces[0]
            var bestScore = Float.NEGATIVE_INFINITY

            for (face in cluster.memberFaces) {
                val emb = face.embedding
                val sim = if (emb != null) DebugTFLiteModel.cosineSimilarity(emb, cluster.centroid) else 0f
                var score = (face.sharpnessScore / 1000f) + ((face.faceCrop.width * face.faceCrop.height) / 10000f) + (sim * 100f)
                if (score > bestScore) {
                    bestScore = score
                    bestFace = face
                }
            }
            cluster.representativeFace = bestFace
        }
    }

    private fun applyTemporalTracking(
        faces: List<DetectedFaceItem>,
        thresholdDistance: Float
    ): Pair<List<DetectedFaceItem>, List<DebugTrackItem>> {
        val tracks = mutableMapOf<Int, MutableList<DetectedFaceItem>>()
        var nextTrackId = 1

        val byFrame = faces.groupBy { it.frameIndex }.toSortedMap()

        for ((_, frameFaces) in byFrame) {
            val availableTracks = tracks.keys.toMutableSet()
            for (face in frameFaces) {
                val c = PointF(face.boundingBox.exactCenterX(), face.boundingBox.exactCenterY())
                var bestTrackId: Int? = null
                var minDist = thresholdDistance

                for (tId in availableTracks) {
                    val last = tracks[tId]?.lastOrNull() ?: continue
                    val lastC = PointF(last.boundingBox.exactCenterX(), last.boundingBox.exactCenterY())
                    val dist = sqrt((c.x - lastC.x).pow(2) + (c.y - lastC.y).pow(2))
                    if (dist < minDist) {
                        minDist = dist
                        bestTrackId = tId
                    }
                }

                if (bestTrackId != null) {
                    tracks[bestTrackId]?.add(face)
                    availableTracks.remove(bestTrackId)
                } else {
                    val newId = nextTrackId++
                    tracks[newId] = mutableListOf(face)
                }
            }
        }

        val trackItems = mutableListOf<DebugTrackItem>()
        val winnerFaces = mutableListOf<DetectedFaceItem>()

        for ((trackId, memberFaces) in tracks) {
            val best = memberFaces.maxByOrNull { it.sharpnessScore } ?: memberFaces.first()
            winnerFaces.add(best)
            trackItems.add(
                DebugTrackItem(
                    trackId = trackId,
                    memberFaces = memberFaces,
                    selectedBestFace = best,
                    startFrame = memberFaces.minOf { it.frameIndex },
                    endFrame = memberFaces.maxOf { it.frameIndex },
                    startTimestampMs = memberFaces.minOf { it.timestampMs },
                    endTimestampMs = memberFaces.maxOf { it.timestampMs }
                )
            )
        }

        return winnerFaces to trackItems
    }

    fun close() {
        try { mlKitDetector.close() } catch (_: Exception) {}
        try { tfliteModel.close() } catch (_: Exception) {}
    }
}
