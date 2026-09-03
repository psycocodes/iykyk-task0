package com.iykyk.task0.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.ui.theme.Baloo2

/**
 * Circular record button with animated countdown timer ring.
 *
 * Features a continuous sweeping gradient countdown ring during active recording,
 * transforming from duration text ("20") to a stop square icon.
 *
 * @param isRecording Active recording state flag.
 * @param maxSeconds Target duration in seconds for maximum recording length.
 * @param onClick Callback triggered on press.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun CircularTimerRecordButton(
    isRecording: Boolean,
    maxSeconds: Int = 20,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "press_scale"
    )

    val ringProgress = remember { Animatable(1f) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            ringProgress.snapTo(1f)
            ringProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = maxSeconds * 1000 * 2,
                    easing = LinearEasing
                )
            )
        } else {
            ringProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .size(86.dp)
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.5.dp.toPx()

            if (ringProgress.value > 0f) {
                val timerGradient = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF3B30).copy(alpha = 0.8f),
                        Color(0xFFFF6B2B).copy(alpha = 0.8f),
                        Color(0xFFFF9500).copy(alpha = 0.8f),
                        Color(0xFFFF5722).copy(alpha = 0.8f),
                        Color(0xFFFF3B30).copy(alpha = 0.8f)
                    )
                )
                drawArc(
                    brush = timerGradient,
                    startAngle = -90f,
                    sweepAngle = 360f * ringProgress.value,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(75.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE50914),
                            Color(0xFFB8000A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "button_content"
            ) { recording ->
                if (recording) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                } else {
                    Text(
                        text = "$maxSeconds",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = Baloo2
                    )
                }
            }
        }
    }
}
