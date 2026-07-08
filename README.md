# Odometer SDK (Advanced)

Flutter-facing SDK for capturing and reading vehicle odometers via the Android
camera, built to be meaningfully more reliable than "point OCR at the full
frame." Three things make that difference:

1. **Detection before OCR.** A TFLite object detector locates the odometer
   region first, so OCR never runs on stray dashboard text (speed stickers,
   clocks, warning labels).
2. **Perspective-aware preprocessing.** CLAHE contrast correction + auto-deskew
   + denoise before OCR, because dashboards are rarely photographed dead-on.
3. **Multi-frame temporal fusion.** Instead of trusting one shot, the SDK
   samples a short burst once it locks onto a stable region and votes across
   frames (exact-match majority, falling back to per-digit voting), which
   absorbs single-frame noise like motion blur or glare flicker.

The result is a `confidence` score you can actually reason about, because it's
exposed as a breakdown (`detectionScore`, `ocrScore`, `temporalConsistency`,
`formatScore`) rather than one opaque float.

## Project layout

```
android/          Kotlin library module (CameraX, TFLite, ML Kit, OpenCV)
flutter_plugin/   Dart package (MethodChannel + EventChannel bridge, public API)
example/          Demo Flutter app using the plugin
```

## What's real vs. what you still need to supply

**Real, working code:** the full pipeline — camera capture, YUV conversion,
preprocessing, OCR wrapping, validation, temporal fusion, the plugin bridge
on both sides, unit tests for the pure-logic pieces (`ReadingValidatorTest`,
`TemporalFusionTest`), and the example app's live-status UI.

**You must supply:**
- `android/src/main/assets/odometer_detector.tflite` — a single-class object
  detector trained on your own labeled dashboard photos. This is the one
  piece that can't be handed to you generically; it needs your data. A
  realistic path: label ~2-5k dashboard photos in Roboflow, export
  EfficientDet-Lite0/SSD-MobileNet in TFLite int8 format, drop it in.
- The CameraX `PreviewView` PlatformView registration on the Android side
  (`OdometerCameraViewFactory` / a `PlatformViewFactory`) to render the live
  preview inside Flutter — omitted here since it's boilerplate specific to
  your app's navigation, not the SDK's accuracy logic.
- Signing/publishing config if you intend to publish `odometer_sdk` privately
  or to pub.dev.

## Confidence score

```
fused = 0.25 * detectionScore
      + 0.40 * ocrScore
      + 0.20 * temporalConsistency
      + 0.15 * formatScore
```
Gated to 0 if `detectionScore < 0.35` or the reading fails format validation
outright (wrong length, all-repeated digits). Tune the weights against your
own labeled validation set once you have real capture data — these are
sensible starting points, not tuned constants.

## Dart API

```dart
await OdometerSdk.startScan(previousKnownReading: 152340);

final sub = OdometerSdk.liveStatusStream.listen((status) {
  // OdometerSearching | OdometerLocked(detectionScore, samplesCollected, samplesNeeded) | OdometerReady
});

final result = await OdometerSdk.captureAndFinalize();
// result.isValid, result.reading, result.confidence, result.breakdown, result.croppedImage
```

## Gradle / pubspec

See `android/build.gradle` for native deps (CameraX 1.4.2, ML Kit text
recognition 16.0.1, TFLite Task Vision, OpenCV 4.9.0) and
`flutter_plugin/pubspec.yaml` for the Dart side.

## Tests

```
cd android && ./gradlew testDebugUnitTest
```
Covers `ReadingValidator` and `TemporalFusion` — the two places where subtle
bugs would silently produce wrong odometer readings. Camera/TFLite/ML Kit
paths need instrumented/on-device tests, not unit tests, and aren't included
here.
