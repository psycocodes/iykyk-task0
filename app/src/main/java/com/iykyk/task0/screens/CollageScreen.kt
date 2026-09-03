package com.iykyk.task0.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.iykyk.task0.R
import com.iykyk.task0.ui.components.CollageActionButtons
import com.iykyk.task0.ui.components.CollageHeader
import com.iykyk.task0.ui.components.FaceCollageGrid
import com.iykyk.task0.ui.components.NoFacesCard
import com.iykyk.task0.ui.components.PillButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying the finalized face collage or the empty state if 0 faces were found.
 *
 * Automatically branches between Portrait and Landscape layouts using modular layout components.
 *
 * @param collageFile The exported JPEG file of the synthesized collage (nullable if no faces detected).
 * @param representatives List of clustered unique face portrait bitmaps.
 * @param onRecordAgainClick Callback to clear pipeline and return to the Camera recording screen.
 * @param modifier Modifier applied to the root Surface container.
 */
@Composable
fun CollageScreen(
    collageFile: File?,
    representatives: List<Bitmap>,
    onRecordAgainClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalCount = representatives.size

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        if (totalCount == 0) {
            NoFacesScreen(onRecordClick = onRecordAgainClick)
        } else {
            FacesGridScreen(
                faces = representatives,
                onShareClick = { shareCollage(context, collageFile) },
                onSaveClick = { saveToGallery(context, collageFile) },
                onRecordClick = onRecordAgainClick
            )
        }
    }
}

/**
 * Screen presentation for 1 or more clustered face portraits.
 */
@Composable
private fun FacesGridScreen(
    faces: List<Bitmap>,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRecordClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val totalCount = faces.size

    val numCols = when {
        totalCount <= 1 -> 1
        totalCount in 2..4 -> 2
        totalCount in 5..9 -> 3
        else -> 4
    }
    val isScrollable = totalCount > (numCols * 3)

    val buttonScale = remember { Animatable(0.6f) }
    val buttonAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        buttonAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        buttonScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 24.dp, horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                FaceCollageGrid(
                    faces = faces,
                    numCols = numCols,
                    isScrollable = isScrollable,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(end = 12.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                CollageHeader(
                    totalCount = totalCount,
                    isLandscape = true,
                    topPadding = 0.dp
                )

                Spacer(modifier = Modifier.height(22.dp))

                CollageActionButtons(
                    buttonScale = buttonScale.value,
                    buttonAlpha = buttonAlpha.value,
                    onShareClick = onShareClick,
                    onSaveClick = onSaveClick,
                    onRecordClick = onRecordClick,
                    isLandscape = true
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CollageHeader(totalCount = totalCount, isLandscape = false, topPadding = 40.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                FaceCollageGrid(
                    faces = faces,
                    numCols = numCols,
                    isScrollable = isScrollable,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            CollageActionButtons(
                buttonScale = buttonScale.value,
                buttonAlpha = buttonAlpha.value,
                onShareClick = onShareClick,
                onSaveClick = onSaveClick,
                onRecordClick = onRecordClick,
                maxWidth = 230.dp,
                bottomPadding = 24.dp,
                isLandscape = false
            )
        }
    }
}

/**
 * Screen presentation for the empty state when no faces were detected in the recorded clip.
 */
@Composable
private fun NoFacesScreen(
    onRecordClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val expandX = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        expandX.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                NoFacesCard(
                    isLandscape = true,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                CollageHeader(totalCount = 0, isLandscape = true, topPadding = 0.dp)

                Spacer(modifier = Modifier.height(24.dp))

                PillButton(
                    text = "record clip",
                    iconResId = R.drawable.record,
                    backgroundColor = Color(0xFF8C2E24),
                    onClick = onRecordClick,
                    height = 52.dp,
                    cornerRadius = 26.dp,
                    iconSize = 26.dp,
                    fontSize = 23.sp,
                    expandScaleX = expandX.value,
                    modifier = Modifier.width(265.dp)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CollageHeader(totalCount = 0, isLandscape = false, topPadding = 40.dp)

            Spacer(modifier = Modifier.height(36.dp))

            NoFacesCard(isLandscape = false)

            Spacer(modifier = Modifier.height(50.dp))

            PillButton(
                text = "record clip",
                iconResId = R.drawable.record,
                backgroundColor = Color(0xFF8C2E24),
                onClick = onRecordClick,
                height = 60.dp,
                cornerRadius = 30.dp,
                iconSize = 30.dp,
                fontSize = 26.sp,
                expandScaleX = expandX.value,
                modifier = Modifier.width(200.dp)
            )
        }
    }
}

/**
 * Copies the generated collage image to the device's public Pictures/IYKYK gallery directory.
 */
private fun saveToGallery(context: Context, collageFile: File?) {
    if (collageFile == null || !collageFile.exists()) {
        Toast.makeText(context, "Collage file not found", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val iykykDir = File(picturesDir, "IYKYK")
        if (!iykykDir.exists()) iykykDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destFile = File(iykykDir, "COLLAGE_$timeStamp.jpg")

        collageFile.copyTo(destFile, overwrite = true)

        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("image/jpeg"),
            null
        )

        Toast.makeText(context, "✓ Saved to Gallery", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Saved to App Storage", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens the Android system Share sheet allowing sharing of the generated collage JPEG.
 */
private fun shareCollage(context: Context, collageFile: File?) {
    if (collageFile == null || !collageFile.exists()) {
        Toast.makeText(context, "Collage file not found", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            collageFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "Look at my IYKYK Face Collage! 📸")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Collage"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
