package com.iykyk.task0.ml.models

data class FaceQuality(
    val isValid: Boolean,
    val blurScore: Float,
    val sharpnessScore: Float,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val failureReason: String? = null
)
