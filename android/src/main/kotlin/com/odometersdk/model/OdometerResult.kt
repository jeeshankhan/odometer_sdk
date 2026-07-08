package com.odometersdk.model

import android.graphics.Bitmap

/**
 * Breakdown of how the final confidence score was computed.
 * Exposing this (not just one number) is what lets a caller decide
 * "reject", "ask for retake", or "accept" instead of guessing at a threshold.
 */
data class ConfidenceBreakdown(
    val detectionScore: Float,   // how sure the detector is this region IS an odometer
    val ocrScore: Float,         // ML Kit's own per-block confidence for the digits
    val temporalConsistency: Float, // agreement across the last N frames (0 if single-shot)
    val formatScore: Float       // did the string match expected odometer format
) {
    /** Weighted fusion — detection and format are gating, OCR/temporal are the signal. */
    fun fused(): Float {
        if (detectionScore < 0.35f || formatScore == 0f) return 0f
        return (0.25f * detectionScore) +
               (0.40f * ocrScore) +
               (0.20f * temporalConsistency) +
               (0.15f * formatScore)
    }
}

enum class OdometerResultCode {
    OK,
    NO_ODOMETER_DETECTED,
    IMAGE_TOO_BLURRY,
    OCR_LOW_CONFIDENCE,
    FORMAT_INVALID,
    UNSTABLE_ACROSS_FRAMES
}

data class OdometerResult(
    val isValid: Boolean,
    val reading: String?,           // normalized digits only, e.g. "184203"
    val units: String?,             // "km" | "mi" | null if undetermined
    val confidence: Float,          // fused() from ConfidenceBreakdown, 0..1
    val breakdown: ConfidenceBreakdown?,
    val code: OdometerResultCode,
    val message: String,
    val croppedImage: Bitmap?,
    val boundingBox: FloatArray?    // [left, top, right, bottom] in original image coords, normalized 0..1
)
