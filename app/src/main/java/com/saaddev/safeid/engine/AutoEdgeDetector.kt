package com.saaddev.safeid.engine

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin on-device document edge detector.
 * Downscales the image, computes 3x3 Sobel edge magnitudes, and locates the
 * dominant document quadrilateral so crop handles snap automatically.
 */
object AutoEdgeDetector {

    private const val TARGET_MAX_DIM = 240

    fun detectDocumentCorners(source: Bitmap): NormalizedCornerPoints {
        val srcW = source.width
        val srcH = source.height
        if (srcW < 32 || srcH < 32) return NormalizedCornerPoints()

        val scale = TARGET_MAX_DIM.toFloat() / max(srcW, srcH).toFloat()
        val w = (srcW * scale).toInt().coerceAtLeast(32)
        val h = (srcH * scale).toInt().coerceAtLeast(32)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != source) scaled.recycle()

        // 1. Grayscale luminance array
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        // 2. Sobel edge magnitude
        val edge = IntArray(w * h)
        var maxEdge = 1
        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val gx = -gray[rowOffset - w + x - 1] + gray[rowOffset - w + x + 1] -
                    2 * gray[rowOffset + x - 1] + 2 * gray[rowOffset + x + 1] -
                    gray[rowOffset + w + x - 1] + gray[rowOffset + w + x + 1]

                val gy = -gray[rowOffset - w + x - 1] - 2 * gray[rowOffset - w + x] - gray[rowOffset - w + x + 1] +
                    gray[rowOffset + w + x - 1] + 2 * gray[rowOffset + w + x] + gray[rowOffset + w + x + 1]

                val mag = abs(gx) + abs(gy)
                edge[rowOffset + x] = mag
                if (mag > maxEdge) maxEdge = mag
            }
        }

        // 3. Adaptive threshold for strong edges
        val threshold = (maxEdge * 0.22f).toInt().coerceAtLeast(45)

        // 4. Locate extreme strong-edge pixels in each quadrant to form a clean quad
        var tlBestDist = Float.MAX_VALUE
        var trBestDist = Float.MAX_VALUE
        var brBestDist = Float.MAX_VALUE
        var blBestDist = Float.MAX_VALUE

        var tlPoint = NormalizedPoint(0f, 0f)
        var trPoint = NormalizedPoint(1f, 0f)
        var brPoint = NormalizedPoint(1f, 1f)
        var blPoint = NormalizedPoint(0f, 1f)

        val marginX = (w * 0.02f).toInt().coerceAtLeast(2)
        val marginY = (h * 0.02f).toInt().coerceAtLeast(2)
        val halfW = w / 2
        val halfH = h / 2

        var strongEdgeCount = 0

        for (y in marginY until h - marginY) {
            val rowOffset = y * w
            for (x in marginX until w - marginX) {
                if (edge[rowOffset + x] < threshold) continue
                strongEdgeCount++

                val nx = x.toFloat() / w.toFloat()
                val ny = y.toFloat() / h.toFloat()

                if (x < halfW && y < halfH) {
                    val d = nx + ny
                    if (d < tlBestDist) {
                        tlBestDist = d
                        tlPoint = NormalizedPoint(nx, ny)
                    }
                } else if (x >= halfW && y < halfH) {
                    val d = (1f - nx) + ny
                    if (d < trBestDist) {
                        trBestDist = d
                        trPoint = NormalizedPoint(nx, ny)
                    }
                } else if (x >= halfW && y >= halfH) {
                    val d = (1f - nx) + (1f - ny)
                    if (d < brBestDist) {
                        brBestDist = d
                        brPoint = NormalizedPoint(nx, ny)
                    }
                } else {
                    val d = nx + (1f - ny)
                    if (d < blBestDist) {
                        blBestDist = d
                        blPoint = NormalizedPoint(nx, ny)
                    }
                }
            }
        }

        if (strongEdgeCount < 20 ||
            tlBestDist == Float.MAX_VALUE ||
            trBestDist == Float.MAX_VALUE ||
            brBestDist == Float.MAX_VALUE ||
            blBestDist == Float.MAX_VALUE
        ) {
            return NormalizedCornerPoints()
        }

        // Validate area (must cover at least 18% of image and not collapse)
        val area = polygonArea(tlPoint, trPoint, brPoint, blPoint)
        if (area < 0.18f || area > 0.98f) {
            return NormalizedCornerPoints()
        }

        // Validate proper ordering
        if (tlPoint.x >= trPoint.x - 0.15f ||
            blPoint.x >= brPoint.x - 0.15f ||
            tlPoint.y >= blPoint.y - 0.15f ||
            trPoint.y >= brPoint.y - 0.15f
        ) {
            return NormalizedCornerPoints()
        }

        return NormalizedCornerPoints(
            topLeft = clampPoint(tlPoint),
            topRight = clampPoint(trPoint),
            bottomRight = clampPoint(brPoint),
            bottomLeft = clampPoint(blPoint)
        )
    }

    private fun clampPoint(p: NormalizedPoint): NormalizedPoint {
        return NormalizedPoint(
            x = min(0.98f, max(0.02f, p.x)),
            y = min(0.98f, max(0.02f, p.y))
        )
    }

    private fun polygonArea(
        p1: NormalizedPoint,
        p2: NormalizedPoint,
        p3: NormalizedPoint,
        p4: NormalizedPoint
    ): Float {
        val sum1 = p1.x * p2.y + p2.x * p3.y + p3.x * p4.y + p4.x * p1.y
        val sum2 = p1.y * p2.x + p2.y * p3.x + p3.y * p4.x + p4.y * p1.x
        return abs(sum1 - sum2) * 0.5f
    }
}
