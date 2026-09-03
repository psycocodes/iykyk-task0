package com.iykyk.task0.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import com.google.mlkit.vision.face.Face
import com.iykyk.task0.debug.ml.BoundingBoxCutItem
import com.iykyk.task0.debug.ml.DebugQualityAndAlignment
import com.iykyk.task0.debug.ml.ClusterAssignmentEvent
import com.iykyk.task0.debug.ml.DebugCollageMaker
import com.iykyk.task0.debug.ml.DebugClusterItem
import com.iykyk.task0.debug.ml.DebugPipelineResult
import com.iykyk.task0.debug.ml.DebugTrackItem
import com.iykyk.task0.debug.ml.DetectedFaceItem
import com.iykyk.task0.debug.ml.FrameFilterItem
import com.iykyk.task0.debug.ml.SampledFrameItem
import com.iykyk.task0.ml.models.FaceCluster
import com.iykyk.task0.ml.models.FaceQuality
import com.iykyk.task0.ml.quality.EdgeClippingValidator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared singleton recording real-time live camera analysis data
 * and feeding it directly into the visual Debug Inspector.
 */
object DebugSessionHolder {
    var latestLiveResult: DebugPipelineResult? = null
    val liveLogs = mutableListOf<String>()
    var lastRecordedVideoUri: Uri? = null
    var lastRecordedVideoFile: File? = null

    // Collectors for current live recording session
    private val liveSampledFrames = mutableListOf<SampledFrameItem>()
    private val liveFrameFilters = mutableListOf<FrameFilterItem>()
    private val liveBoundingBoxCuts = mutableListOf<BoundingBoxCutItem>()
    private val liveAllDetectedFaces = mutableListOf<DetectedFaceItem>()
    private var liveFaceIdCounter = 0

