package com.saaddev.safeid.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedCornerPoints(
    val topLeft: NormalizedPoint = NormalizedPoint(0f, 0f),
    val topRight: NormalizedPoint = NormalizedPoint(1f, 0f),
    val bottomRight: NormalizedPoint = NormalizedPoint(1f, 1f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f)
)

enum class ScanFilter(val displayName: String) {
    ORIGINAL("Original"),
    CLEAN_SCAN("Clean Scan"),
    COLOR_BOOST("Color Boost")
}

object PerspectiveAndFilterEngine {

    /**
     * Warps the 4-corner perspective quadrilateral defined by [corners] in normalized coordinates (0f..1f)
     * into a flat, rectified rectangular Bitmap using [Matrix.setPolyToPoly].
     */
    fun warpPerspective(
        source: Bitmap,
        corners: NormalizedCornerPoints
    ): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        // Source 4 points in pixel coordinates
        val srcPoints = floatArrayOf(
            corners.topLeft.x * srcW, corners.topLeft.y * srcH,
            corners.topRight.x * srcW, corners.topRight.y * srcH,
            corners.bottomRight.x * srcW, corners.bottomRight.y * srcH,
            corners.bottomLeft.x * srcW, corners.bottomLeft.y * srcH
        )

        // Calculate target dimensions from edge lengths
        val widthTop = hypot(
            (corners.topRight.x - corners.topLeft.x) * srcW,
            (corners.topRight.y - corners.topLeft.y) * srcH
        )
        val widthBottom = hypot(
            (corners.bottomRight.x - corners.bottomLeft.x) * srcW,
            (corners.bottomRight.y - corners.bottomLeft.y) * srcH
        )
        val targetWidth = max(widthTop, widthBottom).roundToInt().coerceIn(100, 4000)

        val heightLeft = hypot(
            (corners.bottomLeft.x - corners.topLeft.x) * srcW,
            (corners.bottomLeft.y - corners.topLeft.y) * srcH
        )
        val heightRight = hypot(
            (corners.bottomRight.x - corners.topRight.x) * srcW,
            (corners.bottomRight.y - corners.topRight.y) * srcH
        )
        val targetHeight = max(heightLeft, heightRight).roundToInt().coerceIn(100, 4000)

        // Destination 4 corners of the output rectangle
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        if (!success) {
            // Fallback: simple copy if poly-to-poly fails
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val destination = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destination)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(source, matrix, paint)
        return destination
    }

    /**
     * Applies one of the 3 core ColorMatrix filters to the [source] bitmap:
     * - ORIGINAL: Unmodified bitmap copy
     * - CLEAN_SCAN: High-contrast grayscale document look with clean paper background
     * - COLOR_BOOST: Enhanced saturation, contrast, and brightness for IDs and badges
     */
    fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val colorMatrix = when (filter) {
            ScanFilter.ORIGINAL -> return source.copy(Bitmap.Config.ARGB_8888, true)
            ScanFilter.CLEAN_SCAN -> createCleanScanColorMatrix()
            ScanFilter.COLOR_BOOST -> createColorBoostColorMatrix()
        }

        val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        canvas.drawBitmap(source, 0f, 0f, paint)
        return resultBitmap
    }

    /**
     * Rotates [source] by the given [degrees] (e.g. 90f, -90f, 180f).
     */
    fun rotate(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source.copy(Bitmap.Config.ARGB_8888, true)
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * High-contrast grayscale document look: desaturates and applies a steep contrast curve
     * so text is sharp and dark, while paper noise is lifted to bright white.
     */
    fun createCleanScanColorMatrix(): ColorMatrix {
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }

        val contrast = 1.8f
        val brightness = 20f
        val translate = (-128f * contrast + 128f) + brightness

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayMatrix.postConcat(contrastMatrix)
        return grayMatrix
    }

    /**
     * Vivid color boost: increases color saturation and adds a gentle contrast boost.
     */
    fun createColorBoostColorMatrix(): ColorMatrix {
        val satMatrix = ColorMatrix().apply { setSaturation(1.35f) }

        val contrast = 1.25f
        val brightness = 10f
        val translate = (-128f * contrast + 128f) + brightness

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        satMatrix.postConcat(contrastMatrix)
        return satMatrix
    }
}
