package com.iykyk.task0.debug

import android.graphics.Bitmap
import com.iykyk.task0.utils.CollageGenerator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.iykyk.task0.debug.ml.BoundingBoxCutItem
import com.iykyk.task0.debug.ml.ClusterAssignmentEvent
import com.iykyk.task0.debug.ml.DebugClusterItem
import com.iykyk.task0.debug.ml.DebugCollageMaker
import com.iykyk.task0.debug.ml.DebugConfig
import com.iykyk.task0.debug.ml.DebugPipelineResult
import com.iykyk.task0.debug.ml.DebugPipelineRunner
import com.iykyk.task0.debug.ml.DebugTFLiteModel
import com.iykyk.task0.debug.ml.DebugTrackItem
import com.iykyk.task0.debug.ml.DetectedFaceItem
import com.iykyk.task0.debug.ml.FrameFilterItem
import com.iykyk.task0.debug.ml.SampledFrameItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.os.Environment
import androidx.compose.runtime.DisposableEffect

@Composable
fun DebugScreen(
    onBack: (() -> Unit)? = null,
    onProceedToCollage: ((java.io.File?, List<Bitmap>) -> Unit)? = null
) {
    val context = LocalContext.current
    val pipelineRunner = remember { DebugPipelineRunner(context) }
    DisposableEffect(Unit) {
        onDispose {
            pipelineRunner.close()
        }
    }
    val scope = rememberCoroutineScope()

    var processingJob by remember { mutableStateOf<Job?>(null) }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAssetName by remember { mutableStateOf<String?>(null) }
    var videoStatusText by remember { mutableStateOf("No video selected") }

    var isProcessing by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressFraction by remember { mutableFloatStateOf(0f) }

    var pipelineResult by remember { mutableStateOf<DebugPipelineResult?>(null) }
    var activeTab by remember { mutableIntStateOf(0) }
    var reclusterJob by remember { mutableStateOf<Job?>(null) }

    var similarityThreshold by remember { mutableFloatStateOf(0.65f) }
    var enableTracking by remember { mutableStateOf(false) }
    var enableFaceAlignment by remember { mutableStateOf(true) }
    var enableEdgeClippingFilter by remember { mutableStateOf(true) }
    var enableBlurFilter by remember { mutableStateOf(false) }
    var enableSharpnessFilter by remember { mutableStateOf(false) }
    var enableFrontalityFilter by remember { mutableStateOf(false) }
    var maxYaw by remember { mutableFloatStateOf(55f) }
    var maxPitch by remember { mutableFloatStateOf(45f) }
    var frameIntervalMs by remember { mutableStateOf(250L) }

    val liveLogs = remember {
        mutableStateListOf<String>().apply {
            addAll(DebugSessionHolder.liveLogs)
        }
    }

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        liveLogs.add("[$time] $msg")
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedAssetName = null
            videoStatusText = "Selected: ${uri.lastPathSegment}"
            addLog("Selected video file: ${uri.lastPathSegment}")
        }
    }

    var pendingRecordUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRecordFile by remember { mutableStateOf<File?>(null) }

    val videoRecorder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success: Boolean ->
        if (success && pendingRecordUri != null && pendingRecordFile != null) {
            selectedVideoUri = pendingRecordUri
            selectedAssetName = null
            val file = pendingRecordFile!!
            videoStatusText = "Recorded: ${file.name} (${file.length() / 1024} KB)"
            addLog("[Video] Recorded successfully! Size: ${file.length() / 1024} KB")

            scope.launch {
                val meta = VideoFrameExtractor.getMetadata(context, pendingRecordUri!!)
                if (meta != null) {
                    addLog("[Video] Specs: ${meta.width}x${meta.height}, duration=${meta.durationMs}ms, frames=${meta.totalFrameCount}")
                }
            }
        } else {
            addLog("Camera recording cancelled or failed")
        }
    }

    val assetMp4s = remember {
        try {
            context.assets.list("")?.filter { it.endsWith(".mp4", ignoreCase = true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList<String>()
        }
    }

    LaunchedEffect(Unit) {
        val liveResult = DebugSessionHolder.latestLiveResult
        if (liveResult != null) {
            pipelineResult = liveResult
            videoStatusText = "Live Recording (${liveResult.sampledFrames.size} frames, ${liveResult.boundingBoxCuts.size} cuts)"
            liveLogs.clear()
            liveLogs.addAll(DebugSessionHolder.liveLogs)
            addLog("Loaded live recording session with ${liveResult.boundingBoxCuts.size} face cuts!")
        } else {
            // Auto-locate latest recorded video from disk
            val candidateDirs = listOfNotNull(
                DebugSessionHolder.lastRecordedVideoFile?.parentFile,
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                File("/storage/emulated/0/Android/data/${context.packageName}/files/Movies"),
                File("/storage/emulated/0/Android/data/com.iykyk.task0/files/Movies"),
                File("/sdcard/Movies")
            )
            val recent = candidateDirs.flatMap { dir -> dir.listFiles()?.toList() ?: emptyList() }
                .filter { it.extension == "mp4" }
                .maxByOrNull { it.lastModified() }
            if (recent != null) {
                selectedVideoUri = Uri.fromFile(recent)
                selectedAssetName = null
                videoStatusText = "Recent: ${recent.name} (${recent.length() / 1024} KB)"
                addLog("Auto-selected latest recorded video: ${recent.name}")
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Back", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = { activeTab = 6 },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Logs (${liveLogs.size})", color = Color(0xFFA1A1AA), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Video Input & Recording",
                            color = Color(0xFFFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${frameIntervalMs}ms interval",
                            color = Color(0xFFA1A1AA),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val dir = File(context.cacheDir, "debug_recordings").apply { mkdirs() }
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    val videoFile = File(dir, "REC_$timeStamp.mp4")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        videoFile
                                    )
                                    pendingRecordUri = uri
                                    pendingRecordFile = videoFile
                                    addLog("Launching camera recorder...")
                                    videoRecorder.launch(uri)
                                } catch (e: Exception) {
                                    addLog("Error launching camera: ${e.message}")
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("Record", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { filePicker.launch("video/mp4") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("Pick MP4", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (assetMp4s.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val asset = assetMp4s.first()
                                    selectedAssetName = asset
                                    selectedVideoUri = null
                                    videoStatusText = "Asset: $asset"
                                    addLog("Loaded asset: $asset")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedAssetName != null) Color(0xFF10B981) else Color(0xFF222222)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedAssetName != null) Color(0xFF10B981) else Color(0xFF333333)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("Asset", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                val candidateDirs = listOfNotNull(
                                    DebugSessionHolder.lastRecordedVideoFile?.parentFile,
                                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                                    File("/storage/emulated/0/Android/data/${context.packageName}/files/Movies"),
                                    File("/storage/emulated/0/Android/data/com.iykyk.task0/files/Movies"),
                                    File("/sdcard/Movies")
                                )
                                val recent = candidateDirs.flatMap { dir -> dir.listFiles()?.toList() ?: emptyList() }
                                    .filter { it.extension == "mp4" }
                                    .maxByOrNull { it.lastModified() }
                                if (recent != null) {
                                    selectedVideoUri = Uri.fromFile(recent)
                                    selectedAssetName = null
                                    videoStatusText = "Movie: ${recent.name}"
                                    addLog("Loaded clip: ${recent.name}")
                                } else {
                                    Toast.makeText(context, "No recordings in Movies/", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            border = BorderStroke(1.dp, Color(0xFF333333)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("Clip", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = videoStatusText,
                        color = Color(0xFFF4F4F5),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quality Filters Row 1: Edge Clip, Frontality, Blur
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = enableEdgeClippingFilter,
                                onCheckedChange = { enableEdgeClippingFilter = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edge Clip", color = Color.White, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = enableFrontalityFilter,
                                onCheckedChange = { enableFrontalityFilter = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Frontality", color = Color.White, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = enableBlurFilter,
                                onCheckedChange = { enableBlurFilter = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Blur", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Quality & Processing Row 2: Sharpness, Alignment, Tracking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = enableSharpnessFilter,
                                onCheckedChange = { enableSharpnessFilter = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sharpness", color = Color.White, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = enableFaceAlignment,
                                onCheckedChange = { enableFaceAlignment = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Alignment", color = Color.White, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = enableTracking,
                                onCheckedChange = { enableTracking = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tracking", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sampling Interval Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sampling Interval:", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E1E1E),
                            border = BorderStroke(1.dp, Color(0xFF2E2E2E))
                        ) {
                            Row(modifier = Modifier.padding(2.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (frameIntervalMs == 250L) Color.White else Color.Transparent)
                                        .clickable { frameIntervalMs = 250L }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "250ms",
                                        color = if (frameIntervalMs == 250L) Color.Black else Color(0xFFA1A1AA),
                                        fontSize = 11.sp,
                                        fontWeight = if (frameIntervalMs == 250L) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (frameIntervalMs == 500L) Color.White else Color.Transparent)
                                        .clickable { frameIntervalMs = 500L }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "500ms",
                                        color = if (frameIntervalMs == 500L) Color.Black else Color(0xFFA1A1AA),
                                        fontSize = 11.sp,
                                        fontWeight = if (frameIntervalMs == 500L) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedVideoUri == null && selectedAssetName == null) {
                                    Toast.makeText(context, "Select or record a video first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isProcessing = true
                                activeTab = 0
                                processingJob = scope.launch {
                                    addLog(">>> Decoding video frames...")
                                    val frames = if (selectedVideoUri != null) {
                                        VideoFrameExtractor.extractFramesFromUri(
                                            context,
                                            selectedVideoUri!!,
                                            intervalMs = frameIntervalMs,
                                            onLog = { addLog(it) }
                                        )
                                    } else {
                                        VideoFrameExtractor.extractFramesFromAsset(
                                            context,
                                            selectedAssetName!!,
                                            intervalMs = frameIntervalMs,
                                            onLog = { addLog(it) }
                                        )
                                    }

                                    if (frames.isEmpty()) {
                                        addLog("Failed to decode video frames")
                                        Toast.makeText(context, "Failed to decode frames", Toast.LENGTH_SHORT).show()
                                        isProcessing = false
                                        return@launch
                                    }

                                    addLog("Decoded ${frames.size} frames. Executing pipeline (Tracking: ${if (enableTracking) "ON" else "OFF"})...")

                                    val config = DebugConfig(
                                        enableTracking = enableTracking,
                                        enableFaceAlignment = enableFaceAlignment,
                                        enableEdgeClippingFilter = enableEdgeClippingFilter,
                                        enableFrontalityFilter = enableFrontalityFilter,
                                        enableBlurFilter = enableBlurFilter,
                                        enableSharpnessFilter = enableSharpnessFilter,
                                        maxYaw = maxYaw,
                                        maxPitch = maxPitch,
                                        similarityThreshold = similarityThreshold
                                    )

                                    val result = pipelineRunner.runPipeline(
                                        frames,
                                        config,
                                        onLog = { addLog(it) },
                                        onProgress = { status, prog ->
                                            progressStatus = status
                                            progressFraction = prog
                                        }
                                    )
                                    pipelineResult = result
                                    isProcessing = false
                                }
                            },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Running Pipeline...", fontSize = 12.sp)
                            } else {
                                Text("Run Pipeline", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (isProcessing) {
                            Button(
                                onClick = {
                                    processingJob?.cancel()
                                    processingJob = null
                                    isProcessing = false
                                    addLog("Rejected Processing cancelled by user.")
                                    Toast.makeText(context, "Cancelled", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (pipelineResult != null && !isProcessing) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        addLog(">>> Generating 2 downloadable debug images...")
                                        Toast.makeText(context, "Exporting debug images...", Toast.LENGTH_SHORT).show()
                                        val cutsFile = com.iykyk.task0.debug.ml.DebugImageExporter.exportAllCutsMontage(context, pipelineResult!!.allDetectedFaces)
                                        val clustersFile = com.iykyk.task0.debug.ml.DebugImageExporter.exportClusterRowsMontage(context, pipelineResult!!.clusters, similarityThreshold)

                                        if (cutsFile != null && clustersFile != null) {
                                            addLog(" Saved Cuts Image: ${cutsFile.absolutePath} (${cutsFile.length() / 1024} KB)")
                                            addLog(" Saved Clusters Image: ${clustersFile.absolutePath} (${clustersFile.length() / 1024} KB)")
                                            Toast.makeText(context, "Saved 2 images to Downloads folder!", Toast.LENGTH_LONG).show()
                                        } else {
                                            addLog("[Export] Failed to export debug images")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Export 2 PNGs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981)
                )
                Text(
                    text = progressStatus,
                    color = Color(0xFFF4F4F5),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFF181818),
                contentColor = Color(0xFFFFFFFF),
                edgePadding = 4.dp
            ) {
                val p = pipelineResult
                val edgeClipPassed = p?.allDetectedFaces?.count { !it.isEdgeClipped } ?: 0
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                    Text("1. Sampled (${p?.sampledFrames?.size ?: 0})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                    Text("2. Filtered (${p?.acceptedFramesCount ?: 0})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }) {
                    Text("3. Cuts (${p?.boundingBoxCuts?.size ?: 0})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
                Tab(selected = activeTab == 3, onClick = { activeTab = 3 }) {
                    Text("4. Edge Clip ($edgeClipPassed/${p?.boundingBoxCuts?.size ?: 0})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Tab(selected = activeTab == 4, onClick = { activeTab = 4 }) {
                    Text("5. Preprocess (${p?.candidateFacesForEmbedding?.size ?: 0})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
                Tab(selected = activeTab == 5, onClick = { activeTab = 5 }) {
                    val tCount = if (p?.isTrackingEnabled == true) p.tracks.size else 0
                    Text("6. Tracking ($tCount)", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
                Tab(selected = activeTab == 6, onClick = { activeTab = 6 }) {
                    Text("7. Clusters (${p?.clusters?.size ?: 0})", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
                Tab(selected = activeTab == 7, onClick = { activeTab = 7 }) {
                    Text("Logs", modifier = Modifier.padding(vertical = 8.dp), fontSize = 9.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (activeTab) {
                0 -> {
                    if (pipelineResult != null) {
                        Step1SampledFramesTab(pipelineResult!!.sampledFrames)
                    } else {
                        EmptyStateView("Run pipeline to view all sampled frames")
                    }
                }
                1 -> {
                    if (pipelineResult != null) {
                        Step2FrameFiltersTab(pipelineResult!!.frameFilters)
                    } else {
                        EmptyStateView("Run pipeline to view Accepted vs Rejected frames")
                    }
                }
                2 -> {
                    if (pipelineResult != null) {
                        Step3BoundingBoxCutsTab(pipelineResult!!.boundingBoxCuts)
                    } else {
                        EmptyStateView("Run pipeline to view bounding box cuts")
                    }
                }
                3 -> {
                    if (pipelineResult != null) {
                        StepEdgeClippingTab(
                            allFaces = pipelineResult!!.allDetectedFaces,
                            isFilterEnabled = enableEdgeClippingFilter
                        )
                    } else {
                        EmptyStateView("Run pipeline to view edge clipping stage")
                    }
                }
                4 -> {
                    if (pipelineResult != null) {
                        val preprocessFaces = pipelineResult!!.allDetectedFaces.filter { !it.isEdgeClipped }
                        Step4PreprocessingTab(preprocessFaces)
                    } else {
                        EmptyStateView("Run pipeline to view preprocessed candidates & embeddings")
                    }
                }
                5 -> {
                    if (pipelineResult != null) {
                        StepTrackingTab(
                            tracks = pipelineResult!!.tracks,
                            isTrackingEnabled = pipelineResult!!.isTrackingEnabled,
                            totalValidFaces = pipelineResult!!.validFacesCount,
                            candidateCount = pipelineResult!!.candidateFacesForEmbedding.size
                        )
                    } else {
                        EmptyStateView("Run pipeline to view temporal tracking results")
                    }
                }
                6 -> {
                    if (pipelineResult != null) {
                        Step5ClustersAndCollageTab(
                            result = pipelineResult!!,
                            currentThreshold = similarityThreshold,
                            onThresholdChange = { newVal ->
                                similarityThreshold = newVal
                                reclusterJob?.cancel()
                                reclusterJob = scope.launch {
                                    delay(100)
                                    val (newClusters, newEvents, newReps) = pipelineRunner.reclusterOnly(
                                        pipelineResult!!.candidateFacesForEmbedding,
                                        newVal
                                    )
                                    val newCollage = DebugCollageMaker.generateCollage(newReps)
                                    pipelineResult = pipelineResult!!.copy(
                                        clusters = newClusters,
                                        assignmentEvents = newEvents,
                                        representativeBitmaps = newReps,
                                        collageBitmap = newCollage
                                    )
                                    addLog("Re-clustered at threshold ${"%.2f".format(newVal)}: ${newClusters.size} clusters")
                                }
                            },
                            onProceed = if (onProceedToCollage != null) {
                                {
                                    val reps = pipelineResult!!.representativeBitmaps.ifEmpty {
                                        pipelineResult!!.clusters.mapNotNull { it.representativeFace?.faceCrop }
                                    }
                                    val collageFile = CollageGenerator.generateAndExportCollage(context, reps)
                                    onProceedToCollage.invoke(collageFile, reps)
                                }
                            } else null
                        )
                    } else {
                        EmptyStateView("Run pipeline to view identity clustering & collage")
                    }
                }
                7 -> LiveLogsTab(
                    logs = liveLogs,
                    onClear = { liveLogs.clear() },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("IYKYK Logs", liveLogs.joinToString("\n"))
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun StepTrackingTab(
    tracks: List<DebugTrackItem>,
    isTrackingEnabled: Boolean,
    totalValidFaces: Int,
    candidateCount: Int
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isTrackingEnabled) Color(0xFF0F2E22) else Color(0xFF2A1C0E)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isTrackingEnabled) "Tracking is ACTIVE" else "Tracking is OFF",
                        color = if (isTrackingEnabled) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isTrackingEnabled) {
                            "Centroid tracking connects consecutive detections of the same face into a temporal Track. It selects ONLY the 1 sharpest frame from each track and prunes redundant duplicate frames ($totalValidFaces faces -> $candidateCount candidates for embedding). That is why clusters have 1-2 winner images instead of 10 duplicate images!"
                        } else {
                            "Tracking is disabled. Every single detected face ($totalValidFaces valid faces) is passed directly to MobileFaceNet embeddings without pruning. That is why clusters contain ~10 consecutive duplicate images of the same person."
                        },
                        color = Color(0xFFF4F4F5),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        if (isTrackingEnabled) {
            item {
                Text(
                    text = "Temporal Tracks (${tracks.size} Tracks Formed):",
                    color = Color(0xFFFFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(tracks) { track ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Track #${track.trackId} (${track.memberFaces.size} consecutive frames)",
                                color = Color(0xFFFFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "F#${track.startFrame} (${track.startTimestampMs}ms) -> F#${track.endFrame} (${track.endTimestampMs}ms)",
                                color = Color(0xFFA1A1AA),
                                fontSize = 9.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Winner: Face #${track.selectedBestFace.id} (Sharpness: ${"%.0f".format(track.selectedBestFace.sharpnessScore)})",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(track.memberFaces) { member ->
                                val isSelected = member.id == track.selectedBestFace.id
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF065F46) else Color(0xFF27272A))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF10B981) else Color(0xFFA1A1AA),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(4.dp)
                                ) {
                                    Image(
                                        bitmap = member.faceCrop.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("F#${member.frameIndex}", color = Color.White, fontSize = 8.sp)
                                    Text("shp:${"%.0f".format(member.sharpnessScore)}", color = Color(0xFFF4F4F5), fontSize = 7.sp)
                                    if (isSelected) {
                                        Text("SELECTED", color = Color(0xFF34D399), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("PRUNED", color = Color(0xFFA1A1AA), fontSize = 7.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Step1SampledFramesTab(frames: List<SampledFrameItem>) {
    var selectedFrame by remember { mutableStateOf<SampledFrameItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedFrame != null) {
            val f = selectedFrame!!
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Frame #${f.frameIndex} (${f.timestampMs}ms) - ${f.bitmap.width}x${f.bitmap.height}",
                            color = Color(0xFFFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { selectedFrame = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F46)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(26.dp)
                        ) { Text("Close", fontSize = 10.sp) }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        bitmap = f.bitmap.asImageBitmap(),
                        contentDescription = "Enlarged Frame",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(frames) { frame ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181818))
                        .border(1.dp, Color(0xFF27272A), RoundedCornerShape(6.dp))
                        .clickable { selectedFrame = frame }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = frame.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "F#${frame.frameIndex} (${frame.timestampMs}ms)",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${frame.decodeTimeMs}ms",
                        color = Color(0xFFA1A1AA),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Step2FrameFiltersTab(filters: List<FrameFilterItem>) {
    var filterSelection by remember { mutableIntStateOf(0) }

    val displayed = remember(filters, filterSelection) {
        when (filterSelection) {
            1 -> filters.filter { it.isAccepted }
            2 -> filters.filter { !it.isAccepted }
            else -> filters
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = filterSelection == 0,
                onClick = { filterSelection = 0 },
                label = { Text("All (${filters.size})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF27272A), selectedLabelColor = Color.White)
            )
            FilterChip(
                selected = filterSelection == 1,
                onClick = { filterSelection = 1 },
                label = { Text(" Accepted (${filters.count { it.isAccepted }})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF059669), selectedLabelColor = Color.White)
            )
            FilterChip(
                selected = filterSelection == 2,
                onClick = { filterSelection = 2 },
                label = { Text("Dropped (${filters.count { !it.isAccepted }})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFDC2626), selectedLabelColor = Color.White)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displayed) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (item.isAccepted) Color(0xFF132A22) else Color(0xFF2A1515)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Frame #${item.frameIndex} (${item.timestampMs}ms)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (item.isAccepted) " ACCEPTED (${item.faceCount} face${if (item.faceCount > 1) "s" else ""})" else " DROPPED (0 faces)",
                                color = if (item.isAccepted) Color(0xFF34D399) else Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Image(
                            bitmap = item.annotatedBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Step3BoundingBoxCutsTab(cuts: List<BoundingBoxCutItem>) {
    val byFrame = remember(cuts) { cuts.groupBy { it.frameIndex } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(byFrame.keys.toList()) { frameIndex ->
            val frameCuts = byFrame[frameIndex] ?: emptyList()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Frame #$frameIndex (${frameCuts.firstOrNull()?.timestampMs ?: 0}ms) -> ${frameCuts.size} Bounding Box Cut${if (frameCuts.size > 1) "s" else ""}",
                        color = Color(0xFFFFFFFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(frameCuts) { cut ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF121212))
                                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                            ) {
                                Image(
                                    bitmap = cut.faceCrop.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(75.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Face #${cut.faceId}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${cut.boundingBox.width()}x${cut.boundingBox.height()}", color = Color(0xFFA1A1AA), fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepEdgeClippingTab(
    allFaces: List<DetectedFaceItem>,
    isFilterEnabled: Boolean,
    marginPx: Int = 10
) {
    var selection by remember { mutableIntStateOf(0) }

    val passedList = remember(allFaces) { allFaces.filter { !it.isEdgeClipped } }
    val clippedList = remember(allFaces) { allFaces.filter { it.isEdgeClipped } }

    val displayed = when (selection) {
        1 -> passedList
        2 -> clippedList
        else -> allFaces
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edge Clipping Stage (${allFaces.size} Cuts Evaluated)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Border Margin: ${marginPx}px | Filter Active: ${if (isFilterEnabled) "YES" else "NO"}",
                            color = Color(0xFFA1A1AA),
                            fontSize = 10.sp
                        )
                    }
                    Surface(
                        color = if (isFilterEnabled) Color(0xFF065F46) else Color(0xFF7F1D1D),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isFilterEnabled) "Passed: ${passedList.size} / ${allFaces.size}" else "Disabled",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selection == 0,
                        onClick = { selection = 0 },
                        label = { Text("All (${allFaces.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF27272A), selectedLabelColor = Color.White)
                    )
                    FilterChip(
                        selected = selection == 1,
                        onClick = { selection = 1 },
                        label = { Text("Passed (${passedList.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White)
                    )
                    FilterChip(
                        selected = selection == 2,
                        onClick = { selection = 2 },
                        label = { Text("Clipped (${clippedList.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEF4444), selectedLabelColor = Color.White)
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayed, key = { it.id }) { face ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (face.isEdgeClipped) Color(0xFF2A1515) else Color(0xFF0F291E)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (face.isEdgeClipped) Color(0xFFEF4444) else Color(0xFF10B981))
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = face.faceCrop.asImageBitmap(),
                            contentDescription = "Cut #${face.id}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cut #${face.id} (F#${face.frameIndex})",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val box = face.boundingBox
                        Text(
                            text = "[${box.left}, ${box.top} - ${box.right}, ${box.bottom}]",
                            color = Color(0xFFA1A1AA),
                            fontSize = 8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (face.isEdgeClipped) {
                            Text(
                                text = " ${face.edgeClipReason ?: "Clipped"}",
                                color = Color(0xFFFCA5A5),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        } else {
                            Text(
                                text = " Inside Bounds",
                                color = Color(0xFF86EFAC),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Step4PreprocessingTab(faces: List<DetectedFaceItem>) {
    var selection by remember { mutableIntStateOf(0) }

    val passedFaces = remember(faces) { faces.filter { it.isValid } }
    val rejectedFaces = remember(faces) { faces.filter { !it.isValid } }

    val displayed = when (selection) {
        1 -> passedFaces
        2 -> rejectedFaces
        else -> faces
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Preprocessing Stage (${faces.size} Evaluated)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Blur, Sharpness, Frontality & 112x112 Alignment",
                            color = Color(0xFFA1A1AA),
                            fontSize = 10.sp
                        )
                    }
                    Surface(
                        color = Color(0xFF065F46),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Passed: ${passedFaces.size} / ${faces.size}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selection == 0,
                        onClick = { selection = 0 },
                        label = { Text("All (${faces.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF27272A), selectedLabelColor = Color.White)
                    )
                    FilterChip(
                        selected = selection == 1,
                        onClick = { selection = 1 },
                        label = { Text("Passed (${passedFaces.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White)
                    )
                    FilterChip(
                        selected = selection == 2,
                        onClick = { selection = 2 },
                        label = { Text("Rejected (${rejectedFaces.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEF4444), selectedLabelColor = Color.White)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayed, key = { it.id }) { face ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (face.isValid) Color(0xFF132A22) else Color(0xFF2A1515)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = face.faceCrop.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text("Crop", color = Color(0xFFA1A1AA), fontSize = 8.sp)
                    }

                    face.alignedCrop112?.let { aligned ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                bitmap = aligned.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text("112x112", color = Color(0xFFFFFFFF), fontSize = 8.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Face #${face.id} (Frame #${face.frameIndex})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (face.isValid) "PASSED" else "REJECTED",
                                color = if (face.isValid) Color(0xFF34D399) else Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        if (!face.isValid && face.failureReason != null) {
                            Text(
                                text = face.failureReason,
                                color = Color(0xFFFCA5A5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Pose: yaw ${"%.1f".format(face.yaw)}°, pitch ${"%.1f".format(face.pitch)}°, roll ${"%.1f".format(face.roll)}°",
                            color = Color(0xFFF4F4F5),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Quality: blur ${"%.1f".format(face.blurScore)}, sharpness ${"%.0f".format(face.sharpnessScore)}",
                            color = Color(0xFFA1A1AA),
                            fontSize = 10.sp
                        )

                        face.embedding?.let { emb ->
                            val sample = emb.take(4).joinToString(", ") { "%.3f".format(it) }
                            Text(
                                text = "Embedding: 192D [$sample, ...]",
                                color = Color(0xFFFFFFFF),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

}

@Composable
fun Step5ClustersAndCollageTab(
    result: DebugPipelineResult,
    currentThreshold: Float,
    onThresholdChange: (Float) -> Unit,
    onProceed: (() -> Unit)? = null
) {
    var showAssignmentLog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Identity Threshold: ${"%.2f".format(currentThreshold)}",
                            color = Color(0xFFFFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = currentThreshold,
                            onValueChange = onThresholdChange,
                            valueRange = 0.40f..0.85f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (onProceed != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onProceed,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = "Proceed ->",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            val displayCollage = remember(result.collageBitmap, result.representativeBitmaps) {
                result.collageBitmap ?: DebugCollageMaker.generateCollage(
                    result.representativeBitmaps.ifEmpty {
                        result.clusters.mapNotNull { it.representativeFace?.faceCrop }
                    }
                )
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                border = BorderStroke(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Synthesized Collage Output",
                            color = Color(0xFFFFFFFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = Color(0xFF065F46),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            val count = result.representativeBitmaps.size.coerceAtLeast(result.clusters.size)
                            Text(
                                text = "$count Face${if (count == 1) "" else "s"}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (displayCollage != null) {
                        Image(
                            bitmap = displayCollage.asImageBitmap(),
                            contentDescription = "Synthesized Collage",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF181818), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No collage generated yet", color = Color(0xFFA1A1AA), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clusters (${result.clusters.size} Unique Identities):",
                    color = Color(0xFFFFFFFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showAssignmentLog = !showAssignmentLog },
                    colors = ButtonDefaults.buttonColors(containerColor = if (showAssignmentLog) Color(0xFF27272A) else Color(0xFF27272A)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (showAssignmentLog) "Hide Match Log" else "Show Match Log (${result.assignmentEvents.size})", fontSize = 9.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (showAssignmentLog && result.assignmentEvents.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Sequential Assignment Decisions:", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        for (event in result.assignmentEvents) {
                            val badgeColor = if (event.isNewCluster) Color(0xFFFFFFFF) else Color(0xFF34D399)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = event.explanation,
                                    color = Color(0xFFF4F4F5),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (event.isNewCluster) "NEW SEED" else "sim=${"%.2f".format(event.similarityScore)}",
                                    color = badgeColor,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        items(result.clusters) { cluster ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cluster #${cluster.clusterId}",
                                color = Color(0xFFFFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${cluster.memberFaces.size} assigned face sample(s)",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.sp
                            )
                        }

                        cluster.representativeFace?.let { rep ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Representative",
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Face #${rep.id} (sharpness: ${"%.0f".format(rep.sharpnessScore)})",
                                    color = Color(0xFFF4F4F5),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Assigned Faces in this Cluster:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cluster.memberFaces) { member ->
                            val isWinner = member.id == cluster.representativeFace?.id
                            val sim = member.embedding?.let {
                                val s = DebugTFLiteModel.cosineSimilarity(it, cluster.centroid)
                                if (s.isNaN()) 0f else s
                            } ?: 0f

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isWinner) Color(0xFF065F46) else Color(0xFF27272A))
                                    .border(
                                        width = if (isWinner) 2.dp else 1.dp,
                                        color = if (isWinner) Color(0xFF10B981) else Color(0xFFA1A1AA),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(5.dp)
                            ) {
                                Image(
                                    bitmap = member.faceCrop.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Face #${member.id}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "F#${member.frameIndex} (${member.timestampMs}ms)",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = "sim: ${"%.3f".format(sim)}",
                                    color = if (sim >= currentThreshold) Color(0xFF34D399) else Color(0xFFF87171),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isWinner) {
                                    Text("Representative", color = Color(0xFF34D399), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun EmptyStateView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = Color(0xFFA1A1AA), fontSize = 12.sp)
    }
}

@Composable
fun LiveLogsTab(logs: List<String>, onClear: () -> Unit, onCopy: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Logcat & Timing Console",
                color = Color(0xFFFFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("Copy", fontSize = 10.sp) }
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("Clear", fontSize = 10.sp) }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF181818), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Logs will stream here in real time during video decoding and ML inference...",
                    color = Color(0xFF3F3F46),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { line ->
                        val color = when {
                            line.contains("REJECTED") || line.contains("Rejected") -> Color(0xFFF87171)
                            line.contains("") || line.contains("VALID") -> Color(0xFF34D399)
                            line.contains("===") -> Color(0xFFFBBF24)
                            line.contains("[Decode") -> Color(0xFFA1A1AA)
                            line.startsWith("[Video]") -> Color(0xFFF43F5E)
                            else -> Color(0xFFF4F4F5)
                        }
                        Text(
                            text = line,
                            color = color,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
