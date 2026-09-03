package com.iykyk.task0.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.R
import com.iykyk.task0.ui.components.PillButton
import com.iykyk.task0.ui.theme.Baloo2
import com.iykyk.task0.ui.theme.SFPro

/**
 * Screen presented when the CAMERA permission has not yet been granted.
 *
 * Responsively scales across Portrait and wide Landscape orientations without splitting into separate panes.
 * Directs the user to App Settings if permission has been denied.
 *
 * @param onGrantPermissionClick Callback invoked when tapping to grant permission or open settings.
 * @param hasDeniedOnce True if the user has previously denied camera access.
 * @param modifier Modifier applied to the root Surface container.
 */
@Composable
fun PermissionScreen(
    onGrantPermissionClick: () -> Unit,
    hasDeniedOnce: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val titleText = "Camera Access"
    val subtitleText = if (hasDeniedOnce) {
        "Camera permission was denied. Please allow camera access in App Settings to record clips and detect faces."
    } else {
        "To record video clips up to 20 seconds and detect faces, the app requires access to your camera."
    }
    val buttonText = if (hasDeniedOnce) "open settings" else "grant permission"

    val badgeSize = if (isLandscape) 76.dp else 96.dp
    val iconSize = if (isLandscape) 38.dp else 46.dp
    val titleSize = if (isLandscape) 28.sp else 32.sp
    val subtitleSize = if (isLandscape) 15.sp else 16.sp
    val subtitleMaxWidth = if (isLandscape) 520.dp else 320.dp
    val spacer1 = if (isLandscape) 14.dp else 28.dp
    val spacer2 = if (isLandscape) 6.dp else 12.dp
    val spacer3 = if (isLandscape) 20.dp else 36.dp
    val buttonHeight = if (isLandscape) 52.dp else 56.dp
    val buttonCorner = if (isLandscape) 26.dp else 28.dp

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (isLandscape) 48.dp else 32.dp, vertical = if (isLandscape) 16.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(
                        color = Color(0xFF1E293B),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = "Camera Access Required",
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }

            Spacer(modifier = Modifier.height(spacer1))

            Text(
                text = titleText,
                fontFamily = Baloo2,
                fontWeight = FontWeight.ExtraBold,
                fontSize = titleSize,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(spacer2))

            Text(
                text = subtitleText,
                fontFamily = SFPro,
                fontWeight = FontWeight.Normal,
                fontSize = subtitleSize,
                textAlign = TextAlign.Center,
                color = Color(0xFF7A7A85),
                modifier = Modifier.widthIn(max = subtitleMaxWidth)
            )

            Spacer(modifier = Modifier.height(spacer3))

            PillButton(
                text = buttonText,
                iconResId = null,
                backgroundColor = Color(0xFF008080),
                onClick = onGrantPermissionClick,
                height = buttonHeight,
                cornerRadius = buttonCorner,
                fontSize = 22.sp,
                modifier = Modifier.widthIn(max = 350.dp)
            )
        }
    }
}
