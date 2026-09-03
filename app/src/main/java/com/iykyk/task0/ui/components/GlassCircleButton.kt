package com.iykyk.task0.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Circular glassmorphic action button used for camera flip and cancel controls.
 *
 * @param iconResId Drawable resource ID of the center icon.
 * @param contentDescription Accessibility description for the action.
 * @param onClick Callback triggered on press.
 * @param modifier Modifier applied to the outer container.
 * @param enabled Whether the button responds to click interaction.
 * @param iconRotation Optional rotation degrees applied to the icon graphic.
 */
@Composable
fun GlassCircleButton(
    iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconRotation: Float = 0f
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x771E293B),
                        Color(0x990F172A)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x66FFFFFF),
                        Color(0x1AFFFFFF)
                    )
                ),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .size(26.dp)
                .rotate(iconRotation)
        )
    }
}
