package com.iykyk.task0.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.R
import com.iykyk.task0.ui.theme.Baloo2
import com.iykyk.task0.ui.theme.SFPro

/**
 * Empty state graphic card presenting the sad blob illustration and retry prompts.
 *
 * @param isLandscape Orientation flag adjusting graphic scale and typography sizes.
 * @param illustrationSize Explicit size override for the sad blob illustration.
 * @param titleSize Explicit font size override for the "no faces found :(" title.
 * @param subtitleSize Explicit font size override for the retry hint text.
 * @param modifier Modifier applied to the outer Column.
 */
@Composable
fun NoFacesCard(
    isLandscape: Boolean = false,
    illustrationSize: Dp = if (isLandscape) 150.dp else 190.dp,
    titleSize: TextUnit = if (isLandscape) 20.sp else 26.sp,
    subtitleSize: TextUnit = if (isLandscape) 15.sp else 18.sp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.no_faces),
            contentDescription = "Sad blob - no faces found",
            modifier = Modifier.size(illustrationSize),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "no faces found :(",
            fontFamily = Baloo2,
            fontWeight = FontWeight.ExtraBold,
            fontSize = titleSize,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "try recording another clip",
            fontFamily = SFPro,
            fontWeight = FontWeight.Normal,
            fontSize = subtitleSize,
            color = Color(0xFF7A7A85)
        )
    }
}
