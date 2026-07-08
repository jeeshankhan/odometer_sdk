import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'odometer_result.dart';
import 'odometer_sdk_method_channel.dart';

abstract class OdometerSdkPlatform extends PlatformInterface {
  OdometerSdkPlatform() : super(token: _token);
  static final Object _token = Object();

  static OdometerSdkPlatform _instance = MethodChannelOdometerSdk();
  static OdometerSdkPlatform get instance => _instance;
  static set instance(OdometerSdkPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<void> startScan({int? previousKnownReading}) =>
      throw UnimplementedError('startScan() has not been implemented.');

  Future<OdometerResult> captureAndFinalize() =>
      throw UnimplementedError('captureAndFinalize() has not been implemented.');

  Future<void> stopScan() => throw UnimplementedError('stopScan() has not been implemented.');

  Future<void> setTorch(bool enabled) => throw UnimplementedError('setTorch() has not been implemented.');

  Stream<OdometerLiveStatus> get liveStatusStream =>
      throw UnimplementedError('liveStatusStream has not been implemented.');
}
