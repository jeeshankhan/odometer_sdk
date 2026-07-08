package com.odometersdk.detection

import android.graphics.Bitmap
import android.graphics.Matrix
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Turns a raw camera crop of "roughly where the odometer is" into something
 * OCR can actually read: deskewed, contrast-normalized, denoised.
 *
 * Requires OpenCV (org.opencv:opencv:4.9.0 via Maven Central, or the
 * quickbirdstudios OpenCV AAR if you prefer not to pull the full native libs
 * yourself). This is the single biggest lever on real-world accuracy —
 * dashboards are rarely photographed dead-on, and raw ML Kit OCR degrades
 * fast on skewed, glare-heavy digital odometer displays.
 */
object ImagePreprocessor {

    /** Full pipeline: crop -> grayscale -> CLAHE contrast -> deskew -> denoise -> upscale */
    fun enhance(source: Bitmap, roi: RectF01): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(source, mat)

        val cropped = cropNormalized(mat, roi)
        val gray = Mat()
        Imgproc.cvtColor(cropped, gray, Imgproc.COLOR_RGBA2GRAY)

        // CLAHE handles uneven dashboard lighting / glare far better than a global
        // histogram equalization would.
        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        val contrastBoosted = Mat()
        clahe.apply(gray, contrastBoosted)

        val deskewed = deskew(contrastBoosted)

        val denoised = Mat()
        Imgproc.bilateralFilter(deskewed, denoised, 5, 50.0, 50.0)

        // Upscale small crops — ML Kit's text recognizer performs meaningfully
        // better above ~32px digit height.
        val upscaled = Mat()
        val targetHeight = 200.0
        val scale = targetHeight / denoised.rows()
        Imgproc.resize(denoised, upscaled, Size(), scale, scale, Imgproc.INTER_CUBIC)

        val outBitmap = Bitmap.createBitmap(upscaled.cols(), upscaled.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(upscaled, outBitmap)

        listOf(mat, cropped, gray, contrastBoosted, deskewed, denoised, upscaled).forEach { it.release() }
        return outBitmap
    }

    private fun cropNormalized(mat: Mat, roi: RectF01): Mat {
        val x = (roi.left * mat.cols()).toInt().coerceIn(0, mat.cols() - 1)
        val y = (roi.top * mat.rows()).toInt().coerceIn(0, mat.rows() - 1)
        val w = ((roi.right - roi.left) * mat.cols()).toInt().coerceAtLeast(1)
            .coerceAtMost(mat.cols() - x)
        val h = ((roi.bottom - roi.top) * mat.rows()).toInt().coerceAtLeast(1)
            .coerceAtMost(mat.rows() - y)
        return Mat(mat, Rect(x, y, w, h)).clone()
    }

    /**
     * Detects the dominant skew angle of the digit strip via minAreaRect on the
     * largest contour and rotates it flat. Falls back to the original image if
     * no confident contour is found (better a slightly skewed read than a
     * botched rotation).
     */
    private fun deskew(gray: Mat): Mat {
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release()

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return gray
        if (Imgproc.contourArea(largest) < gray.rows() * gray.cols() * 0.05) return gray

        val points2f = MatOfPoint2f(*largest.toArray())
        val rect = Imgproc.minAreaRect(points2f)
        var angle = rect.angle
        if (angle < -45) angle += 90.0

        if (Math.abs(angle) < 0.5) return gray // not worth rotating

        val center = Point(gray.cols() / 2.0, gray.rows() / 2.0)
        val rotMat = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val rotated = Mat()
        Imgproc.warpAffine(gray, rotated, rotMat, gray.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        return rotated
    }
}

/** Normalized (0..1) bounding box, independent of source image resolution. */
data class RectF01(val left: Float, val top: Float, val right: Float, val bottom: Float)
