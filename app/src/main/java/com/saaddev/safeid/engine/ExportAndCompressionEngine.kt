package com.saaddev.safeid.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class CompressionResult(
    val data: ByteArray,
    val sizeBytes: Long,
    val targetKbLimit: Int?
)

object ExportAndCompressionEngine {

    const val A4_WIDTH_PX = 1240
    const val A4_HEIGHT_PX = 1754
    const val ID_CARD_WIDTH_PX = 680f
    const val FRONT_CARD_Y_PX = 260f
    const val BACK_CARD_Y_PX = 860f

    /**
     * Compresses [source] using iterative binary-search JPEG quality adjustment (95 down to 20)
     * and recursive downscaling if quality reduction alone cannot meet [targetKb].
     * Complies with PRD Section 4.3.
     */
    fun compressBitmapToTargetKb(
        source: Bitmap,
        targetKb: Int?
    ): CompressionResult {
        val stream = ByteArrayOutputStream()

        // If target is null or 0, return high quality (95)
        if (targetKb == null || targetKb <= 0) {
            source.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            val bytes = stream.toByteArray()
            return CompressionResult(bytes, bytes.size.toLong(), targetKb)
        }

        val maxBytes = targetKb * 1024
        var workingBitmap = source

        // Step 1: Binary search JPEG quality between 20 and 95
        var low = 20
        var high = 95
        var bestBytes: ByteArray? = null

        while (low <= high) {
            val mid = (low + high) / 2
            stream.reset()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, mid, stream)
            val candidate = stream.toByteArray()
            if (candidate.size <= maxBytes) {
                bestBytes = candidate
                low = mid + 1 // Try higher quality that still fits under limit
            } else {
                high = mid - 1
            }
        }

        if (bestBytes != null) {
            return CompressionResult(bestBytes, bestBytes.size.toLong(), targetKb)
        }

        // Step 2: If quality 20 still exceeds targetKb, downscale dimensions by 85% iteratively
        var current = workingBitmap
        val quality = 50
        while (true) {
            val newW = (current.width * 0.85f).toInt().coerceAtLeast(320)
            val newH = (current.height * 0.85f).toInt().coerceAtLeast(320)
            if (newW == current.width && newH == current.height) {
                break
            }
            val scaled = Bitmap.createScaledBitmap(current, newW, newH, true)
            if (scaled != current && current != source) {
                current.recycle()
            }
            current = scaled

            stream.reset()
            current.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val candidate = stream.toByteArray()
            if (candidate.size <= maxBytes || newW <= 320 || newH <= 320) {
                if (current != source) current.recycle()
                return CompressionResult(candidate, candidate.size.toLong(), targetKb)
            }
        }

        val finalBytes = stream.toByteArray()
        return CompressionResult(finalBytes, finalBytes.size.toLong(), targetKb)
    }

    /**
     * Generates a single-page A4 [PdfDocument] with [documentBitmap] scaled proportionally
     * and centered on an A4 page (1240 × 1754 px at 150 DPI).
     */
    fun createSinglePagePdf(documentBitmap: Bitmap): PdfDocument {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PX, A4_HEIGHT_PX, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Fill pure white background
        canvas.drawColor(Color.WHITE)

        val marginPx = 60f
        val maxAvailableW = A4_WIDTH_PX - (marginPx * 2f)
        val maxAvailableH = A4_HEIGHT_PX - (marginPx * 2f)

        val scale = minOf(
            maxAvailableW / documentBitmap.width.toFloat(),
            maxAvailableH / documentBitmap.height.toFloat()
        )

        val scaledW = documentBitmap.width * scale
        val scaledH = documentBitmap.height * scale
        val left = (A4_WIDTH_PX - scaledW) / 2f
        val top = (A4_HEIGHT_PX - scaledH) / 2f

        val dstRect = RectF(left, top, left + scaledW, top + scaledH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(documentBitmap, null, dstRect, paint)

        // Subtle 1px light-grey border frame
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(dstRect, borderPaint)

        pdfDocument.finishPage(page)
        return pdfDocument
    }

    /**
     * Generates a single-page A4 [PdfDocument] centering Front and Back ID cards at 300 DPI equivalent
     * proportions (1240 × 1754 px A4 canvas, card width 680 px at Y = 260 px and Y = 860 px).
     * Complies with PRD Section 4.4.
     */
    fun createFrontBackIdCardPdf(
        frontBitmap: Bitmap,
        backBitmap: Bitmap
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PX, A4_HEIGHT_PX, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // 1. Pure white background
        canvas.drawColor(Color.WHITE)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt() // Subtle 1px light-grey frame
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        // Center X for standard 680px card
        val cardX = (A4_WIDTH_PX - ID_CARD_WIDTH_PX) / 2f

        // 2. Render Front Side
        val frontAspectRatio = frontBitmap.height.toFloat() / frontBitmap.width.toFloat()
        val frontHeight = ID_CARD_WIDTH_PX * frontAspectRatio
        val frontRect = RectF(cardX, FRONT_CARD_Y_PX, cardX + ID_CARD_WIDTH_PX, FRONT_CARD_Y_PX + frontHeight)
        canvas.drawBitmap(frontBitmap, null, frontRect, bitmapPaint)
        canvas.drawRect(frontRect, borderPaint)

        // 3. Render Back Side
        val backAspectRatio = backBitmap.height.toFloat() / backBitmap.width.toFloat()
        val backHeight = ID_CARD_WIDTH_PX * backAspectRatio
        val backRect = RectF(cardX, BACK_CARD_Y_PX, cardX + ID_CARD_WIDTH_PX, BACK_CARD_Y_PX + backHeight)
        canvas.drawBitmap(backBitmap, null, backRect, bitmapPaint)
        canvas.drawRect(backRect, borderPaint)

        pdfDocument.finishPage(page)
        return pdfDocument
    }

    /**
     * Saves raw [bytes] to an export file inside `cacheDir/safeid_exports/`.
     */
    fun saveBytesToExportCache(
        context: Context,
        bytes: ByteArray,
        fileName: String
    ): File {
        val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)
        FileOutputStream(targetFile).use { it.write(bytes) }
        return targetFile
    }

    /**
     * Writes [pdfDocument] to an export file inside `cacheDir/safeid_exports/` and closes the document.
     */
    fun savePdfToExportCache(
        context: Context,
        pdfDocument: PdfDocument,
        fileName: String
    ): File {
        val exportDir = File(context.cacheDir, "safeid_exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)
        FileOutputStream(targetFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return targetFile
    }

    /**
     * Saves [sourceFile] into the public Downloads/SafeID/ directory.
     * Uses scoped MediaStore.Downloads on Android 10+ (API 29+) with zero storage permissions,
     * and direct public file access with media scanning fallback for Android 8-9.
     */
    fun saveToPublicDownloads(
        context: Context,
        sourceFile: File,
        mimeType: String
    ): Uri? {
        val fileName = sourceFile.name
        val resolver = context.contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SafeID")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val safeIdDir = File(downloadsDir, "SafeID").apply { mkdirs() }
            val destFile = File(safeIdDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(destFile)
        }
    }
}

