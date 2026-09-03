package com.iykyk.task0.ml.config

const val DEFAULT_SIMILARITY_THRESHOLD = 0.65f

data class MLPipelineConfig(
    val enableTracking: Boolean = false,
    val trackingCentroidThreshold: Float = 160f,
    val targetFrameIntervalMs: Long = 250L,
    
    val enableEdgeClippingFilter: Boolean = true,
    val maxMissingFaceRatio: Float = 0.75f,
    val edgeClippingMarginPx: Int = 20,
    val enableSizeFilter: Boolean = false,         
    val enableFrontalityFilter: Boolean = false,   
    val enableBlurFilter: Boolean = false,         
    val enableSharpnessFilter: Boolean = false,   
    val enableFaceAlignment: Boolean = true,      
    
    val enableClustering: Boolean = true,

    val minFaceSize: Int = 40,                    
    
    val maxYaw: Float = 55f,                      
    val maxPitch: Float = 45f,                    
    val maxRoll: Float = 45f,                     
    
    val minBlurScore: Float = 8f,                

    val minSharpness: Float = 600f,
    
    val similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
    
    val embeddingDimension: Int = 192
)
