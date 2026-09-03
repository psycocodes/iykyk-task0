package com.iykyk.task0.ml.models

data class FaceTrack(
    val trackId: String,
    val personId: String,
    val frames: MutableList<FaceFrame> = mutableListOf()
)
