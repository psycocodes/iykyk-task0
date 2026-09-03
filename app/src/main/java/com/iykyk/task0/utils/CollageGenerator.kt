package com.iykyk.task0.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private const val TAG = "IYKYK_COLLAGE"

/**
 * Utility responsible for synthesizing, styling, and exporting the final face collage.
 *
 * Renders portrait bitmaps onto a high-resolution canvas with rounded corner clipping
 * and saves a compressed JPEG file to application storage.
 */
object CollageGenerator {

    /**
     * Synthesizes and exports a high-resolution collage JPEG containing the clustered portraits.
     *
     * @param context Application context used for accessing external storage.
     * @param bitmaps List of unique portrait bitmaps to include in the collage.
     * @return Generated File instance if successful, or null on error or empty input.
     */
    fun generateAndExportCollage(
        context: Context,
        bitmaps: List<Bitmap>
    ): File? {
        if (bitmaps.isEmpty()) {
            Log.w(TAG, "No bitmaps provided to render collage.")
            return null
        }

        try {
            val totalPersons = bitmaps.size
            val numCols = when {
                totalPersons == 1 -> 1
                totalPersons in 2..4 -> 2
                totalPersons in 5..9 -> 3
                else -> 4
            }
            val numRows = ceil(totalPersons / numCols.toDouble()).toInt().coerceAtLeast(1)

            val canvasWidth = 1080
            val outerMargin = 48f
            val cellGap = 24f

            val availableWidth = canvasWidth - (outerMargin * 2) - ((numCols - 1) * cellGap)
            val cellSize = availableWidth / numCols
            val gridHeight = (numRows * cellSize) + ((numRows - 1) * cellGap)
            val canvasHeight = (gridHeight + (outerMargin * 2)).toInt()

            val collageBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(collageBitmap)

            val bgPaint = Paint().apply {
                color = Color.parseColor("#0A0E14")
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), bgPaint)

            val outerR = 44f
            val innerR = 20f

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            for ((i, faceBmp) in bitmaps.withIndex()) {
                val row = i / numCols
                val col = i % numCols

                val left = outerMargin + col * (cellSize + cellGap)
                val top = outerMargin + row * (cellSize + cellGap)
                val right = left + cellSize
                val bottom = top + cellSize

                val cellRect = RectF(left, top, right, bottom)

                val isTopLeft = (row == 0 && col == 0)
                val isTopRight = (row == 0 && col == numCols - 1)
                val isBottomLeft = (row == numRows - 1 && col == 0)
                val isBottomRight = (row == numRows - 1 && (col == numCols - 1 || i == totalPersons - 1))

                val tlR = if (isTopLeft) outerR else innerR
                val trR = if (isTopRight) outerR else innerR
                val brR = if (isBottomRight) outerR else innerR
                val blR = if (isBottomLeft) outerR else innerR

                val radii = floatArrayOf(
                    tlR, tlR,
                    trR, trR,
                    brR, brR,
                    blR, blR
                )

                val cellPath = Path().apply {
                    addRoundRect(cellRect, radii, Path.Direction.CW)
                }

                canvas.save()
                canvas.clipPath(cellPath)

                val srcRect = Rect(0, 0, faceBmp.width, faceBmp.height)
                canvas.drawBitmap(faceBmp, srcRect, cellRect, null)
                canvas.restore()

                canvas.drawPath(cellPath, borderPaint)
            }

            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val iykykDir = File(picturesDir, "IYKYK")
            if (!iykykDir.exists()) iykykDir.mkdirs()

            val fileName = "COLLAGE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val outputFile = File(iykykDir, fileName)

            val outputStream = FileOutputStream(outputFile)
            collageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.flush()
            outputStream.close()

            Log.d(TAG, "✓ Successfully exported collage to: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating collage: ${e.message}", e)
            return null
        }
    }
}
