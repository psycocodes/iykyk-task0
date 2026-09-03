package com.iykyk.task0.ml.config

const val DEFAULT_SIMILARITY_THRESHOLD = 0.60f

data class MLPipelineConfig(

    val enableTracking: Boolean = true,
    val trackingCentroidThreshold: Float = 160f,
    val targetFrameIntervalMs: Long = 250L,
    
    val enableSizeFilter: Boolean = false,         
    val enableFrontalityFilter: Boolean = true,   
    val enableBlurFilter: Boolean = true,         
    val enableSharpnessFilter: Boolean = false,   
    val enableFaceAlignment: Boolean = false,      
    
    val enableClustering: Boolean = true,

    val minFaceSize: Int = 40,                    
    
    val maxYaw: Float = 50f,                      
    val maxPitch: Float = 35f,                    
    val maxRoll: Float = 30f,                     
    
    val minBlurScore: Float = 40f,                

    val minSharpness: Float = 5000f,
    

    val similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
    
    val embeddingDimension: Int = 512
)