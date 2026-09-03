package com.iykyk.task0.debug.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.ceil

object DebugCollageMaker {

    fun generateCollage(representatives: List<Bitmap>, targetDimension: Int = 1080): Bitmap? {
        val totalCount = representatives.size
        if (totalCount == 0) return null

        val numCols = when {
            totalCount <= 1 -> 1
            totalCount in 2..4 -> 2
            totalCount in 5..9 -> 3
            else -> 4
        }
        val numRows = ceil(totalCount / numCols.toDouble()).toInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(targetDimension, targetDimension, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val outerRadius = targetDimension * 0.05f
        val innerRadius = targetDimension * 0.025f
        val spacing = targetDimension * 0.025f

        val totalSpacingX = spacing * (numCols - 1)
        val totalSpacingY = spacing * (numRows - 1)
        val margin = targetDimension * 0.04f

        val availableW = targetDimension - (margin * 2) - totalSpacingX
        val availableH = targetDimension - (margin * 2) - totalSpacingY
        val cellSize = minOf(availableW / numCols, availableH / numRows)

        val startX = margin + (availableW - (cellSize * numCols)) / 2f
        val startY = margin + (availableH - (cellSize * numRows)) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for ((index, face) in representatives.withIndex()) {
            val r = index / numCols
            val c = index % numCols

            val left = startX + c * (cellSize + spacing)
            val top = startY + r * (cellSize + spacing)
            val right = left + cellSize
            val bottom = top + cellSize
            val cellRect = RectF(left, top, right, bottom)

            val isTopLeft = (r == 0 && c == 0)
            val isTopRight = (r == 0 && c == numCols - 1)
            val isBottomLeft = (r == numRows - 1 && c == 0)
            val isBottomRight = (r == numRows - 1 && (c == numCols - 1 || index == totalCount - 1))

            val radii = floatArrayOf(
                if (isTopLeft) outerRadius else innerRadius, if (isTopLeft) outerRadius else innerRadius,
                if (isTopRight) outerRadius else innerRadius, if (isTopRight) outerRadius else innerRadius,
                if (isBottomRight) outerRadius else innerRadius, if (isBottomRight) outerRadius else innerRadius,
                if (isBottomLeft) outerRadius else innerRadius, if (isBottomLeft) outerRadius else innerRadius
            )

            val path = Path().apply {
                addRoundRect(cellRect, radii, Path.Direction.CW)
            }

            canvas.save()
            canvas.clipPath(path)

            val srcRect = Rect(0, 0, face.width, face.height)
            canvas.drawBitmap(face, srcRect, cellRect, paint)
            canvas.restore()
        }

        return output
    }
}
