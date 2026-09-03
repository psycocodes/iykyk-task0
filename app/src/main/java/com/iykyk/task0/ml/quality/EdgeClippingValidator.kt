package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face

/**
 * Validates that a detected face is not clipped by screen boundaries.
 * Rejects face if bounding box is within marginPx of any frame edge.
 */
class EdgeClippingValidator(
    private val marginPx: Int = 20
) : FaceValidator {

    private var lastReason = ""

    override fun isValid(bitmap: Bitmap, face: Face, frameWidth: Int, frameHeight: Int): Boolean {
        val width = if (frameWidth > 0) frameWidth else bitmap.width
        val height = if (frameHeight > 0) frameHeight else bitmap.height
        val box = face.boundingBox

        val clippedEdges = mutableListOf<String>()
        if (box.left <= marginPx) clippedEdges.add("Left (${box.left}px <= ${marginPx}px)")
        if (box.top <= marginPx) clippedEdges.add("Top (${box.top}px <= ${marginPx}px)")
        if (box.right >= width - marginPx) clippedEdges.add("Right (${box.right}px >= ${width - marginPx}px)")
        if (box.bottom >= height - marginPx) clippedEdges.add("Bottom (${box.bottom}px >= ${height - marginPx}px)")

        if (clippedEdges.isNotEmpty()) {
            lastReason = "Edge clipped (${marginPx}px): ${clippedEdges.joinToString(", ")}"
            return false
        }

        return true
    }

    override fun getReason(): String = lastReason
}
