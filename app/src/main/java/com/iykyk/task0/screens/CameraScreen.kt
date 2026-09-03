package com.iykyk.task0.screens

import android.content.Context

import android.net.Uri
import com.iykyk.task0.debug.DebugSessionHolder

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import android.view.OrientationEventListener
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.AspectRatio
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iykyk.task0.R
import com.iykyk.task0.ml.processing.Detector
import com.iykyk.task0.ui.components.CircularTimerRecordButton
import com.iykyk.task0.ui.components.GlassCircleButton
import com.iykyk.task0.ui.components.GlassTimerPill
import com.iykyk.task0.ui.theme.SFPro
import com.iykyk.task0.utils.AppConfig
import com.iykyk.task0.utils.MAX_RECORD_SECONDS
import com.iykyk.task0.utils.VideoRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "IYKYK_CAMERA"
private const val DETECTION_FRAME_INTERVAL_MS = 250L

/**
 * Camera recording screen managing real-time video capture and face detection sampling.
 *
 * Automatically adapts controls layout between portrait (bottom dock) and landscape (right edge dock)
 * without tearing down the underlying hardware camera stream.
 *
 * @param detector Face detector engine receiving sampled frames during recording.
 * @param onRecordingCompleted Callback invoked when video recording finishes and finalization completes.
 * @param modifier Modifier applied to the root container.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    detector: Detector,
    onRecordingCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    frameIntervalMs: Long = 250L
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    var detectedFaceBoxes by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var isDetectingFaces by remember { mutableStateOf(false) }
    var imageSourceWidth by remember { mutableIntStateOf(0) }
    var imageSourceHeight by remember { mutableIntStateOf(0) }
    var lastAnalysisTimestamp by remember { mutableLongStateOf(0L) }

    val videoRecorder = remember { VideoRecorder(context, scope) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val aspect16x9Selector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
    }

    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .build()
        VideoCapture.withOutput(recorder)
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(aspect16x9Selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    val preview = remember {
        Preview.Builder()
            .setResolutionSelector(aspect16x9Selector)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
    }

    val displayManager = remember(context) {
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }

    fun syncRotationToDisplay() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        try {
            imageAnalysis.targetRotation = rotation
            if (!isRecording) {
                videoCapture.targetRotation = rotation
            }
        } catch (_: Exception) {}
    }

    DisposableEffect(displayManager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                syncRotationToDisplay()
            }
        }
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        syncRotationToDisplay()
        onDispose {
            displayManager?.unregisterDisplayListener(listener)
        }
    }

    LaunchedEffect(configuration.orientation) {
        syncRotationToDisplay()
    }

    LaunchedEffect(lensFacing) {
        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val now = System.currentTimeMillis()
            val mediaImage = imageProxy.image

            if (mediaImage != null && isRecording && (now - lastAnalysisTimestamp >= frameIntervalMs)) {
                lastAnalysisTimestamp = now
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                try {
                    val rawBitmap = imageProxy.toBitmap()
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val uprightBitmap = Bitmap.createBitmap(
                        rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                    )

                    scope.launch {
                        val faces = detector.processVideoFrame(uprightBitmap, 0, now)
                        if (faces.isNotEmpty()) {
                            isDetectingFaces = true
                            detectedFaceBoxes = faces.map { RectF(it.boundingBox) }
                            imageSourceWidth = uprightBitmap.width
                            imageSourceHeight = uprightBitmap.height
                        } else {
                            isDetectingFaces = false
                            detectedFaceBoxes = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    if (AppConfig.DEBUG) {
                        Log.w(TAG, "Frame analysis error: ${e.message}")
                    }
                } finally {
                    imageProxy.close()
                }
            } else {
                if (!isRecording && detectedFaceBoxes.isNotEmpty()) {
                    isDetectingFaces = false
                    detectedFaceBoxes = emptyList()
                }
                imageProxy.close()
            }
        }

        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture,
                imageAnalysis
            )
            if (AppConfig.DEBUG) {
                Log.d(TAG, "✓ Camera bound with lensFacing=$lensFacing")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera bind initial attempt: ${e.message}, retrying...")
            try {
                delay(250)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture,
                    imageAnalysis
                )
            } catch (retryEx: Exception) {
                Log.e(TAG, "Camera bind failed: ${retryEx.message}")
                Toast.makeText(context, "Camera bind error: ${retryEx.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            videoRecorder.stopRecording()
            analysisExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (AppConfig.DEBUG && isRecording) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (detectedFaceBoxes.isNotEmpty() && imageSourceWidth > 0 && imageSourceHeight > 0) {
                    val isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                    val scale = maxOf(size.width / imageSourceWidth, size.height / imageSourceHeight)
                    val offsetX = (size.width - imageSourceWidth * scale) / 2f
                    val offsetY = (size.height - imageSourceHeight * scale) / 2f

                    for (rect in detectedFaceBoxes) {
                        val left = if (isFront) {
                            (imageSourceWidth - rect.right) * scale + offsetX
                        } else {
                            rect.left * scale + offsetX
                        }
                        val top = rect.top * scale + offsetY
                        val boxWidth = rect.width() * scale
                        val boxHeight = rect.height() * scale

                        drawRoundRect(
                            color = Color(0xFFFFD700),
                            topLeft = Offset(left, top),
                            size = Size(boxWidth, boxHeight),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        drawRoundRect(
                            color = Color(0x22FFD700),
                            topLeft = Offset(left, top),
                            size = Size(boxWidth, boxHeight),
                            cornerRadius = CornerRadius(14.dp.toPx())
                        )
                    }
                }
            }
        }

        GlassTimerPill(
            isRecording = isRecording,
            elapsedSeconds = elapsedSeconds,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        )

        AnimatedVisibility(
            visible = isRecording && isDetectingFaces,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = if (isLandscape) 52.dp else 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x990F172A), Color(0xBB020617))
                        )
                    )
                    .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF38BDF8), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Detecting faces...",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SFPro
                    )
                }
            }
        }

        if (isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .navigationBarsPadding()
                    .padding(end = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCircleButton(
                    iconResId = R.drawable.outline_add_24,
                    iconRotation = 45f,
                    contentDescription = "Cancel Recording",
                    onClick = {
                        if (isRecording) {
                            videoRecorder.cancelRecording()
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            isRecording = false
                            elapsedSeconds = 0
                            detectedFaceBoxes = emptyList()
                            isDetectingFaces = false
                            scope.launch { detector.clear() }
                        }
                    },
                    enabled = isRecording,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-84).dp)
                        .alpha(if (isRecording) 1.0f else 0.35f)
                )

                CircularTimerRecordButton(
                    isRecording = isRecording,
                    maxSeconds = MAX_RECORD_SECONDS,
                    onClick = {
                        if (isRecording) {
                            videoRecorder.stopRecording()
                            isRecording = false
                            elapsedSeconds = 0
                            detectedFaceBoxes = emptyList()
                            isDetectingFaces = false
                        } else {
                            val currentOrientation = context.resources.configuration.orientation
                            activity?.requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            }

                            isRecording = true
                            elapsedSeconds = 0
                            DebugSessionHolder.startLiveRecordingSession()
                            scope.launch { detector.clear() }
                            videoRecorder.startRecording(
                                videoCapture = videoCapture,
                                maxSeconds = MAX_RECORD_SECONDS,
                                onTick = { sec -> elapsedSeconds = sec },
                                onFinished = { file, error ->
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    isRecording = false
                                    elapsedSeconds = 0
                                    detectedFaceBoxes = emptyList()
                                    isDetectingFaces = false
                                    if (file != null) {
                                        DebugSessionHolder.lastRecordedVideoFile = file
                                        DebugSessionHolder.lastRecordedVideoUri = Uri.fromFile(file)
                                        onRecordingCompleted()
                                    } else if (error != null) {
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                GlassCircleButton(
                    iconResId = R.drawable.baseline_flip_camera_android_24,
                    contentDescription = "Flip Camera",
                    onClick = {
                        if (!isRecording) {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    },
                    enabled = !isRecording,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 84.dp)
                        .alpha(if (isRecording) 0.35f else 1.0f)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCircleButton(
                    iconResId = R.drawable.baseline_flip_camera_android_24,
                    contentDescription = "Flip Camera",
                    onClick = {
                        if (!isRecording) {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    },
                    enabled = !isRecording,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-84).dp)
                        .alpha(if (isRecording) 0.35f else 1.0f)
                )

                CircularTimerRecordButton(
                    isRecording = isRecording,
                    maxSeconds = MAX_RECORD_SECONDS,
                    onClick = {
                        if (isRecording) {
                            videoRecorder.stopRecording()
                            isRecording = false
                            elapsedSeconds = 0
                            detectedFaceBoxes = emptyList()
                            isDetectingFaces = false
                        } else {
                            val currentOrientation = context.resources.configuration.orientation
                            activity?.requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            }

                            isRecording = true
                            elapsedSeconds = 0
                            DebugSessionHolder.startLiveRecordingSession()
                            scope.launch { detector.clear() }
                            videoRecorder.startRecording(
                                videoCapture = videoCapture,
                                maxSeconds = MAX_RECORD_SECONDS,
                                onTick = { sec -> elapsedSeconds = sec },
                                onFinished = { file, error ->
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    isRecording = false
                                    elapsedSeconds = 0
                                    detectedFaceBoxes = emptyList()
                                    isDetectingFaces = false
                                    if (file != null) {
                                        DebugSessionHolder.lastRecordedVideoFile = file
                                        DebugSessionHolder.lastRecordedVideoUri = Uri.fromFile(file)
                                        onRecordingCompleted()
                                    } else if (error != null) {
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                GlassCircleButton(
                    iconResId = R.drawable.outline_add_24,
                    iconRotation = 45f,
                    contentDescription = "Cancel Recording",
                    onClick = {
                        if (isRecording) {
                            videoRecorder.cancelRecording()
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            isRecording = false
                            elapsedSeconds = 0
                            detectedFaceBoxes = emptyList()
                            isDetectingFaces = false
                            scope.launch { detector.clear() }
                        }
                    },
                    enabled = isRecording,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 84.dp)
                        .alpha(if (isRecording) 1.0f else 0.35f)
                )
            }
        }
    }
}
