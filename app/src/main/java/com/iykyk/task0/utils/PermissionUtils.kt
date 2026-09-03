package com.iykyk.task0.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Encapsulates runtime camera permission state, denial status, and action launchers.
 *
 * @property hasPermission True when CAMERA permission has been granted by user.
 * @property hasDeniedOnce True when the user has denied camera permission at least once.
 * @property requestPermission Action triggering system permission dialog or settings navigation.
 * @property openAppSettings Action opening Android application detail settings.
 */
data class CameraPermissionState(
    val hasPermission: Boolean,
    val hasDeniedOnce: Boolean,
    val requestPermission: () -> Unit,
    val openAppSettings: () -> Unit
)

/**
 * Utility helpers for camera permission validation.
 */
object PermissionUtils {
    const val CAMERA_PERMISSION = Manifest.permission.CAMERA

    /**
     * Checks whether the application holds granted CAMERA permission.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Composable remember state managing reactive camera permission status, denial history, and settings navigation.
 */
@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("iykyk_permissions", Context.MODE_PRIVATE)
    }

    var hasPermission by remember {
        mutableStateOf(PermissionUtils.hasCameraPermission(context))
    }

    var hasDeniedOnce by remember {
        mutableStateOf(prefs.getBoolean("has_denied_camera", false))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentPermission = PermissionUtils.hasCameraPermission(context)
                hasPermission = currentPermission
                if (currentPermission) {
                    hasDeniedOnce = false
                    prefs.edit().putBoolean("has_denied_camera", false).apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            hasDeniedOnce = true
            prefs.edit().putBoolean("has_denied_camera", true).apply()
        } else {
            hasDeniedOnce = false
            prefs.edit().putBoolean("has_denied_camera", false).apply()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !hasDeniedOnce) {
            launcher.launch(PermissionUtils.CAMERA_PERMISSION)
        }
    }

    val openSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    return CameraPermissionState(
        hasPermission = hasPermission,
        hasDeniedOnce = hasDeniedOnce,
        requestPermission = {
            if (hasDeniedOnce) {
                openSettings()
            } else {
                launcher.launch(PermissionUtils.CAMERA_PERMISSION)
            }
        },
        openAppSettings = openSettings
    )
}
