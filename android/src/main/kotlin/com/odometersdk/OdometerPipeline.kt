package com.odometersdk

import android.content.Context
import android.graphics.Bitmap
import com.odometersdk.detection.ImagePreprocessor
import com.odometersdk.detection.OdometerDetector
import com.odometersdk.fusion.TemporalFusion
import com.odometersdk.model.ConfidenceBreakdown
import com.odometersdk.model.OdometerResult
import com.odometersdk.model.OdometerResultCode
import com.odometersdk.ocr.OdometerOcrEngine
import com.odometersdk.validation.ReadingValidator

/**
 * End-to-end per-frame pipeline. Two entry points:
 *  - processFrame(): call on every CameraX analysis frame during "live scan" mode;
 *    feeds the temporal fusion buffer and streams intermediate status back so the
 *    UI can show "hold steady" / "getting closer" feedback instead of a blind spinner.
 *  - finalize(): call once processFrame() has reported isReady()==true to get the
 *    fused, high-confidence OdometerResult.
 */
class OdometerPipeline(context: Context, previousKnownReading: Int? = null) {

    private val detector = OdometerDetector(context)
    private val ocrEngine = OdometerOcrEngine()
    private val fusion = TemporalFusion(windowSize = 5)
    private val previousReading = previousKnownReading

    private var lastDetection: OdometerDetector.Detection? = null
    private var lastCrop: Bitmap? = null
    private var lastOcrConfidence: Float = 0f

    sealed class LiveStatus {
        object SearchingForOdometer : LiveStatus()
        data class Locked(val detectionScore: Float, val samplesCollected: Int, val samplesNeeded: Int) : LiveStatus()
        object ReadyToFinalize : LiveStatus()
    }

    suspend fun processFrame(frame: Bitmap): LiveStatus {
        val detection = detector.detect(frame) ?: run {
            fusion.reset()
            return LiveStatus.SearchingForOdometer
        }
        lastDetection = detection

        val enhanced = ImagePreprocessor.enhance(frame, detection.roi)
        lastCrop = enhanced

        val ocr = ocrEngine.read(enhanced) ?: return LiveStatus.Locked(detection.score, fusion.let { 0 }, 3)
        lastOcrConfidence = ocr.confidence

        val validation = ReadingValidator.validate(ocr.digits, previousReading)
        if (validation.isPlausible && validation.normalized != null) {
            fusion.push(validation.normalized)
        }

        return if (fusion.isReady()) LiveStatus.ReadyToFinalize
               else LiveStatus.Locked(detection.score, 0, 3)
    }

    fun finalize(): OdometerResult {
        val detection = lastDetection
            ?: return notDetectedResult()

        val fused = fusion.fuse()
            ?: return OdometerResult(
                isValid = false, reading = null, units = null, confidence = 0f, breakdown = null,
                code = OdometerResultCode.OCR_LOW_CONFIDENCE,
                message = "Could not obtain a stable reading. Please retry with better lighting/angle.",
                croppedImage = lastCrop, boundingBox = detection.roi.let {
                    floatArrayOf(it.left, it.top, it.right, it.bottom)
                }
            )

        val validation = ReadingValidator.validate(fused.consensus, previousReading)
        val breakdown = ConfidenceBreakdown(
            detectionScore = detection.score,
            ocrScore = lastOcrConfidence,
            temporalConsistency = fused.agreement,
            formatScore = validation.formatScore
        )
        val fusedConfidence = breakdown.fused()

        val code = when {
            !validation.isPlausible -> OdometerResultCode.FORMAT_INVALID
            fused.agreement < 0.5f -> OdometerResultCode.UNSTABLE_ACROSS_FRAMES
            fusedConfidence < 0.55f -> OdometerResultCode.OCR_LOW_CONFIDENCE
            else -> OdometerResultCode.OK
        }

        return OdometerResult(
            isValid = code == OdometerResultCode.OK,
            reading = if (code == OdometerResultCode.OK) validation.normalized else null,
            units = null,
            confidence = fusedConfidence,
            breakdown = breakdown,
            code = code,
            message = messageFor(code),
            croppedImage = lastCrop,
            boundingBox = floatArrayOf(detection.roi.left, detection.roi.top, detection.roi.right, detection.roi.bottom)
        )
    }

    fun reset() {
        fusion.reset()
        lastDetection = null
        lastCrop = null
        lastOcrConfidence = 0f
    }

    fun close() {
        detector.close()
        ocrEngine.close()
    }

    private fun notDetectedResult() = OdometerResult(
        isValid = false, reading = null, units = null, confidence = 0f, breakdown = null,
        code = OdometerResultCode.NO_ODOMETER_DETECTED,
        message = "No odometer detected in frame. Aim the camera at the dashboard display.",
        croppedImage = null, boundingBox = null
    )

    private fun messageFor(code: OdometerResultCode) = when (code) {
        OdometerResultCode.OK -> "Reading captured successfully."
        OdometerResultCode.FORMAT_INVALID -> "Detected text doesn't look like a valid odometer reading."
        OdometerResultCode.UNSTABLE_ACROSS_FRAMES -> "Reading was inconsistent across frames. Hold the camera steadier."
        OdometerResultCode.OCR_LOW_CONFIDENCE -> "Low confidence read. Try reducing glare or moving closer."
        OdometerResultCode.IMAGE_TOO_BLURRY -> "Image too blurry. Hold the camera steady."
        OdometerResultCode.NO_ODOMETER_DETECTED -> "No odometer detected in frame."
    }
}
