package com.iykyk.task0.ml.models

import android.graphics.Bitmap

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class Embedding(val current: Int, val total: Int) : ProcessingState()
    object Clustering : ProcessingState()
    object SelectingRepresentatives : ProcessingState()
    data class Complete(val representatives: List<Bitmap>) : ProcessingState()
    object Cancelled : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
