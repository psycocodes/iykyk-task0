package com.iykyk.task0

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iykyk.task0.ml.di.MLContainer
import com.iykyk.task0.screens.CameraScreen
import com.iykyk.task0.screens.CollageScreen
import com.iykyk.task0.screens.PermissionScreen
import com.iykyk.task0.screens.ProcessingScreen
import com.iykyk.task0.ui.theme.Iykyktask0Theme
import com.iykyk.task0.utils.rememberCameraPermissionState
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.iykyk.task0.debug.DebugScreen

/**
 * Top-level application destinations for navigation flow.
 */
enum class AppDestination {
    CAMERA,
    PROCESSING,
    COLLAGE,
    DEBUG
}

/**
 * Single-activity entry point configuring edge-to-edge rendering and composing the root UI hierarchy.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Iykyktask0Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { _ ->
                    AppRoot(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Primary state coordinator and screen router.
 *
 * Manages runtime permission checks, MLContainer dependency lifecycle,
 * and animated transitions across Camera, Processing, and Collage destinations.
 *
 * @param modifier Modifier applied to the root container.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mlContainer = remember { MLContainer(context) }

    DisposableEffect(Unit) {
        onDispose {
            mlContainer.close()
        }
    }

    var currentDestination by remember { mutableStateOf(AppDestination.CAMERA) }
    var generatedCollageFile by remember { mutableStateOf<File?>(null) }
    var representativeBitmaps by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }

    val permissionState = rememberCameraPermissionState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!permissionState.hasPermission) {
            PermissionScreen(
                hasDeniedOnce = permissionState.hasDeniedOnce,
                onGrantPermissionClick = { permissionState.requestPermission() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { destination ->
                when (destination) {
                    AppDestination.CAMERA -> {
                        CameraScreen(
                            detector = mlContainer.detector,
                            frameIntervalMs = mlContainer.config.targetFrameIntervalMs,
                            onRecordingCompleted = {
                                currentDestination = AppDestination.PROCESSING
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppDestination.PROCESSING -> {
                        ProcessingScreen(
                            detector = mlContainer.detector,
                            processor = mlContainer.processor,
                            onProcessingFinished = { file, representatives, _ ->
                                generatedCollageFile = file
                                representativeBitmaps = representatives
                                currentDestination = AppDestination.COLLAGE
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppDestination.COLLAGE -> {
                        CollageScreen(
                            collageFile = generatedCollageFile,
                            representatives = representativeBitmaps,
                            onRecordAgainClick = {
                                scope.launch { mlContainer.detector.clear() }
                                currentDestination = AppDestination.CAMERA
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AppDestination.DEBUG -> {
                        DebugScreen(
                            onBack = { currentDestination = AppDestination.CAMERA },
                            onProceedToCollage = { collageFile, reps ->
                                generatedCollageFile = collageFile
                                representativeBitmaps = reps
                                currentDestination = AppDestination.COLLAGE
                            }
                        )
                    }
                }
            }

            // Small black icon at the bottom to access debug screen
            if (currentDestination == AppDestination.CAMERA || currentDestination == AppDestination.COLLAGE) {
                IconButton(
                    onClick = { currentDestination = AppDestination.DEBUG },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp, end = 16.dp)
                        .size(38.dp)
                        .background(Color(0xFF18181B).copy(alpha = 0.90f), shape = CircleShape)
                        .border(1.dp, Color(0xFF3F3F46).copy(alpha = 0.60f), shape = CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_debug_inspect),
                        contentDescription = "Debug Inspector",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
