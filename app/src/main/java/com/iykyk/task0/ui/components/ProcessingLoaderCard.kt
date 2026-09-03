package com.iykyk.task0.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.R
import com.iykyk.task0.ui.theme.Baloo2
import com.iykyk.task0.ui.theme.SFPro

/**
 * Animated processing state card featuring the stacked cyan blob character with smile.
 *
 * Internally handles both portrait and landscape arrangements via [isLandscape].
 *
 * @param blobScale Scale animation float driving the pulsing blob character.
 * @param subtitleText Real-time progress text (e.g. "4/12 faces embedded").
 * @param isLandscape Boolean flag toggling between portrait and landscape layouts.
 * @param blobSize Size of the pulsing loader character in portrait mode.
 * @param onCancelClick Optional callback invoked on cancel button click in landscape layout.
 * @param modifier Modifier applied to the outer container.
 */
@Composable
fun ProcessingLoaderCard(
    blobScale: Float,
    subtitleText: String,
    isLandscape: Boolean = false,
    blobSize: Dp = if (isLandscape) 150.dp else 190.dp,
    onCancelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        Row(
            modifier = modifier
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
                Box(
                    modifier = Modifier
                        .size(blobSize)
                        .scale(blobScale)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.loader_shape),
                        contentDescription = "Processing loader",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Image(
                        painter = painterResource(R.drawable.loader_smile),
                        contentDescription = "Smile",
                        modifier = Modifier.size(68.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "sit tight!",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 50.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(1.dp))

                Text(
                    text = "processsing...",
                    fontFamily = Baloo2,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFF7A7A85)
                )

                Text(
                    text = subtitleText,
                    fontFamily = SFPro,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    color = Color(0xFF7A7A85)
                )

                if (onCancelClick != null) {
                    Spacer(modifier = Modifier.height(24.dp))

                    PillButton(
                        text = "cancel",
                        iconResId = R.drawable.cross,
                        backgroundColor = Color(0xFF8C2E24),
                        onClick = onCancelClick,
                        height = 52.dp,
                        cornerRadius = 26.dp,
                        iconSize = 26.dp,
                        fontSize = 23.sp,
                        modifier = Modifier.width(265.dp)
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(blobSize)
                    .scale(blobScale),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.loader_shape),
                    contentDescription = "Processing loader",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                Image(
                    painter = painterResource(R.drawable.loader_smile),
                    contentDescription = "Smile",
                    modifier = Modifier.size(84.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "processsing...",
                fontFamily = Baloo2,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitleText,
                fontFamily = SFPro,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = Color(0xFF7A7A85)
            )
        }
    }
}
