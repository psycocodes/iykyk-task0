package com.iykyk.task0.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.ui.theme.Baloo2

/**
 * Reusable dynamic pill button component supporting custom themes, optional icon badges, and scale animations.
 *
 * @param text Button label text.
 * @param iconResId Optional drawable resource ID for the button's leading icon (null for text-only button).
 * @param backgroundColor Background fill color.
 * @param onClick Callback triggered on click.
 * @param modifier Modifier applied to the outer container.
 * @param height Total vertical button height.
 * @param cornerRadius Corner radius of the pill capsule shape.
 * @param iconSize Size of the leading icon graphic.
 * @param fontSize Font size for the button label text.
 * @param textColor Color of the label text.
 * @param contentPadding Horizontal inner padding inside the button.
 * @param expandScaleX Horizontal scale animation factor.
 * @param alpha Opacity animation factor.
 */
@Composable
fun PillButton(
    text: String,
    iconResId: Int? = null,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    cornerRadius: Dp = 30.dp,
    iconSize: Dp = 30.dp,
    fontSize: TextUnit = 26.sp,
    textColor: Color = Color.White,
    contentPadding: Dp = 16.dp,
    expandScaleX: Float = 1f,
    alpha: Float = 1f
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = expandScaleX
                this.alpha = alpha
            }
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconResId != null) {
                Image(
                    painter = painterResource(iconResId),
                    contentDescription = text,
                    modifier = Modifier.size(iconSize)
                )

                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = text,
                fontFamily = Baloo2,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                color = textColor
            )
        }
    }
}
