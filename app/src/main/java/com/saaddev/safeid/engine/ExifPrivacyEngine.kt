package com.saaddev.safeid.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.max

data class SensitiveTag(
    val tagKey: String,
    val displayName: String,
    val value: String
)

data class PrivacyAuditReport(
    val hasExifData: Boolean,
    val gpsCoordinates: String?,
    val cameraMake: String?,
    val cameraModel: String?,
    val dateTimeTaken: String?,
    val software: String?,
    val detectedTags: List<SensitiveTag>,
    val strippedTagCount: Int
) {
    val isClean: Boolean get() = !hasExifData || strippedTagCount == 0

    fun toSummaryChipList(): List<String> {
        if (isClean) {
            return listOf("Clean File • No GPS/EXIF Found")
        }
        val chips = mutableListOf<String>()
        gpsCoordinates?.let { chips.add("GPS Coordinates ($it)") }
        val device = listOfNotNull(cameraMake, cameraModel).joinToString(" ").trim()
        if (device.isNotEmpty()) chips.add("Device: $device")
        dateTimeTaken?.let { chips.add("Captured: $it") }
        software?.let { chips.add("Software: $it") }
        return chips
    }
}

object ExifPrivacyEngine {

    private const val DEFAULT_MAX_DIMENSION = 2400

    fun openStream(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            Log.e("SafeID", "openStream failed for $uri: ${e.message}")
            null
        }
    }

    /**
     * Loads the raw bytes of [uri] exactly once and produces both the [PrivacyAuditReport]
     * and the safely downscaled and orientation-corrected [Bitmap].
     * Reading the stream only once guarantees compatibility with single-read stream providers
     * like modern Android PhotoPicker pipes and transient grant URIs.
     */
    fun loadAndAudit(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Pair<PrivacyAuditReport, Bitmap?> {
        Log.d("SafeID", "loadAndAudit called for $uri")
        val bytes = try {
            openStream(context, uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("SafeID", "loadAndAudit failed reading stream for $uri", e)
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            Log.e("SafeID", "loadAndAudit: bytes were null or empty for $uri")
            return Pair(
                PrivacyAuditReport(false, null, null, null, null, null, emptyList(), 0),
                null
            )
        }

        val report = try {
            inspectStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            Log.e("SafeID", "inspectStream failed", e)
            PrivacyAuditReport(false, null, null, null, null, null, emptyList(), 0)
        }

        val bitmap = decodeFromBytes(bytes, maxDimension)
        return Pair(report, bitmap)
    }

    fun decodeFromBytes(bytes: ByteArray, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        return try {
            val orientation = try {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e("SafeID", "decodeFromBytes: invalid dimensions ${options.outWidth} x ${options.outHeight}")
                return null
            }

            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val sampledBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: run {
                Log.e("SafeID", "decodeFromBytes: decodeByteArray returned null")
                return null
            }

            val orientedBitmap = applyExifOrientation(sampledBitmap, orientation)
            val currentMax = max(orientedBitmap.width, orientedBitmap.height)
            val result = if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax
                val newW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
                val newH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(orientedBitmap, newW, newH, true)
                if (scaled != orientedBitmap) orientedBitmap.recycle()
                scaled
            } else {
                orientedBitmap
            }
            Log.d("SafeID", "decodeFromBytes success: ${result.width} x ${result.height}")
            result
        } catch (e: Exception) {
            Log.e("SafeID", "decodeFromBytes failed", e)
            null
        }
    }

    /**
     * Inspects image metadata at [uri] via [ExifInterface] and returns a structured privacy audit report.
     */
    fun inspectPrivacyAudit(context: Context, uri: Uri): PrivacyAuditReport {
        return loadAndAudit(context, uri).first
    }

    /**
     * Inspects an [InputStream] using [ExifInterface].
     */
    fun inspectStream(stream: InputStream): PrivacyAuditReport {
        val exif = ExifInterface(stream)
        val tags = mutableListOf<SensitiveTag>()

        // 1. GPS Coordinates
        val latLong = exif.latLong
        val gpsStr = latLong?.let {
            val latDir = if (it[0] >= 0) "N" else "S"
            val lonDir = if (it[1] >= 0) "E" else "W"
            String.format(
                Locale.US,
                "%.4f° %s, %.4f° %s",
                Math.abs(it[0]),
                latDir,
                Math.abs(it[1]),
                lonDir
            )
        }
        if (gpsStr != null) {
            tags.add(SensitiveTag(ExifInterface.TAG_GPS_LATITUDE, "GPS Coordinates", gpsStr))
        }

        // 2. Camera Hardware
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() }
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
        make?.let { tags.add(SensitiveTag(ExifInterface.TAG_MAKE, "Camera Make", it)) }
        model?.let { tags.add(SensitiveTag(ExifInterface.TAG_MODEL, "Camera Model", it)) }

        // 3. Timestamp
        val dateTime = (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME))?.trim()?.takeIf { it.isNotEmpty() }
        dateTime?.let { tags.add(SensitiveTag(ExifInterface.TAG_DATETIME, "Capture Time", it)) }

        // 4. Software / Editing tools
        val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.trim()?.takeIf { it.isNotEmpty() }
        software?.let { tags.add(SensitiveTag(ExifInterface.TAG_SOFTWARE, "Software", it)) }

        // Additional sensitive metadata fields
        exif.getAttribute(ExifInterface.TAG_ARTIST)?.takeIf { it.isNotBlank() }?.let {
            tags.add(SensitiveTag(ExifInterface.TAG_ARTIST, "Artist / Author", it))
        }
        exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.takeIf { it.isNotBlank() }?.let {
            tags.add(SensitiveTag(ExifInterface.TAG_USER_COMMENT, "User Comment", it))
        }
        exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.takeIf { it.isNotBlank() }?.let {
            tags.add(SensitiveTag(ExifInterface.TAG_LENS_MODEL, "Lens Model", it))
        }

        val hasExif = tags.isNotEmpty()

        return PrivacyAuditReport(
            hasExifData = hasExif,
            gpsCoordinates = gpsStr,
            cameraMake = make,
            cameraModel = model,
            dateTimeTaken = dateTime,
            software = software,
            detectedTags = tags,
            strippedTagCount = tags.size
        )
    }

    /**
     * Decodes the image at [uri] safely using [BitmapFactory], downsampling if dimensions exceed
     * [maxDimension] to prevent OOM errors, and applying EXIF orientation rotation.
     * All EXIF metadata is naturally discarded because only pixel data is retained.
     */
    fun decodeSafely(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap? {
        return loadAndAudit(context, uri, maxDimension).second
    }


    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var currentW = width
        var currentH = height
        while (currentW > maxDimension || currentH > maxDimension) {
            sampleSize *= 2
            currentW /= 2
            currentH /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return source
        }

        return try {
            val rotated = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
            if (rotated != source) source.recycle()
            rotated
        } catch (_: OutOfMemoryError) {
            source
        }
    }
}

