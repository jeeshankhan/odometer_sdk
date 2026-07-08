package com.odometersdk.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Thin CameraX wrapper: preview to a PreviewView (rendered as a Flutter
 * PlatformView) + a throttled ImageAnalysis stream feeding OdometerPipeline.
 *
 * Frames are throttled to ~6fps for analysis (full preview stays smooth at
 * native rate) — that's plenty for the temporal-fusion window and keeps
 * TFLite + ML Kit CPU/NNAPI load reasonable on mid-range devices.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var lastAnalyzedAtMs = 0L
    private val analysisIntervalMs = 166L // ~6fps

    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null

    fun start(
        previewView: androidx.camera.view.PreviewView,
        onFrame: (Bitmap) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor) { imageProxy ->
                        throttledAnalyze(imageProxy, onFrame)
                    }
                }
            imageAnalysis = analysis

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)

            // Continuous autofocus with a macro-leaning metering point matters
            // a lot here — odometers are typically read from 20-40cm away.
            camera?.cameraControl?.enableTorch(false)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun throttledAnalyze(imageProxy: ImageProxy, onFrame: (Bitmap) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAtMs < analysisIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedAtMs = now
        try {
            val bitmap = imageProxy.toBitmap()
            onFrame(bitmap)
        } finally {
            imageProxy.close()
        }
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun stop() {
        imageAnalysis?.clearAnalyzer()
        analysisExecutor.shutdown()
    }
}

/** ImageProxy -> Bitmap; real implementation should use YuvToRgbConverter for
 *  perf instead of the naive path shown here (kept simple for readability). */
private fun ImageProxy.toBitmap(): Bitmap {
    val yuvToRgbConverter = com.odometersdk.util.YuvToRgbConverter(this)
    return yuvToRgbConverter.convert()
}
