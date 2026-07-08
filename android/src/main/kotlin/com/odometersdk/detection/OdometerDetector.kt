package com.odometersdk.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * Locates the odometer display within a full dashboard frame before OCR runs.
 *
 * This is the piece the "basic" version (OCR-on-full-frame) skips, and it's
 * why that approach false-positives on speed limit stickers, tire pressure
 * placards, clock displays, etc. A single-class SSD/EfficientDet-Lite model
 * trained on dashboard photos (odometer vs. background) is small enough
 * (~4-8MB int8) to run per-frame on-device at 15-30fps on mid-range hardware.
 *
 * Model is NOT included here — you provide odometer_detector.tflite trained
 * on your own labeled dashboard dataset (Roboflow + a few thousand labeled
 * photos is a realistic starting point). This class just wraps inference.
 */
class OdometerDetector(context: Context, modelAssetName: String = "odometer_detector.tflite") {

    private val detector: ObjectDetector = ObjectDetector.createFromFileAndOptions(
        context,
        modelAssetName,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(3)
            .setScoreThreshold(0.35f)
            .build()
    )

    data class Detection(val roi: RectF01, val score: Float)

    /** Returns the single best odometer-region candidate, or null if none clears the threshold. */
    fun detect(frame: Bitmap): Detection? {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(frame))

        val results = detector.detect(tensorImage)
        val best = results.maxByOrNull { it.categories.firstOrNull()?.score ?: 0f } ?: return null
        val category = best.categories.firstOrNull() ?: return null

        val box = best.boundingBox
        val roi = RectF01(
            left = (box.left / 320f).coerceIn(0f, 1f),
            top = (box.top / 320f).coerceIn(0f, 1f),
            right = (box.right / 320f).coerceIn(0f, 1f),
            bottom = (box.bottom / 320f).coerceIn(0f, 1f)
        )
        return Detection(roi, category.score)
    }

    fun close() = detector.close()
}
