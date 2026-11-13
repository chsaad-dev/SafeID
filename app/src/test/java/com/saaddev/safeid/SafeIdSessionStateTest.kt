package com.saaddev.safeid

import com.saaddev.safeid.data.model.ExifAuditSummary
import com.saaddev.safeid.data.model.ExportConfig
import com.saaddev.safeid.data.model.ExportFormat
import com.saaddev.safeid.data.model.TargetSize
import com.saaddev.safeid.data.model.WatermarkDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeIdSessionStateTest {

    @Test
    fun exifAuditSummary_cleanStateVerification() {
        val cleanSummary = ExifAuditSummary(hasExifData = false)
        assertTrue(cleanSummary.isClean)

        val dirtySummary = ExifAuditSummary(
            hasExifData = true,
            gpsCoordinates = "31.52 N, 74.35 E",
            deviceModel = "Pixel 8",
            detectedTags = listOf("GPSLatitude", "Model")
        )
        assertFalse(dirtySummary.isClean)
        assertEquals(2, dirtySummary.detectedTags.size)
    }

    @Test
    fun exportConfig_defaultsMatchPRDSpecification() {
        val config = ExportConfig()
        assertEquals(ExportFormat.JPG, config.format)
        assertEquals(TargetSize.ORIGINAL, config.targetSize)
        assertEquals(0.25f, config.watermarkOpacity, 0.001f)
        assertEquals(WatermarkDensity.NORMAL, config.watermarkDensity)
        assertTrue(config.autoDeleteCacheOnExit)
    }
}