    @Synchronized
    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$timestamp] $message"
        liveLogs.add(entry)
        if (liveLogs.size > 2000) liveLogs.removeAt(0)
        android.util.Log.i("IYKYK_ML", "[DebugHolder] $message")
    }

    @Synchronized
    fun startLiveRecordingSession() {
        liveSampledFrames.clear()
        liveFrameFilters.clear()
        liveBoundingBoxCuts.clear()
        liveAllDetectedFaces.clear()
        liveFaceIdCounter = 0
        liveLogs.clear()
        latestLiveResult = null
        addLog("[Recording] Started live camera recording session")
    }

    @Synchronized
    fun recordEmptyLiveFrame(
        frameIndex: Int,
        timestampMs: Long,
        bitmap: Bitmap
    ) {
        val sampledItem = SampledFrameItem(
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            bitmap = bitmap,
            decodeTimeMs = 0,
            hasFaces = false,
            faceCount = 0
        )
        liveSampledFrames.add(sampledItem)

        val annotated = drawEmptyFrameBadge(bitmap)
        liveFrameFilters.add(
            FrameFilterItem(
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                annotatedBitmap = annotated,
                isAccepted = false,
                faceCount = 0,
                faces = emptyList()
            )
        )
        addLog("[Frame #$frameIndex] Frame REJECTED: 0 faces detected (dropped)")
    }

    @Synchronized
    fun recordLiveFrame(
        frameIndex: Int,
        timestampMs: Long,
        bitmap: Bitmap,
        detectedFaces: List<Pair<Face, FaceQuality>>,
        cropExtractor: (Bitmap, Rect) -> Bitmap
    ) {
        val hasFaces = detectedFaces.isNotEmpty()
        val sampledItem = SampledFrameItem(
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            bitmap = bitmap,
            decodeTimeMs = 0,
            hasFaces = hasFaces,
            faceCount = detectedFaces.size
        )
        liveSampledFrames.add(sampledItem)

        val frameFaceItems = mutableListOf<DetectedFaceItem>()

        for ((face, quality) in detectedFaces) {
            liveFaceIdCounter++
            val crop = cropExtractor(bitmap, face.boundingBox)

            val box = face.boundingBox
            val w = bitmap.width
            val h = bitmap.height
            val edgeValidator = EdgeClippingValidator(marginPx = 20)
            val isEdgeValid = edgeValidator.isValid(bitmap, face, w, h)
            val isEdgeClipped = !isEdgeValid
            val edgeClipReason = if (isEdgeClipped) edgeValidator.getReason() else null

            val aligned112 = DebugQualityAndAlignment.alignFace(bitmap, face, true)

            val isValid = !isEdgeClipped
            val failureReason = if (isEdgeClipped) edgeClipReason else null

            val faceItem = DetectedFaceItem(
                id = liveFaceIdCounter,
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                boundingBox = face.boundingBox,
                faceCrop = crop,
                alignedCrop112 = aligned112,
                fullBitmap = bitmap,
                mlKitFace = face,
                yaw = quality.yaw,
                pitch = quality.pitch,
                roll = quality.roll,
                blurScore = quality.blurScore,
                sharpnessScore = quality.sharpnessScore,
                isEdgeClipped = isEdgeClipped,
                edgeClipReason = edgeClipReason,
                isValid = isValid,
                failureReason = failureReason
            )
            frameFaceItems.add(faceItem)
            liveAllDetectedFaces.add(faceItem)

            liveBoundingBoxCuts.add(
                BoundingBoxCutItem(
                    faceId = liveFaceIdCounter,
                    frameIndex = frameIndex,
                    timestampMs = timestampMs,
                    boundingBox = face.boundingBox,
                    faceCrop = crop
                )
            )

            if (quality.isValid) {
                addLog("  [Face #${liveFaceIdCounter} PASSED: yaw=${"%.1f".format(quality.yaw)}°, sharpness=${"%.1f".format(quality.sharpnessScore)}, blur=${"%.1f".format(quality.blurScore)}")
            } else {
                addLog("  [Face #${liveFaceIdCounter} REJECTED: '${quality.failureReason}'")
            }
        }

        addLog("[Frame #$frameIndex] Frame ACCEPTED: ${detectedFaces.size} face(s) detected")

        val annotated = annotateFrame(bitmap, frameFaceItems)
        liveFrameFilters.add(
            FrameFilterItem(
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                annotatedBitmap = annotated,
                isAccepted = frameFaceItems.isNotEmpty(),
                faceCount = frameFaceItems.size,
                faces = frameFaceItems
            )
        )
    }

    private fun annotateFrame(source: Bitmap, faces: List<DetectedFaceItem>): Bitmap {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
        }

        for (face in faces) {
            val rect = RectF(face.boundingBox)
            val colorHex = if (face.isValid) "#10B981" else "#EF4444"
            strokePaint.color = Color.parseColor(colorHex)
            canvas.drawRoundRect(rect, 4f, 4f, strokePaint)

            val label = if (face.isValid) "#${face.id} PASSED" else "#${face.id} REJECTED"
            val textWidth = textPaint.measureText(label)
            val textHeight = 18f
            val pad = 4f

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

    private fun drawEmptyFrameBadge(source: Bitmap): Bitmap {
        val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC7F1D1D")
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            isFakeBoldText = true
        }

        canvas.drawRoundRect(RectF(16f, 16f, 260f, 64f), 8f, 8f, paint)
        canvas.drawText("No Faces Detected", 28f, 48f, textPaint)
        return annotated
    }

    @Synchronized
    fun completeLiveProcessing(
        clusters: List<FaceCluster>,
        representatives: List<Bitmap>,
        collage: Bitmap?
    ) {
        val candidateFaces = liveAllDetectedFaces.filter { it.isValid }

        val allClusterFaces = clusters.flatMap { it.faces }
        for ((cIdx, faceItem) in candidateFaces.withIndex()) {
            if (faceItem.embedding == null) {
                val match = allClusterFaces.firstOrNull { it.first === faceItem.faceCrop }
                    ?: if (cIdx < allClusterFaces.size) allClusterFaces[cIdx] else null
                if (match != null) {
                    faceItem.embedding = match.second
                }
            }
        }

        addLog("=== Live Batch Complete: ${candidateFaces.size} candidates -> ${clusters.size} clusters, ${representatives.size} representatives ===")

        val debugClusters = clusters.map { fc ->
            val memberItems = mutableListOf<DetectedFaceItem>()
            for ((bmp, emb) in fc.faces) {
                val match = candidateFaces.firstOrNull { it.faceCrop === bmp }
                    ?: candidateFaces.firstOrNull { it.embedding?.contentEquals(emb) == true }
                if (match != null && !memberItems.contains(match)) {
                    match.embedding = emb
                    memberItems.add(match)
                }
            }

            val finalMembers = if (memberItems.isNotEmpty()) memberItems else {
                val clusterEmbs = fc.faces.map { it.second }
                candidateFaces.filter { face ->
                    face.embedding != null && clusterEmbs.any { it.contentEquals(face.embedding) }
                }.toMutableList()
            }

            DebugClusterItem(
                clusterId = fc.clusterId,
                memberFaces = finalMembers,
                centroid = fc.centroid,
                representativeFace = finalMembers.firstOrNull() ?: candidateFaces.firstOrNull()
            )
        }

        val actualCollage = collage ?: DebugCollageMaker.generateCollage(
            representatives.ifEmpty {
                debugClusters.mapNotNull { it.representativeFace?.faceCrop }
            }
        )

        latestLiveResult = DebugPipelineResult(
            totalFramesProcessed = liveSampledFrames.size,
            acceptedFramesCount = liveFrameFilters.count { it.isAccepted },
            rejectedFramesCount = liveFrameFilters.count { !it.isAccepted },
            totalFacesDetected = liveAllDetectedFaces.size,
            validFacesCount = liveAllDetectedFaces.count { it.isValid },
            rejectedFacesCount = liveAllDetectedFaces.count { !it.isValid },
            sampledFrames = liveSampledFrames.toList(),
            frameFilters = liveFrameFilters.toList(),
            boundingBoxCuts = liveBoundingBoxCuts.toList(),
            allDetectedFaces = liveAllDetectedFaces.toList(),
            tracks = emptyList(),
            isTrackingEnabled = false,
            candidateFacesForEmbedding = candidateFaces,
            clusters = debugClusters,
            assignmentEvents = emptyList(),
            representativeBitmaps = representatives,
            collageBitmap = actualCollage
        )
        addLog("PASSED DebugSessionHolder: latestLiveResult stored successfully with ${liveBoundingBoxCuts.size} cuts!")
    }
}
