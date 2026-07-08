library odometer_sdk;

export 'src/odometer_result.dart';

import 'src/odometer_result.dart';
import 'src/odometer_sdk_platform_interface.dart';

/// Public API. Typical usage:
///
/// ```dart
/// await OdometerSdk.startScan(previousKnownReading: vehicle.lastOdometer);
///
/// final sub = OdometerSdk.liveStatusStream.listen((status) {
///   // update UI: "searching...", "hold steady, 2/3 frames"...
/// });
///
/// // once status is OdometerReady, or user taps capture:
/// final result = await OdometerSdk.captureAndFinalize();
/// if (result.isValid) {
///   print('Reading: ${result.reading} (${result.confidence})');
/// } else {
///   print('Rejected: ${result.message}');
/// }
///
/// sub.cancel();
/// await OdometerSdk.stopScan();
/// ```
class OdometerSdk {
  OdometerSdk._();

  /// Begins a scan session. [previousKnownReading], if provided, is used to
  /// down-weight OCR results that are physically implausible (a new reading
  /// lower than the last recorded one).
  static Future<void> startScan({int? previousKnownReading}) =>
      OdometerSdkPlatform.instance.startScan(previousKnownReading: previousKnownReading);

  /// Runs multi-frame fusion and returns the final validated result.
  /// Best called once [liveStatusStream] emits [OdometerReady], but can be
  /// called any time (e.g. a manual capture button) — it will fuse whatever
  /// frames have been collected so far.
  static Future<OdometerResult> captureAndFinalize() =>
      OdometerSdkPlatform.instance.captureAndFinalize();

  static Future<void> stopScan() => OdometerSdkPlatform.instance.stopScan();

  static Future<void> setTorch(bool enabled) => OdometerSdkPlatform.instance.setTorch(enabled);

  /// Real-time feedback while scanning: searching / locked-collecting-frames / ready.
  static Stream<OdometerLiveStatus> get liveStatusStream =>
      OdometerSdkPlatform.instance.liveStatusStream;
}
