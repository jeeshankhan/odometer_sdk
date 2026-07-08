import 'package:flutter/services.dart';
import 'odometer_result.dart';
import 'odometer_sdk_platform_interface.dart';

class MethodChannelOdometerSdk extends OdometerSdkPlatform {
  final methodChannel = const MethodChannel('odometer_sdk/methods');
  final eventChannel = const EventChannel('odometer_sdk/live_status');

  Stream<OdometerLiveStatus>? _liveStatusStream;

  @override
  Future<void> startScan({int? previousKnownReading}) async {
    await methodChannel.invokeMethod<void>('startScan', {
      'previousKnownReading': previousKnownReading,
    });
  }

  @override
  Future<OdometerResult> captureAndFinalize() async {
    final map = await methodChannel.invokeMethod<Map<dynamic, dynamic>>('captureAndFinalize');
    if (map == null) {
      throw PlatformException(code: 'NULL_RESULT', message: 'Native side returned no result.');
    }
    return OdometerResult.fromMap(map);
  }

  @override
  Future<void> stopScan() async {
    await methodChannel.invokeMethod<void>('stopScan');
  }

  @override
  Future<void> setTorch(bool enabled) async {
    await methodChannel.invokeMethod<void>('setTorch', {'enabled': enabled});
  }

  @override
  Stream<OdometerLiveStatus> get liveStatusStream {
    _liveStatusStream ??= eventChannel
        .receiveBroadcastStream()
        .map((event) => OdometerLiveStatus.fromMap(event as Map<dynamic, dynamic>));
    return _liveStatusStream!;
  }
}
