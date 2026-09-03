package com.iykyk.task0.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val MAX_RECORD_SECONDS = 20
private const val TAG = "IYKYK_RECORDER"

/**
 * Manages CameraX video recording sessions, countdown timing events, and file persistence.
 *
 * @param context Application context used for executor access and filesystem paths.
 * @param scope Coroutine scope for asynchronous callbacks.
 */
class VideoRecorder(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var activeRecording: Recording? = null
    private var lastEmittedSec = -1
    private var isCancelled = false

    /**
     * Initiates a new video recording session writing to an MP4 file.
     *
     * @param videoCapture Active CameraX VideoCapture use case.
     * @param maxSeconds Maximum duration before recording automatically finalizes.
     * @param onTick Callback fired whenever a whole elapsed second passes.
     * @param onFinished Callback fired upon finalization with the file or an error string.
     */
    fun startRecording(
        videoCapture: VideoCapture<Recorder>,
        maxSeconds: Int = MAX_RECORD_SECONDS,
        onTick: (elapsedSec: Int) -> Unit,
        onFinished: (file: File?, error: String?) -> Unit
    ) {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        if (!moviesDir.exists()) moviesDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val mp4File = File(moviesDir, "VID_$timeStamp.mp4")
        val outputOptions = FileOutputOptions.Builder(mp4File).build()

        lastEmittedSec = -1
        isCancelled = false

        val pending = videoCapture.output.prepareRecording(context, outputOptions)

        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    onTick(0)
                }
                is VideoRecordEvent.Status -> {
                    val durationNanos = event.recordingStats.recordedDurationNanos
                    val currentSec = (durationNanos / 1_000_000_000L).toInt()

                    if (currentSec != lastEmittedSec) {
                        lastEmittedSec = currentSec
                        onTick(currentSec)
                    }

                    if (currentSec >= maxSeconds) {
                        stopRecording()
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    if (isCancelled) {
                        try {
                            if (mp4File.exists()) mp4File.delete()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error deleting cancelled file: ${e.message}")
                        }
                        onFinished(null, null)
                        return@start
                    }

                    val isSuccess = !event.hasError() || 
                        (event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE && mp4File.exists() && mp4File.length() > 0)

                    if (isSuccess) {
                        Log.d(TAG, "✓ Video saved: ${mp4File.absolutePath}")
                        onFinished(mp4File, null)
                    } else {
                        Log.e(TAG, "Recording failed with error: ${event.error}")
                        onFinished(null, "Recording error: ${event.error}")
                    }
                }
            }
        }
    }

    /**
     * Requests the active recording session to stop and finalize gracefully.
     */
    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "activeRecording.stop() exception: ${e.message}")
        }
        activeRecording = null
    }

    /**
     * Cancels the active recording session, discarding and deleting any partially recorded video file.
     */
    fun cancelRecording() {
        isCancelled = true
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "activeRecording.stop() exception: ${e.message}")
        }
        activeRecording = null
    }
}
