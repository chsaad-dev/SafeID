package com.saaddev.safeid.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Offline on-device OCR engine powered by bundled Google ML Kit Text Recognition.
 * Detects sensitive ID, tax, financial, and card numbers and converts them into
 * normalized BlurStrokes for 1-tap redaction.
 */
object SensitiveDataOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Matches CNIC (12345-1234567-1), Aadhaar (1234 5678 9012), SSN (123-45-6789),
    // 16-digit cards, and contiguous 8+ digit ID/account numbers.
    private val sensitivePatterns = listOf(
        Regex("\\b\\d{5}-\\d{7}-\\d\\b"),
        Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        Regex("\\b(?:\\d{4}[\\s-]?){2}\\d{4}\\b"),
        Regex("\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b"),
        Regex("\\b[A-Z]{1,3}\\d{6,10}\\b"),
        Regex("\\b\\d{8,18}\\b")
    )

    suspend fun detectSensitiveStrokes(bitmap: Bitmap): List<BlurStroke> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = suspendCancellableCoroutine<Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        } ?: return@withContext emptyList()

        val width = bitmap.width.toFloat().coerceAtLeast(1f)
        val height = bitmap.height.toFloat().coerceAtLeast(1f)
        val minDim = min(width, height)

        val detectedBoxes = mutableListOf<Rect>()

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                val lineMatched = sensitivePatterns.any { it.containsMatchIn(lineText) }

                if (lineMatched) {
                    // Prefer element-level boxes when the line has mixed label + ID text
                    var elementMatchedAny = false
                    for (element in line.elements) {
                        val elText = element.text.trim()
                        val digitCount = elText.count { it.isDigit() }
                        if (digitCount >= 4 && sensitivePatterns.any { it.containsMatchIn(elText) }) {
                            element.boundingBox?.let {
                                detectedBoxes.add(it)
                                elementMatchedAny = true
                            }
                        }
                    }
                    if (!elementMatchedAny) {
                        line.boundingBox?.let { detectedBoxes.add(it) }
                    }
                }
            }
        }

        // Deduplicate overlapping boxes and convert to horizontal BlurStrokes
        detectedBoxes
            .distinctBy { "${it.left}_${it.top}_${it.right}_${it.bottom}" }
            .map { box ->
                val centerY = ((box.top + box.bottom) / 2f / height).coerceIn(0f, 1f)
                val boxHeight = (box.bottom - box.top).toFloat().coerceAtLeast(12f)
                val padX = boxHeight * 0.25f
                val startX = ((box.left - padX) / width).coerceIn(0f, 1f)
                val endX = ((box.right + padX) / width).coerceIn(0f, 1f)
                val normalizedRadius = ((boxHeight * 1.15f) / minDim).coerceIn(0.025f, 0.12f)

                BlurStroke(
                    points = listOf(
                        NormalizedPoint(startX, centerY),
                        NormalizedPoint(endX, centerY)
                    ),
                    normalizedRadius = normalizedRadius
                )
            }
    }
}
