package com.iykyk.task0.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Modular Dynamic Face Collage Grid:
 * - Displays individual square portrait face crops in an adaptive grid.
 * - Outer corners use outerCornerRadius (22dp); inner corners use innerCornerRadius (10dp).
 * - Zero bounding box border or outer padding wrapper.
 *
 * @param faces List of representative face portrait bitmaps to display.
 * @param numCols Number of columns dynamically calculated for the grid.
 * @param modifier Modifier applied to the LazyVerticalGrid.
 * @param isScrollable Whether the grid enables vertical scrolling when face count exceeds capacity.
 * @param outerCornerRadius Radius for outer boundary corners of the grid.
 * @param innerCornerRadius Radius for internal cell corners.
 * @param spacing Spacing between adjacent portrait cells.
 */
@Composable
fun FaceCollageGrid(
    faces: List<Bitmap>,
    numCols: Int,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = false,
    outerCornerRadius: Dp = 22.dp,
    innerCornerRadius: Dp = 10.dp,
    spacing: Dp = 12.dp
) {
    val totalCount = faces.size
    val numRows = ceil(totalCount / numCols.toDouble()).toInt().coerceAtLeast(1)

    LazyVerticalGrid(
        columns = GridCells.Fixed(numCols),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(0.dp),
        userScrollEnabled = isScrollable,
        modifier = modifier
    ) {
        itemsIndexed(faces) { index, faceBitmap ->
            val row = index / numCols
            val col = index % numCols

            val isTopLeft = (row == 0 && col == 0)
            val isTopRight = (row == 0 && col == numCols - 1)
            val isBottomLeft = (row == numRows - 1 && col == 0)
            val isBottomRight = (row == numRows - 1 && (col == numCols - 1 || index == totalCount - 1))

            val cellShape = RoundedCornerShape(
                topStart = if (isTopLeft) outerCornerRadius else innerCornerRadius,
                topEnd = if (isTopRight) outerCornerRadius else innerCornerRadius,
                bottomEnd = if (isBottomRight) outerCornerRadius else innerCornerRadius,
                bottomStart = if (isBottomLeft) outerCornerRadius else innerCornerRadius
            )

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(cellShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF2E3D4F), cellShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = faceBitmap.asImageBitmap(),
                    contentDescription = "Face $index",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
