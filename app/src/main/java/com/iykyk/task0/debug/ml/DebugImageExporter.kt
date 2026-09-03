package com.iykyk.task0.debug.ml

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

private const val TAG = "IYKYK_DEBUG"

object DebugImageExporter {

    suspend fun exportAllCutsMontage(
        context: Context,
        faces: List<DetectedFaceItem>
    ): File? = withContext(Dispatchers.IO) {
        if (faces.isEmpty()) return@withContext null

        val columns = 4
        val rows = ceil(faces.size.toDouble() / columns).toInt()

        val cardW = 280
        val cardH = 360
        val pad = 16
        val headerH = 100

        val totalW = columns * cardW + (columns + 1) * pad
        val totalH = headerH + rows * cardH + (rows + 1) * pad

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#0B1120"))

        // Paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 34f
            isFakeBoldText = true
        }

        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
        }

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val textBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
        }

        val textSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 14f
        }

        // Draw Header
        canvas.drawText("IYKYK ML - All Preprocessed Bounding Box Cuts", pad.toFloat(), 50f, titlePaint)
        val validCount = faces.count { it.isValid }
        val rejectedCount = faces.count { !it.isValid }
        canvas.drawText("Total Faces: ${faces.size}  |  Passed Quality: $validCount  |  Rejected: $rejectedCount", pad.toFloat(), 85f, subTitlePaint)

        // Draw Grid
        for ((idx, face) in faces.withIndex()) {
            val col = idx % columns
            val row = idx / columns

            val left = pad + col * (cardW + pad).toFloat()
            val top = headerH + pad + row * (cardH + pad).toFloat()
            val right = left + cardW
            val bottom = top + cardH

            // Card background
            cardBgPaint.color = if (face.isValid) Color.parseColor("#132A22") else Color.parseColor("#2A1515")
            canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, cardBgPaint)

            // Card border
            borderPaint.color = if (face.isValid) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
            canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, borderPaint)

            // Draw Face Crop (180x180)
            val imgLeft = left + (cardW - 180) / 2f
            val imgTop = top + 14f
            val destRect = Rect(imgLeft.toInt(), imgTop.toInt(), (imgLeft + 180).toInt(), (imgTop + 180).toInt())
            canvas.drawBitmap(face.faceCrop, null, destRect, null)

            // Draw Aligned 112x112 Thumbnail in corner if present
            face.alignedCrop112?.let { aligned ->
                val alDest = Rect((imgLeft + 120).toInt(), (imgTop + 120).toInt(), (imgLeft + 180).toInt(), (imgTop + 180).toInt())
                borderPaint.color = Color.parseColor("#38BDF8")
                borderPaint.strokeWidth = 2f
                canvas.drawRect(alDest, borderPaint)
                canvas.drawBitmap(aligned, null, alDest, null)
            }

            // Text Info
            val textLeft = left + 14f
            var textY = imgTop + 205f

            canvas.drawText("Face #${face.id} (F#${face.frameIndex}, ${face.timestampMs}ms)", textLeft, textY, textBoldPaint)
            textY += 22f

            val statusColor = if (face.isValid) Color.parseColor("#34D399") else Color.parseColor("#F87171")
            val statusText = if (face.isValid) "✓ PASSED QUALITY" else "✗ REJECTED"
            textBoldPaint.color = statusColor
            canvas.drawText(statusText, textLeft, textY, textBoldPaint)
            textBoldPaint.color = Color.WHITE
            textY += 20f

            if (!face.isValid && face.failureReason != null) {
                textSmallPaint.color = Color.parseColor("#FCA5A5")
                canvas.drawText(face.failureReason, textLeft, textY, textSmallPaint)
                textSmallPaint.color = Color.parseColor("#CBD5E1")
                textY += 20f
            }

            canvas.drawText("yaw: ${"%.1f".format(face.yaw)}°  pitch: ${"%.1f".format(face.pitch)}°  roll: ${"%.1f".format(face.roll)}°", textLeft, textY, textSmallPaint)
            textY += 18f
            canvas.drawText("blur: ${"%.1f".format(face.blurScore)}  sharpness: ${"%.0f".format(face.sharpnessScore)}", textLeft, textY, textSmallPaint)
        }

        val destFile = saveBitmapToDownloads(context, bitmap, "debug_preprocessed_cuts.png")
        bitmap.recycle()
        return@withContext destFile
    }

    suspend fun exportClusterRowsMontage(
        context: Context,
        clusters: List<DebugClusterItem>,
        threshold: Float
    ): File? = withContext(Dispatchers.IO) {
        if (clusters.isEmpty()) return@withContext null

        val maxMembersInRow = clusters.maxOf { it.memberFaces.size }.coerceAtLeast(1)
        val memberCardW = 160
        val rowH = 240
        val pad = 16
        val headerH = 100
        val clusterTitleW = 260

        val totalW = clusterTitleW + maxMembersInRow * (memberCardW + pad) + pad * 2
        val totalH = headerH + clusters.size * (rowH + pad) + pad

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#0B1120"))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 34f
            isFakeBoldText = true
        }

        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
        }

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val textBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            isFakeBoldText = true
        }

        val textSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 13f
        }

        // Header
        canvas.drawText("IYKYK ML - Identity Clusters Formed (Row-Wise)", pad.toFloat(), 50f, titlePaint)
        val totalClusterFaces = clusters.sumOf { it.memberFaces.size }
        canvas.drawText("Total Clusters: ${clusters.size}  |  Total Face Samples: $totalClusterFaces  |  Cosine Threshold: ${"%.2f".format(threshold)}", pad.toFloat(), 85f, subTitlePaint)

        // Draw each cluster as a row
        for ((cIdx, cluster) in clusters.withIndex()) {
            val rowTop = headerH + pad + cIdx * (rowH + pad).toFloat()
            val rowBottom = rowTop + rowH

            // Draw full row card
            canvas.drawRoundRect(RectF(pad.toFloat(), rowTop, totalW - pad.toFloat(), rowBottom), 12f, 12f, rowBgPaint)

            // Cluster Title Info (left column)
            var textY = rowTop + 35f
            textBoldPaint.color = Color.parseColor("#38BDF8")
            textBoldPaint.textSize = 20f
            canvas.drawText("Cluster #${cluster.clusterId}", pad + 20f, textY, textBoldPaint)

            textY += 24f
            textSmallPaint.color = Color.parseColor("#94A3B8")
            canvas.drawText("${cluster.memberFaces.size} assigned face(s)", pad + 20f, textY, textSmallPaint)

            cluster.representativeFace?.let { rep ->
                textY += 24f
                textBoldPaint.color = Color.parseColor("#34D399")
                textBoldPaint.textSize = 15f
                canvas.drawText("★ Best Portrait:", pad + 20f, textY, textBoldPaint)
                textY += 20f
                textSmallPaint.color = Color.WHITE
                canvas.drawText("Face #${rep.id} (sharpness: ${"%.0f".format(rep.sharpnessScore)})", pad + 20f, textY, textSmallPaint)
            }

            textBoldPaint.textSize = 17f
            textBoldPaint.color = Color.WHITE

            // Draw Member Faces horizontally
            var currentX = pad + clusterTitleW.toFloat()
            for (member in cluster.memberFaces) {
                val isWinner = member.id == cluster.representativeFace?.id
                val sim = member.embedding?.let {
                    DebugTFLiteModel.cosineSimilarity(it, cluster.centroid)
                } ?: 0f

                val cardLeft = currentX
                val cardTop = rowTop + 16f
                val cardRight = cardLeft + memberCardW
                val cardBottom = cardTop + (rowH - 32f)

                // Member card background
                cardBgPaint.color = if (isWinner) Color.parseColor("#064E3B") else Color.parseColor("#0F172A")
                canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 8f, 8f, cardBgPaint)

                // Member border
                borderPaint.color = if (isWinner) Color.parseColor("#10B981") else Color.parseColor("#334155")
                borderPaint.strokeWidth = if (isWinner) 3f else 1.5f
                canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 8f, 8f, borderPaint)

                // Draw face thumbnail (120x120)
                val imgLeft = cardLeft + (memberCardW - 120) / 2f
                val imgTop = cardTop + 10f
                val destRect = Rect(imgLeft.toInt(), imgTop.toInt(), (imgLeft + 120).toInt(), (imgTop + 120).toInt())
                canvas.drawBitmap(member.faceCrop, null, destRect, null)

                // Member labels
                val mTextLeft = cardLeft + 10f
                var mY = imgTop + 140f

                canvas.drawText("Face #${member.id}", mTextLeft, mY, textBoldPaint)
                mY += 18f
                textSmallPaint.color = Color.parseColor("#CBD5E1")
                canvas.drawText("F#${member.frameIndex} (${member.timestampMs}ms)", mTextLeft, mY, textSmallPaint)

                mY += 18f
                val simColor = if (sim >= threshold) Color.parseColor("#34D399") else Color.parseColor("#F87171")
                textSmallPaint.color = simColor
                textSmallPaint.isFakeBoldText = true
                val winnerLabel = if (isWinner) " [WINNER]" else ""
                canvas.drawText("sim: ${"%.3f".format(sim)}$winnerLabel", mTextLeft, mY, textSmallPaint)
                textSmallPaint.isFakeBoldText = false

                currentX += memberCardW + pad
            }
        }

        val destFile = saveBitmapToDownloads(context, bitmap, "debug_clusters_rowwise.png")
        bitmap.recycle()
        return@withContext destFile
    }

    private fun saveBitmapToDownloads(context: Context, bitmap: Bitmap, fileName: String): File {
        // 1. Save to app-specific external files dir (guaranteed permission on all Android versions)
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val localFile = File(appDownloadsDir, fileName)
        try {
            FileOutputStream(localFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing local debug image: ${e.message}", e)
        }

        // 2. Also write to public Downloads/IYKYK via MediaStore on Android 10+ (API 29+) or MediaScanner on legacy
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IYKYK")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                    Log.i(TAG, "[Export] Wrote to MediaStore: $uri")
                }
            } else {
                val pubDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                pubDir.mkdirs()
                val pubFile = File(pubDir, fileName)
                FileOutputStream(pubFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(pubFile)
                context.sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing to public MediaStore: ${e.message}", e)
        }

        Log.i(TAG, "[Export] Wrote debug image to: ${localFile.absolutePath} (${localFile.length() / 1024} KB)")
        return localFile
    }
}
