package com.odometersdk

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.LifecycleOwner
import com.odometersdk.model.OdometerResult
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * Two channels, deliberately:
 *  - MethodChannel "odometer_sdk/methods" for imperative calls (start scan,
 *    stop scan, finalize/capture).
 *  - EventChannel "odometer_sdk/live_status" streaming LiveStatus updates so
 *    the Flutter UI can render real-time "searching / locked / hold steady"
 *    feedback instead of a blind loading spinner during the multi-frame scan.
 *
 * This mirrors how ML Kit's own live-camera samples are structured, and it's
 * what makes the difference between "OCR wrapper" and an SDK that feels good
 * to use in a real capture flow.
 */
class OdometerSdkPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null

    private var pipeline: OdometerPipeline? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activityBinding: ActivityPluginBinding? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel = MethodChannel(binding.binaryMessenger, "odometer_sdk/methods")
        methodChannel.setMethodCallHandler(this)
        eventChannel = EventChannel(binding.binaryMessenger, "odometer_sdk/live_status")
        eventChannel.setStreamHandler(this)
        pipeline = OdometerPipeline(binding.applicationContext)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        pipeline?.close()
        scope.cancel()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activityBinding = binding }
    override fun onDetachedFromActivity() { activityBinding = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activityBinding = binding }
    override fun onDetachedFromActivityForConfigChanges() { activityBinding = null }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { eventSink = events }
    override fun onCancel(arguments: Any?) { eventSink = null }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startScan" -> {
                val previousReading = call.argument<Int>("previousKnownReading")
                pipeline?.reset()
                pipeline = OdometerPipeline(activityBinding?.activity?.applicationContext
                    ?: return result.error("NO_CONTEXT", "Activity not attached", null), previousReading)
                result.success(null)
                // Actual per-frame feed happens from the native camera analyzer,
                // which calls pipeline.processFrame() and posts LiveStatus below.
            }
            "captureAndFinalize" -> {
                scope.launch {
                    val odometerResult = pipeline?.finalize()
                    withContext(Dispatchers.Main) {
                        result.success(odometerResult?.toMap())
                    }
                }
            }
            "stopScan" -> {
                pipeline?.reset()
                result.success(null)
            }
            "setTorch" -> {
                // delegated to CameraController instance held by the native camera view
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    /** Called by the native ImageAnalysis analyzer for every throttled frame. */
    fun onNativeFrame(bitmap: Bitmap) {
        scope.launch {
            val status = pipeline?.processFrame(bitmap) ?: return@launch
            withContext(Dispatchers.Main) {
                eventSink?.success(status.toMap())
            }
        }
    }

    private fun OdometerPipeline.LiveStatus.toMap(): Map<String, Any?> = when (this) {
        is OdometerPipeline.LiveStatus.SearchingForOdometer -> mapOf("state" to "searching")
        is OdometerPipeline.LiveStatus.Locked -> mapOf(
            "state" to "locked",
            "detectionScore" to detectionScore,
            "samplesCollected" to samplesCollected,
            "samplesNeeded" to samplesNeeded
        )
        is OdometerPipeline.LiveStatus.ReadyToFinalize -> mapOf("state" to "ready")
    }

    private fun OdometerResult.toMap(): Map<String, Any?> = mapOf(
        "isValid" to isValid,
        "reading" to reading,
        "units" to units,
        "confidence" to confidence,
        "code" to code.name,
        "message" to message,
        "croppedImageBase64" to croppedImage?.toBase64(),
        "boundingBox" to boundingBox?.toList(),
        "breakdown" to breakdown?.let {
            mapOf(
                "detectionScore" to it.detectionScore,
                "ocrScore" to it.ocrScore,
                "temporalConsistency" to it.temporalConsistency,
                "formatScore" to it.formatScore
            )
        }
    )

    private fun Bitmap.toBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
