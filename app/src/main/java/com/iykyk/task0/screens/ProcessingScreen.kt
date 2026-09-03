package com.iykyk.task0.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.R
import com.iykyk.task0.ml.models.ProcessingState
import com.iykyk.task0.ml.processing.Detector
import com.iykyk.task0.ml.processing.Processor
import com.iykyk.task0.ui.components.CollageHeader
import com.iykyk.task0.ui.components.PillButton
import com.iykyk.task0.ui.components.ProcessingLoaderCard
import com.iykyk.task0.utils.CollageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "IYKYK_PROCESSING"

/**
 * Screen presenting real-time progress while ML embeddings and clustering are calculated in the background.
 *
 * Automatically branches between Portrait and Landscape using component-level configurations.
 *
 * @param detector Detector tracking face samples gathered during recording.
 * @param processor Processor executing quality validation, TFLite embedding extraction, and clustering.
 * @param onProcessingFinished Callback triggered upon pipeline completion or user cancellation.
 * @param modifier Modifier applied to the root Surface container.
 */
@Composable
fun ProcessingScreen(
    detector: Detector,
    processor: Processor,
    onProcessingFinished: (collageFile: File?, representatives: List<Bitmap>, wasCancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val progressState by processor.processingProgress.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "loader_pulse")
    val blobScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val detectedFaces = detector.getBestFacesPerTrack()
            Log.d(TAG, "Starting batch processing on ${detectedFaces.size} face samples...")

            val output = processor.processFaces(detectedFaces)
            val collageFile = CollageGenerator.generateAndExportCollage(context, output.representativeBitmaps)

            withContext(Dispatchers.Main) {
                onProcessingFinished(collageFile, output.representativeBitmaps, output.wasCancelled)
            }
        }
    }

    val subtitleText = when (val state = progressState) {
        is ProcessingState.Embedding -> "${state.current}/${state.total} faces embedded"
        is ProcessingState.Clustering -> "clustering identities..."
        is ProcessingState.SelectingRepresentatives -> "selecting best portraits..."
        is ProcessingState.Complete -> "ready!"
        is ProcessingState.Cancelled -> "cancelled"
        is ProcessingState.Error -> state.message
        is ProcessingState.Idle -> "preparing..."
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        if (isLandscape) {
            ProcessingLoaderCard(
                blobScale = blobScale,
                subtitleText = subtitleText,
                isLandscape = true,
                onCancelClick = { processor.cancelProcessing() }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                CollageHeader(
                    title = "sit tight!",
                    isLandscape = false,
                    topPadding = 40.dp
                )

                ProcessingLoaderCard(
                    blobScale = blobScale,
                    subtitleText = subtitleText,
                    isLandscape = false
                )

                PillButton(
                    text = "cancel",
                    iconResId = R.drawable.cross,
                    backgroundColor = Color(0xFF8C2E24),
                    onClick = { processor.cancelProcessing() },
                    height = 52.dp,
                    cornerRadius = 26.dp,
                    iconSize = 20.dp,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(bottom = 36.dp)
                        .width(190.dp)
                )
            }
        }
    }
}
