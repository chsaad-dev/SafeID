package com.saaddev.safeid.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.saaddev.safeid.data.model.GridCopies
import com.saaddev.safeid.data.model.PaperSheetSize
import com.saaddev.safeid.data.model.PassportStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class PassportGridResult(
    val file: File,
    val previewBitmap: Bitmap,
    val fileSizeBytes: Long
)

object PassportGridEngine {

    private const val DPI = 300f
    private const val MM_TO_INCH = 1f / 25.4f

    /**
     * Converts millimeters to pixels at 300 DPI.
     */
    fun mmToPx(mm: Float): Float = mm * MM_TO_INCH * DPI

    /**
     * Crops and scales the source portrait bitmap based on the target aspect ratio,
     * zoom factor (1.0f to 2.0f), and pan offset (-1.0f to 1.0f normalized).
     */
    fun cropAndFramePortrait(
        source: Bitmap,
        standard: PassportStandard,
        zoom: Float = 1.0f,
        panXNormalized: Float = 0f,
        panYNormalized: Float = 0f
    ): Bitmap {
        val targetAspect = standard.widthMm / standard.heightMm
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val srcAspect = srcW / srcH

        val baseCropW: Float
        val baseCropH: Float
        if (srcAspect > targetAspect) {
            baseCropH = srcH
            baseCropW = srcH * targetAspect
        } else {
            baseCropW = srcW
            baseCropH = srcW / targetAspect
        }

        val clampedZoom = zoom.coerceIn(1.0f, 2.0f)
        val finalCropW = baseCropW / clampedZoom
        val finalCropH = baseCropH / clampedZoom

        val maxOffsetX = (srcW - finalCropW) / 2f
        val maxOffsetY = (srcH - finalCropH) / 2f

        val centerX = srcW / 2f + (panXNormalized * maxOffsetX).coerceIn(-maxOffsetX, maxOffsetX)
        val centerY = srcH / 2f + (panYNormalized * maxOffsetY).coerceIn(-maxOffsetY, maxOffsetY)

        val cropLeft = (centerX - finalCropW / 2f).coerceIn(0f, srcW - finalCropW).toInt()
        val cropTop = (centerY - finalCropH / 2f).coerceIn(0f, srcH - finalCropH).toInt()
        val cropW = finalCropW.toInt().coerceAtMost(source.width - cropLeft)
        val cropH = finalCropH.toInt().coerceAtMost(source.height - cropTop)

        return Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH)
    }

    /**
     * Renders a live preview bitmap at reduced scale (~600px width) for real-time responsiveness.
     */
    fun generatePreviewSheet(
        croppedPortrait: Bitmap,
        standard: PassportStandard,
        paperSize: PaperSheetSize,
        copies: GridCopies,
        showCutGuides: Boolean
    ): Bitmap {
        val previewScale = 600f / paperSize.widthPx
        val previewW = 600
        val previewH = (paperSize.heightPx * previewScale).toInt()

        val previewBitmap = Bitmap.createBitmap(previewW, previewH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(previewBitmap)
        canvas.drawColor(Color.WHITE)

        renderGridOnCanvas(
            canvas = canvas,
            sheetW = previewW.toFloat(),
            sheetH = previewH.toFloat(),
            croppedPortrait = croppedPortrait,
            standard = standard,
            paperSize = paperSize,
            copies = copies,
            showCutGuides = showCutGuides,
            scaleMultiplier = previewScale
        )

        return previewBitmap
    }

    /**
     * Generates the full 300 DPI printable sheet and saves it to cache as JPG or PDF.
     */
    suspend fun generatePrintSheet(
        context: Context,
        croppedPortrait: Bitmap,
        standard: PassportStandard,
        paperSize: PaperSheetSize,
        copies: GridCopies,
        showCutGuides: Boolean,
        exportAsPdf: Boolean,
        baseFileName: String? = null
    ): PassportGridResult = withContext(Dispatchers.IO) {
        val sheetW = paperSize.widthPx
        val sheetH = paperSize.heightPx

        val fullSheetBitmap = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullSheetBitmap)
        canvas.drawColor(Color.WHITE)

        renderGridOnCanvas(
            canvas = canvas,
            sheetW = sheetW.toFloat(),
            sheetH = sheetH.toFloat(),
            croppedPortrait = croppedPortrait,
            standard = standard,
            paperSize = paperSize,
            copies = copies,
            showCutGuides = showCutGuides,
            scaleMultiplier = 1.0f
        )

        val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
        val savedFile: File
        if (exportAsPdf) {
            val fileName = if (baseFileName != null) "$baseFileName.pdf" else "SafeID_PassportGrid_${System.currentTimeMillis()}.pdf"
            savedFile = File(exportDir, fileName)
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(sheetW, sheetH, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(fullSheetBitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)
            FileOutputStream(savedFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
        } else {
            val fileName = if (baseFileName != null) "$baseFileName.jpg" else "SafeID_PassportGrid_${System.currentTimeMillis()}.jpg"
            savedFile = File(exportDir, fileName)
            FileOutputStream(savedFile).use { out ->
                fullSheetBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }

        PassportGridResult(
            file = savedFile,
            previewBitmap = fullSheetBitmap,
            fileSizeBytes = savedFile.length()
        )
    }

    private fun renderGridOnCanvas(
        canvas: Canvas,
        sheetW: Float,
        sheetH: Float,
        croppedPortrait: Bitmap,
        standard: PassportStandard,
        paperSize: PaperSheetSize,
        copies: GridCopies,
        showCutGuides: Boolean,
        scaleMultiplier: Float
    ) {
        val isLandscape = sheetW > sheetH
        val cols: Int
        val rows: Int

        when (copies) {
            GridCopies.FOUR -> {
                cols = 2
                rows = 2
            }
            GridCopies.SIX -> {
                if (isLandscape) {
                    cols = 3
                    rows = 2
                } else {
                    cols = 2
                    rows = 3
                }
            }
            GridCopies.EIGHT -> {
                if (isLandscape) {
                    cols = 4
                    rows = 2
                } else {
                    cols = 2
                    rows = 4
                }
            }
        }

        // True physical dimensions at 300 DPI
        var photoW = mmToPx(standard.widthMm) * scaleMultiplier
        var photoH = mmToPx(standard.heightMm) * scaleMultiplier

        // Margin constraints
        val minMargin = 20f * scaleMultiplier
        val availableW = sheetW - (minMargin * 2f)
        val availableH = sheetH - (minMargin * 2f)

        // Fit scale if photos exceed sheet dimensions
        val maxGridW = cols * photoW
        val maxGridH = rows * photoH
        if (maxGridW > availableW || maxGridH > availableH) {
            val fitScale = min(availableW / maxGridW, availableH / maxGridH)
            photoW *= fitScale
            photoH *= fitScale
        }

        val totalGridW = cols * photoW
        val totalGridH = rows * photoH

        val gapX = if (cols > 1) (sheetW - totalGridW) / (cols + 1) else (sheetW - totalGridW) / 2f
        val gapY = if (rows > 1) (sheetH - totalGridH) / (rows + 1) else (sheetH - totalGridH) / 2f

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD1D5DB.toInt() // Light Grey cut lines
            style = Paint.Style.STROKE
            strokeWidth = max(1f, 1f * scaleMultiplier)
        }

        var photoIndex = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (photoIndex >= copies.count) break

                val left = gapX + c * (photoW + gapX)
                val top = gapY + r * (photoH + gapY)
                val right = left + photoW
                val bottom = top + photoH
                val dstRect = RectF(left, top, right, bottom)

                canvas.drawBitmap(croppedPortrait, null, dstRect, bitmapPaint)

                if (showCutGuides) {
                    canvas.drawRect(dstRect, guidePaint)
                }

                photoIndex++
            }
        }
    }
}
