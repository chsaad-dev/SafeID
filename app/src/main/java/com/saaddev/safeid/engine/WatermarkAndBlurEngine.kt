package com.saaddev.safeid.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

data class BlurStroke(
    val points: List<NormalizedPoint>,
    val normalizedRadius: Float = 0.045f // Fraction of min(width, height)
)

enum class WatermarkColorPreset(val displayName: String, val colorInt: Int) {
    GREY("Slate Grey", 0xFF64748B.toInt()),
    RED("Alert Red", 0xFFDC2626.toInt()),
    BLUE("Royal Blue", 0xFF2563EB.toInt())
}

object WatermarkAndBlurEngine {

    /**
     * Formats the watermark text, auto-appending today's date ("dd MMM yyyy") if not already present.
     */
    fun formatWatermarkText(baseText: String, autoAppendDate: Boolean = true): String {
        val trimmed = baseText.trim()
        if (!autoAppendDate) return trimmed
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        return if (trimmed.isEmpty()) {
            dateStr
        } else if (trimmed.contains(dateStr, ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed • $dateStr"
        }
    }

    /**
     * Draws repeated diagonal tiled watermark text across [canvas] at -30° rotation.
     * Complies with PRD Section 4.2.
     */
    fun drawDiagonalWatermark(
        canvas: Canvas,
        text: String,
        colorInt: Int = WatermarkColorPreset.GREY.colorInt,
        opacityFloat: Float = 0.25f,
        spacingMultiplier: Float = 1.0f
    ) {
        if (text.isBlank()) return

        val alphaInt = (opacityFloat.coerceIn(0.10f, 0.60f) * 255f).roundToInt().coerceIn(15, 180)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            alpha = alphaInt
            textSize = (canvas.width * 0.032f).coerceAtLeast(24f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textWidth = paint.measureText(text)
        val stepX = (textWidth + (canvas.width * 0.14f)) * spacingMultiplier
        val stepY = paint.textSize * 4.2f * spacingMultiplier
        val diagonal = hypot(canvas.width.toFloat(), canvas.height.toFloat())

        canvas.save()
        canvas.rotate(-30f, canvas.width / 2f, canvas.height / 2f)

        var y = -diagonal
        var rowIndex = 0
        while (y < diagonal * 2) {
            var x = -diagonal + (if (rowIndex % 2 == 1) stepX * 0.5f else 0f)
            while (x < diagonal * 2) {
                canvas.drawText(text, x, y, paint)
                x += stepX
            }
            y += stepY
            rowIndex++
        }
        canvas.restore()
    }

    /**
     * Applies localized pixelation mask strokes along [blurStrokes] (normalized 0f..1f coordinates).
     * Uses in-place [BitmapShader] so redacted strokes reveal pixelated clusters without seam artifacts.
     */
    fun applyBlurStrokes(
        source: Bitmap,
        blurStrokes: List<BlurStroke>,
        pixelationBlockSize: Int = 16
    ): Bitmap {
        if (blurStrokes.isEmpty()) return source.copy(Bitmap.Config.ARGB_8888, true)

        val width = source.width
        val height = source.height
        val minDim = min(width, height).toFloat()

        // 1. Generate downscaled and upscaled pixelated texture
        val smallW = (width / pixelationBlockSize).coerceAtLeast(4)
        val smallH = (height / pixelationBlockSize).coerceAtLeast(4)
        val smallBitmap = Bitmap.createScaledBitmap(source, smallW, smallH, false)
        val pixelatedBitmap = Bitmap.createScaledBitmap(smallBitmap, width, height, false)
        if (smallBitmap != pixelatedBitmap) smallBitmap.recycle()

        // 2. Prepare canvas on mutable source copy
        val resultBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 3. Paint with BitmapShader from the pixelated texture
        val shader = BitmapShader(pixelatedBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            style = Paint.Style.FILL
        }

        for (stroke in blurStrokes) {
            if (stroke.points.isEmpty()) continue
            val strokeWidthPx = (stroke.normalizedRadius * minDim).coerceAtLeast(8f)
            strokePaint.strokeWidth = strokeWidthPx

            if (stroke.points.size == 1) {
                val pt = stroke.points[0]
                canvas.drawCircle(pt.x * width, pt.y * height, strokeWidthPx / 2f, circlePaint)
            } else {
                val path = Path()
                val first = stroke.points[0]
                path.moveTo(first.x * width, first.y * height)
                for (i in 1 until stroke.points.size) {
                    val pt = stroke.points[i]
                    path.lineTo(pt.x * width, pt.y * height)
                }
                canvas.drawPath(path, strokePaint)
            }
        }

        pixelatedBitmap.recycle()
        return resultBitmap
    }

    /**
     * Bakes both the blur strokes and the tiled diagonal watermark directly into a fresh Bitmap.
     */
    fun applyWatermarkAndBlur(
        source: Bitmap,
        watermarkText: String,
        watermarkColorInt: Int = WatermarkColorPreset.GREY.colorInt,
        watermarkOpacity: Float = 0.25f,
        autoAppendDate: Boolean = true,
        spacingMultiplier: Float = 1.0f,
        blurStrokes: List<BlurStroke> = emptyList()
    ): Bitmap {
        // Step 1: Apply blur strokes first so watermarks sit cleanly on top
        val blurredBitmap = applyBlurStrokes(source, blurStrokes)

        // Step 2: Draw permanent tiled diagonal watermark
        val finalBitmap = blurredBitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (blurredBitmap != source && blurredBitmap != finalBitmap) blurredBitmap.recycle()

        val canvas = Canvas(finalBitmap)
        val fullText = formatWatermarkText(watermarkText, autoAppendDate)
        drawDiagonalWatermark(canvas, fullText, watermarkColorInt, watermarkOpacity, spacingMultiplier)

        return finalBitmap
    }
}
