package com.iykyk.task0.debug

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "IYKYK_DEBUG"

data class ExtractedVideoFrame(
    val timestampMs: Long,
    val bitmap: Bitmap,
    val frameIndex: Int,
    val decodeTimeMs: Long
)

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val totalFrameCount: Int
)

object VideoFrameExtractor {

    suspend fun getMetadata(context: Context, uri: Uri): VideoMetadata? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var pfd: ParcelFileDescriptor? = null
        return@withContext try {
            if (uri.scheme == "file" && uri.path != null && File(uri.path!!).exists()) {
                retriever.setDataSource(uri.path)
            } else {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    retriever.setDataSource(pfd.fileDescriptor)
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
            extractMetadata(retriever)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video metadata: ${e.message}", e)
            null
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractMetadata(retriever: MediaMetadataRetriever): VideoMetadata {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
        return VideoMetadata(duration, width, height, rotation, frameCount)
    }

    suspend fun extractFramesFromUri(
        context: Context,
        uri: Uri,
        intervalMs: Long = 250L,
        maxFrames: Int = 40,
        targetMaxDimension: Int = 960,
        onLog: (String) -> Unit = {},
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ExtractedVideoFrame> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<ExtractedVideoFrame>()
        var pfd: ParcelFileDescriptor? = null

        try {
            if (uri.scheme == "file" && uri.path != null && File(uri.path!!).exists()) {
                retriever.setDataSource(uri.path)
                onLog("DataSource: File path (${uri.path})")
            } else {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    retriever.setDataSource(pfd.fileDescriptor)
                    onLog("DataSource: Opened POSIX FileDescriptor")
                } else {
                    retriever.setDataSource(context, uri)
                    onLog("DataSource: Content URI stream")
                }
            }

            val meta = extractMetadata(retriever)
            val durationMs = meta.durationMs
            if (durationMs <= 0) {
                onLog("Error: Duration is 0ms or invalid video")
                return@withContext emptyList()
            }

            onLog("Video: ${meta.width}x${meta.height}, duration=${durationMs}ms, frames=${meta.totalFrameCount}")
            Log.i(TAG, "[VideoDecoder] Video ${meta.width}x${meta.height}, duration=${durationMs}ms")

            val (targetW, targetH) = computeTargetDimensions(meta.width, meta.height, targetMaxDimension)
            onLog("Target decode resolution: ${targetW}x${targetH}")

            val totalExpected = ((durationMs / intervalMs) + 1).toInt().coerceAtMost(maxFrames)
            var currentTimeMs = 0L
            var frameIndex = 0

            val useFrameIndex = meta.totalFrameCount > 10 && meta.totalFrameCount >= totalExpected
            val frameIndexStep = if (useFrameIndex) (meta.totalFrameCount.toFloat() / totalExpected).coerceAtLeast(1f) else 1f

            while (currentTimeMs <= durationMs && frameIndex < maxFrames) {
                val timeUs = currentTimeMs * 1000L
                val t0 = System.currentTimeMillis()

                var frameBitmap: Bitmap? = null

                if (useFrameIndex && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val targetIdx = (frameIndex * frameIndexStep).toInt().coerceAtMost(meta.totalFrameCount - 1)
                    try {
                        frameBitmap = retriever.getFrameAtIndex(targetIdx)
                        if (frameBitmap != null && (frameBitmap.width > targetW || frameBitmap.height > targetH)) {
                            frameBitmap = Bitmap.createScaledBitmap(frameBitmap, targetW, targetH, true)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getFrameAtIndex failed: ${e.message}")
                    }
                }

                if (frameBitmap == null) {
                    frameBitmap = decodeFrame(retriever, timeUs, targetW, targetH)
                }

                val decodeMs = System.currentTimeMillis() - t0

                if (frameBitmap != null) {
                    val pCenter = frameBitmap.getPixel(frameBitmap.width / 2, frameBitmap.height / 2)
                    frames.add(ExtractedVideoFrame(currentTimeMs, frameBitmap, frameIndex, decodeMs))
                    val msg = "[Decode #$frameIndex] at ${currentTimeMs}ms in ${decodeMs}ms (${frameBitmap.width}x${frameBitmap.height}, pixel=${Integer.toHexString(pCenter)})"
                    onLog(msg)
                    Log.i(TAG, msg)
                } else {
                    val msg = "[Decode #$frameIndex] Skipped (null) at ${currentTimeMs}ms (${decodeMs}ms)"
                    onLog(msg)
                    Log.w(TAG, msg)
                }

                frameIndex++
                currentTimeMs += intervalMs
                onProgress(frameIndex, totalExpected)
            }
        } catch (e: Exception) {
            val err = "Error extracting frames: ${e.message}"
            onLog(err)
            Log.e(TAG, err, e)
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            try { retriever.release() } catch (_: Exception) {}
        }

        return@withContext frames
    }

    suspend fun extractFramesFromAsset(
        context: Context,
        assetName: String,
        intervalMs: Long = 250L,
        maxFrames: Int = 40,
        targetMaxDimension: Int = 960,
        onLog: (String) -> Unit = {},
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<ExtractedVideoFrame> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<ExtractedVideoFrame>()

        try {
            val afd = context.assets.openFd(assetName)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            val meta = extractMetadata(retriever)
            val durationMs = meta.durationMs
            if (durationMs <= 0) return@withContext emptyList()

            onLog("Asset Video: ${meta.width}x${meta.height}, duration=${durationMs}ms, frames=${meta.totalFrameCount}")
            Log.i(TAG, "[VideoDecoder] Asset ${meta.width}x${meta.height}, duration=${durationMs}ms")

            val (targetW, targetH) = computeTargetDimensions(meta.width, meta.height, targetMaxDimension)
            onLog("Target decode resolution: ${targetW}x${targetH}")

            val totalExpected = ((durationMs / intervalMs) + 1).toInt().coerceAtMost(maxFrames)
            var currentTimeMs = 0L
            var frameIndex = 0

            val useFrameIndex = meta.totalFrameCount > 10 && meta.totalFrameCount >= totalExpected
            val frameIndexStep = if (useFrameIndex) (meta.totalFrameCount.toFloat() / totalExpected).coerceAtLeast(1f) else 1f

            while (currentTimeMs <= durationMs && frameIndex < maxFrames) {
                val timeUs = currentTimeMs * 1000L
                val t0 = System.currentTimeMillis()

                var frameBitmap: Bitmap? = null

                if (useFrameIndex && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val targetIdx = (frameIndex * frameIndexStep).toInt().coerceAtMost(meta.totalFrameCount - 1)
                    try {
                        frameBitmap = retriever.getFrameAtIndex(targetIdx)
                        if (frameBitmap != null && (frameBitmap.width > targetW || frameBitmap.height > targetH)) {
                            frameBitmap = Bitmap.createScaledBitmap(frameBitmap, targetW, targetH, true)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "getFrameAtIndex failed: ${e.message}")
                    }
                }

                if (frameBitmap == null) {
                    frameBitmap = decodeFrame(retriever, timeUs, targetW, targetH)
                }

                val decodeMs = System.currentTimeMillis() - t0

                if (frameBitmap != null) {
                    val pCenter = frameBitmap.getPixel(frameBitmap.width / 2, frameBitmap.height / 2)
                    frames.add(ExtractedVideoFrame(currentTimeMs, frameBitmap, frameIndex, decodeMs))
                    val msg = "[Decode #$frameIndex] at ${currentTimeMs}ms in ${decodeMs}ms (${frameBitmap.width}x${frameBitmap.height}, pixel=${Integer.toHexString(pCenter)})"
                    onLog(msg)
                    Log.i(TAG, msg)
                } else {
                    val msg = "[Decode #$frameIndex] Skipped (null) at ${currentTimeMs}ms (${decodeMs}ms)"
                    onLog(msg)
                    Log.w(TAG, msg)
                }

                frameIndex++
                currentTimeMs += intervalMs
                onProgress(frameIndex, totalExpected)
            }
        } catch (e: Exception) {
            val err = "Error extracting asset frames: ${e.message}"
            onLog(err)
            Log.e(TAG, err, e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return@withContext frames
    }

    private fun computeTargetDimensions(w: Int, h: Int, maxDim: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 960 to 540
        if (maxOf(w, h) <= maxDim) return w to h

        val scale = maxDim.toFloat() / maxOf(w, h)
        val dstW = ((w * scale).toInt() / 2) * 2
        val dstH = ((h * scale).toInt() / 2) * 2
        return dstW.coerceAtLeast(320) to dstH.coerceAtLeast(320)
    }

    private fun decodeFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        targetW: Int,
        targetH: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val scaled = retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetW,
                    targetH
                )
                if (scaled != null) return scaled
            } catch (e: Exception) {
                Log.w(TAG, "getScaledFrameAtTime failed: ${e.message}")
            }
        }

        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
    }
}
