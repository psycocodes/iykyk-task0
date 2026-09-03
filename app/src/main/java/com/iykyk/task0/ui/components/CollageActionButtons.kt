package com.iykyk.task0.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.R

/**
 * Composite action buttons component for the Face Collage screen.
 *
 * Internally handles both portrait and landscape representations via [isLandscape].
 *
 * @param buttonScale Animated scale factor applied to the buttons.
 * @param buttonAlpha Animated alpha transparency factor applied to the buttons.
 * @param onShareClick Callback triggered on tapping Share.
 * @param onSaveClick Callback triggered on tapping Save.
 * @param onRecordClick Callback triggered on tapping Record Clip.
 * @param modifier Modifier applied to the button container.
 * @param isLandscape Boolean flag toggling between portrait and landscape layouts.
 * @param bottomPadding Bottom margin offset for portrait mode.
 * @param maxWidth Maximum width constraint for portrait mode.
 */
@Composable
fun CollageActionButtons(
    buttonScale: Float,
    buttonAlpha: Float,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    bottomPadding: Dp = if (isLandscape) 0.dp else 24.dp,
    maxWidth: Dp = 230.dp
) {
    if (isLandscape) {
        Column(
            modifier = modifier.width(265.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.width(265.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PillButton(
                    text = "save",
                    iconResId = R.drawable.save,
                    backgroundColor = Color(0xFF008080),
                    onClick = onSaveClick,
                    height = 52.dp,
                    cornerRadius = 26.dp,
                    iconSize = 26.dp,
                    fontSize = 23.sp,
                    expandScaleX = buttonScale,
                    alpha = buttonAlpha,
                    modifier = Modifier.weight(1f)
                )

                PillButton(
                    text = "share",
                    iconResId = R.drawable.share,
                    backgroundColor = Color(0xFF008080),
                    onClick = onShareClick,
                    height = 52.dp,
                    cornerRadius = 26.dp,
                    iconSize = 26.dp,
                    fontSize = 23.sp,
                    expandScaleX = buttonScale,
                    alpha = buttonAlpha,
                    modifier = Modifier.weight(1f)
                )
            }

            PillButton(
                text = "record clip",
                iconResId = R.drawable.record,
                backgroundColor = Color(0xFF8C2E24),
                onClick = onRecordClick,
                height = 52.dp,
                cornerRadius = 26.dp,
                iconSize = 26.dp,
                fontSize = 23.sp,
                expandScaleX = buttonScale,
                alpha = buttonAlpha,
                modifier = Modifier.width(265.dp)
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(horizontal = 8.dp)
                .padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PillButton(
                    text = "share",
                    iconResId = R.drawable.share,
                    backgroundColor = Color(0xFF008080),
                    onClick = onShareClick,
                    height = 56.dp,
                    cornerRadius = 28.dp,
                    iconSize = 26.dp,
                    fontSize = 22.sp,
                    expandScaleX = buttonScale,
                    alpha = buttonAlpha,
                    modifier = Modifier.weight(1f)
                )

                PillButton(
                    text = "save",
                    iconResId = R.drawable.save,
                    backgroundColor = Color(0xFF008080),
                    onClick = onSaveClick,
                    height = 56.dp,
                    cornerRadius = 28.dp,
                    iconSize = 26.dp,
                    fontSize = 22.sp,
                    expandScaleX = buttonScale,
                    alpha = buttonAlpha,
                    modifier = Modifier.weight(1f)
                )
            }

            PillButton(
                text = "record clip",
                iconResId = R.drawable.record,
                backgroundColor = Color(0xFF8C2E24),
                onClick = onRecordClick,
                height = 56.dp,
                cornerRadius = 28.dp,
                iconSize = 26.dp,
                fontSize = 22.sp,
                expandScaleX = buttonScale,
                alpha = buttonAlpha,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
