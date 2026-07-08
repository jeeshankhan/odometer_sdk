# Changelog

## 1.0.0
- Initial release: CameraX capture, TFLite odometer-region detection,
  ML Kit OCR, perspective-correction preprocessing, multi-frame temporal
  fusion, and confidence-breakdown scoring.
- Known limitation: `odometer_detector.tflite` model not bundled — must be
  supplied by the consuming app (see README).
- Known limitation: CameraX `PlatformView` preview wiring not yet included.
