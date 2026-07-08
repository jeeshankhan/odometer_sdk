package com.odometersdk.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class OcrReading(val digits: String, val rawText: String, val confidence: Float, val units: String?)

/**
 * Runs ML Kit text recognition on an already-cropped/enhanced odometer image
 * and reduces the result to "just the digits", since odometer displays mix
 * digits with unit labels, trip-mode letters (A/B), decimals for tenths, etc.
 */
class OdometerOcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(enhanced: Bitmap): OcrReading? {
        val input = InputImage.fromBitmap(enhanced, 0)
        val result = recognizer.process(input).await()

        // ML Kit doesn't give a single scalar confidence in the stable API,
        // so we derive one from element-level signal: how much of the
        // recognized text is digit characters, and how consistent element
        // heights are (odometer digit wheels are near-uniform height; noise
        // text usually isn't).
        val allText = result.text
        if (allText.isBlank()) return null

        val digitRun = extractLongestDigitRun(allText) ?: return null
        val units = detectUnits(allText)

        val digitRatio = digitRun.length.toFloat() / allText.count { !it.isWhitespace() }.coerceAtLeast(1)
        val heights = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.map { it.boundingBox?.height() ?: 0 }
        val heightConsistency = heightUniformity(heights)

        val ocrConfidence = (0.6f * digitRatio + 0.4f * heightConsistency).coerceIn(0f, 1f)

        return OcrReading(digits = digitRun, rawText = allText, confidence = ocrConfidence, units = units)
    }

    private fun extractLongestDigitRun(text: String): String? {
        val runs = Regex("\\d+").findAll(text).map { it.value }.toList()
        return runs.maxByOrNull { it.length }
    }

    private fun detectUnits(text: String): String? = when {
        Regex("\\bkm\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "km"
        Regex("\\bmi\\b|\\bmiles\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "mi"
        else -> null
    }

    private fun heightUniformity(heights: List<Int>): Float {
        val nonZero = heights.filter { it > 0 }
        if (nonZero.size < 2) return 0.5f
        val mean = nonZero.average()
        val variance = nonZero.map { (it - mean) * (it - mean) }.average()
        val cv = Math.sqrt(variance) / mean // coefficient of variation
        return (1.0 - cv.coerceIn(0.0, 1.0)).toFloat()
    }

    fun close() = recognizer.close()
}
