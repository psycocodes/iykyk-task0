package com.iykyk.task0.debug.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face

data class SampledFrameItem(
    val frameIndex: Int,
    val timestampMs: Long,
    val bitmap: Bitmap,
    val decodeTimeMs: Long,
    val hasFaces: Boolean,
    val faceCount: Int
)

data class FrameFilterItem(
    val frameIndex: Int,
    val timestampMs: Long,
    val annotatedBitmap: Bitmap,
    val isAccepted: Boolean,
    val faceCount: Int,
    val faces: List<DetectedFaceItem>
)

data class BoundingBoxCutItem(
    val faceId: Int,
    val frameIndex: Int,
    val timestampMs: Long,
    val boundingBox: Rect,
    val faceCrop: Bitmap
)

data class DetectedFaceItem(
    val id: Int,
    val frameIndex: Int,
    val timestampMs: Long,
    val boundingBox: Rect,
    val faceCrop: Bitmap,
    val alignedCrop112: Bitmap?,
    val fullBitmap: Bitmap,
    val mlKitFace: Face,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val blurScore: Float,
    val sharpnessScore: Float,
    val isEdgeClipped: Boolean = false,
    val edgeClipReason: String? = null,
    val isValid: Boolean,
    val failureReason: String? = null,
    var embedding: FloatArray? = null
)

data class DebugTrackItem(
    val trackId: Int,
    val memberFaces: List<DetectedFaceItem>,
    val selectedBestFace: DetectedFaceItem,
    val startFrame: Int,
    val endFrame: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long
)

data class DebugClusterItem(
    val clusterId: Int,
    val memberFaces: MutableList<DetectedFaceItem>,
    var centroid: FloatArray,
    var representativeFace: DetectedFaceItem? = null
)

data class ClusterAssignmentEvent(
    val faceId: Int,
    val frameIndex: Int,
    val timestampMs: Long,
    val assignedClusterId: Int,
    val isNewCluster: Boolean,
    val similarityScore: Float,
    val explanation: String
)

data class DebugPipelineResult(
    val totalFramesProcessed: Int,
    val acceptedFramesCount: Int,
    val rejectedFramesCount: Int,
    val totalFacesDetected: Int,
    val validFacesCount: Int,
    val rejectedFacesCount: Int,
    val sampledFrames: List<SampledFrameItem>,
    val frameFilters: List<FrameFilterItem>,
    val boundingBoxCuts: List<BoundingBoxCutItem>,
    val allDetectedFaces: List<DetectedFaceItem>,
    val tracks: List<DebugTrackItem>,
    val isTrackingEnabled: Boolean,
    val candidateFacesForEmbedding: List<DetectedFaceItem>,
    val clusters: List<DebugClusterItem>,
    val assignmentEvents: List<ClusterAssignmentEvent>,
    val representativeBitmaps: List<Bitmap>,
    val collageBitmap: Bitmap?
)
