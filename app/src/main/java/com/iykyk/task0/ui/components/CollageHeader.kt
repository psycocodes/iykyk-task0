package com.iykyk.task0.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.task0.ml.config.DEFAULT_SIMILARITY_THRESHOLD
import com.iykyk.task0.ui.theme.Baloo2
import com.iykyk.task0.ui.theme.SFPro

/**
 * Universal header component for Collage, NoFaces, and Processing screens.
 *
 * @param totalCount Detected face count (determines "X faces!" title if custom title is null).
 * @param title Optional custom title text overriding default face count formatting.
 * @param isLandscape Orientation flag adjusting typography scale and alignment.
 * @param topPadding Configurable top margin for edge-to-edge status bar offset.
 * @param modifier Modifier applied to the outer header Column.
 */
@Composable
fun CollageHeader(
    totalCount: Int = 0,
    title: String? = null,
    isLandscape: Boolean = false,
    topPadding: Dp = if (isLandscape) 12.dp else 40.dp,
    modifier: Modifier = Modifier
) {
    val headerTitle = title ?: when (totalCount) {
        0 -> "0 faces!"
        1 -> "1 face!"
        else -> "$totalCount faces!"
    }

    val titleSize = when {
        isLandscape -> 55.sp
        totalCount == 0 || title != null -> 50.sp
        else -> 46.sp
    }

    val subtitleSize = if (isLandscape) 16.sp else 16.sp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Text(
            text = headerTitle,
            fontFamily = Baloo2,
            fontWeight = FontWeight.ExtraBold,
            fontSize = titleSize,
            lineHeight = (titleSize.value * 0.88f).sp,
            color = Color.White,
            textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center
        )

        if (title == null) {
            Text(
                text = "cosine similiarity: ${"%.2f".format(DEFAULT_SIMILARITY_THRESHOLD)}",
                fontFamily = SFPro,
                fontWeight = FontWeight.Normal,
                fontSize = subtitleSize,
                color = Color(0xFF7A7A85),
                textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.offset(y = if (isLandscape) (-8).dp else (-6).dp)
            )
        }
    }
}
