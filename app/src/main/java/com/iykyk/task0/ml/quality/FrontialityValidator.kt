package com.iykyk.task0.ml.quality

import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import kotlin.math.abs

class FrontialityValidator(
    private val maxYaw: Float = 30f,
    private val maxPitch: Float = 20f,
    private val maxRoll: Float = 20f
) : FaceValidator {
    private var lastReason = ""

    override fun isValid(bitmap: Bitmap, face: Face, frameWidth: Int, frameHeight: Int): Boolean {
        val yaw = abs(face.headEulerAngleY)
        val pitch = abs(face.headEulerAngleX)
        val roll = abs(face.headEulerAngleZ)

        if (yaw > maxYaw) {
            lastReason = "Yaw too high: ${"%.1f".format(yaw)}° > $maxYaw°"
            return false
        }
        if (pitch > maxPitch) {
            lastReason = "Pitch too high: ${"%.1f".format(pitch)}° > $maxPitch°"
            return false
        }
        if (roll > maxRoll) {
            lastReason = "Roll too high: ${"%.1f".format(roll)}° > $maxRoll°"
            return false
        }
        return true
    }

    override fun getReason(): String = lastReason
}
