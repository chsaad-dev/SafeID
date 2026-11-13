package com.saaddev.safeid

import com.saaddev.safeid.engine.ExportAndCompressionEngine
import com.saaddev.safeid.engine.NormalizedCornerPoints
import com.saaddev.safeid.engine.NormalizedPoint
import com.saaddev.safeid.engine.PerspectiveAndFilterEngine
import com.saaddev.safeid.engine.PrivacyAuditReport
import com.saaddev.safeid.engine.SensitiveTag
import com.saaddev.safeid.engine.WatermarkAndBlurEngine
import com.saaddev.safeid.engine.WatermarkColorPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EngineTests {

    @Test
    fun privacyAuditReport_summaryChipsGeneration() {
        val reportWithGps = PrivacyAuditReport(
            hasExifData = true,
            gpsCoordinates = "31.5200° N, 74.3500° E",
            cameraMake = "Google",
            cameraModel = "Pixel 8",
            dateTimeTaken = "2026:10:15 14:30:00",
            software = "HDR+ v1.0",
            detectedTags = listOf(
                SensitiveTag("GPSLatitude", "GPS Coordinates", "31.5200° N, 74.3500° E"),
                SensitiveTag("Make", "Camera Make", "Google"),
                SensitiveTag("Model", "Camera Model", "Pixel 8")
            ),
            strippedTagCount = 3
        )

        assertFalse(reportWithGps.isClean)
        val chips = reportWithGps.toSummaryChipList()
        assertTrue(chips.any { it.contains("GPS Coordinates") })
        assertTrue(chips.any { it.contains("Pixel 8") })

        val cleanReport = PrivacyAuditReport(
            hasExifData = false,
            gpsCoordinates = null,
            cameraMake = null,
            cameraModel = null,
            dateTimeTaken = null,
            software = null,
            detectedTags = emptyList(),
            strippedTagCount = 0
        )
        assertTrue(cleanReport.isClean)
        assertEquals(listOf("Clean File • No GPS/EXIF Found"), cleanReport.toSummaryChipList())
    }

    @Test
    fun watermarkText_autoAppendsCurrentDate() {
        val baseText = "FOR SIM VERIFICATION ONLY"
        val formatted = WatermarkAndBlurEngine.formatWatermarkText(baseText, autoAppendDate = true)

        val todayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        assertTrue(formatted.startsWith(baseText))
        assertTrue(formatted.contains(todayDate))

        // Should not duplicate date if already formatted
        val reFormatted = WatermarkAndBlurEngine.formatWatermarkText(formatted, autoAppendDate = true)
        assertEquals(formatted, reFormatted)
    }

    @Test
    fun watermarkColors_presetValues() {
        assertEquals(0xFF64748B.toInt(), WatermarkColorPreset.GREY.colorInt)
        assertEquals(0xFFDC2626.toInt(), WatermarkColorPreset.RED.colorInt)
        assertEquals(0xFF2563EB.toInt(), WatermarkColorPreset.BLUE.colorInt)
    }

    @Test
    fun cornerPoints_defaultBounds() {
        val corners = NormalizedCornerPoints()
        assertEquals(0f, corners.topLeft.x, 0.001f)
        assertEquals(0f, corners.topLeft.y, 0.001f)
        assertEquals(1f, corners.topRight.x, 0.001f)
        assertEquals(0f, corners.topRight.y, 0.001f)
        assertEquals(1f, corners.bottomRight.x, 0.001f)
        assertEquals(1f, corners.bottomRight.y, 0.001f)
        assertEquals(0f, corners.bottomLeft.x, 0.001f)
        assertEquals(1f, corners.bottomLeft.y, 0.001f)
    }

    @Test
    fun exportAndCompressionEngine_a4Dimensions() {
        assertEquals(1240, ExportAndCompressionEngine.A4_WIDTH_PX)
        assertEquals(1754, ExportAndCompressionEngine.A4_HEIGHT_PX)
        assertEquals(680f, ExportAndCompressionEngine.ID_CARD_WIDTH_PX, 0.001f)
        assertEquals(260f, ExportAndCompressionEngine.FRONT_CARD_Y_PX, 0.001f)
        assertEquals(860f, ExportAndCompressionEngine.BACK_CARD_Y_PX, 0.001f)
    }

    @Test
    fun targetSizes_kbLimits() {
        assertEquals(null, com.saaddev.safeid.data.model.TargetSize.ORIGINAL.maxKb)
        assertEquals(200, com.saaddev.safeid.data.model.TargetSize.KB_200.maxKb)
        assertEquals(100, com.saaddev.safeid.data.model.TargetSize.KB_100.maxKb)
        assertEquals(50, com.saaddev.safeid.data.model.TargetSize.KB_50.maxKb)
    }

    @Test
    fun exportFormats_mimeTypes() {
        assertEquals("image/jpeg", com.saaddev.safeid.data.model.ExportFormat.JPG.mimeType)
        assertEquals("application/pdf", com.saaddev.safeid.data.model.ExportFormat.PDF.mimeType)
    }
}
