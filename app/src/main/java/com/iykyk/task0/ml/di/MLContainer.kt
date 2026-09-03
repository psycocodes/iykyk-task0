package com.iykyk.task0.ml.di

import android.content.Context
import com.iykyk.task0.ml.clustering.ClusteringEngine
import com.iykyk.task0.ml.clustering.CosineClusteringEngine
import com.iykyk.task0.ml.clustering.QualityBasedRepresentativeSelector
import com.iykyk.task0.ml.clustering.RepresentativeSelector
import com.iykyk.task0.ml.config.MLPipelineConfig
import com.iykyk.task0.ml.detection.FaceDetector
import com.iykyk.task0.ml.detection.FaceQualityFilter
import com.iykyk.task0.ml.detection.MLKitFaceDetector
import com.iykyk.task0.ml.embedding.EmbeddingGenerator
import com.iykyk.task0.ml.embedding.EmbeddingModel
import com.iykyk.task0.ml.embedding.FaceAligner
import com.iykyk.task0.ml.embedding.TFLiteEmbeddingModel
import com.iykyk.task0.ml.processing.Detector
import com.iykyk.task0.ml.processing.FaceProcessingPipeline
import com.iykyk.task0.ml.processing.Processor
import com.iykyk.task0.ml.quality.EdgeClippingValidator
import com.iykyk.task0.ml.quality.FaceSizeValidator
import com.iykyk.task0.ml.quality.FaceValidator
import com.iykyk.task0.ml.quality.FrontialityValidator
import com.iykyk.task0.ml.quality.LaplacianBlurDetector
import com.iykyk.task0.ml.quality.SharpnessValidator

/**
 * Dependency injection container for the machine learning pipeline.
 *
 * Instantiates and wires MLKit face detection, OpenCV/Laplacian quality validators,
 * TFLite MobileFaceNet embeddings, and cosine identity clustering engines.
 *
 * @param context Application context used to load TFLite model assets.
 */
class MLContainer(private val context: Context) {

    val config: MLPipelineConfig by lazy {
        MLPipelineConfig()
    }

    val faceDetector: FaceDetector by lazy {
        MLKitFaceDetector()
    }

    val blurDetector: LaplacianBlurDetector by lazy {
        LaplacianBlurDetector(minBlurScore = config.minBlurScore)
    }

    val sharpnessValidator: SharpnessValidator by lazy {
        SharpnessValidator(minSharpness = config.   minSharpness)
    }

    val qualityFilter: FaceQualityFilter by lazy {
        val validatorList = mutableListOf<FaceValidator>()

        if (config.enableEdgeClippingFilter) {
            validatorList.add(EdgeClippingValidator(marginPx = config.edgeClippingMarginPx))
        }
        if (config.enableSizeFilter) {
            validatorList.add(FaceSizeValidator(minSize = config.minFaceSize))
        }
        if (config.enableFrontalityFilter) {
            validatorList.add(
                FrontialityValidator(
                    maxYaw = config.maxYaw,
                    maxPitch = config.maxPitch,
                    maxRoll = config.maxRoll
                )
            )
        }
        if (config.enableBlurFilter) {
            validatorList.add(blurDetector)
        }
        if (config.enableSharpnessFilter) {
            validatorList.add(sharpnessValidator)
        }

        FaceQualityFilter(
            validators = validatorList,
            blurDetector = blurDetector,
            sharpnessValidator = sharpnessValidator
        )
    }

    val faceAligner: FaceAligner by lazy {
        FaceAligner(enabled = config.enableFaceAlignment)
    }

    val embeddingModel: EmbeddingModel by lazy {
        TFLiteEmbeddingModel(context)
    }

    val embeddingGenerator: EmbeddingGenerator by lazy {
        EmbeddingGenerator(embeddingModel, faceAligner)
    }

    val clusteringEngine: ClusteringEngine by lazy {
        CosineClusteringEngine()
    }

    val representativeSelector: RepresentativeSelector by lazy {
        QualityBasedRepresentativeSelector(sharpnessValidator)
    }

    val detector: Detector by lazy {
        Detector(faceDetector, qualityFilter, config)
    }

    val processor: Processor by lazy {
        Processor(
            embeddingGenerator = embeddingGenerator,
            clusteringEngine = clusteringEngine,
            representativeSelector = representativeSelector,
            qualityFilter = qualityFilter,
            config = config
        )
    }

    val pipeline: FaceProcessingPipeline by lazy {
        FaceProcessingPipeline(detector, processor)
    }

    /**
     * Releases underlying hardware resources and closes detector and TFLite model interpreters.
     */
    fun close() {
        faceDetector.close()
        embeddingModel.close()
    }
}
