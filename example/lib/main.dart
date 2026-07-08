import 'dart:async';
import 'package:flutter/material.dart';
import 'package:odometer_sdk/odometer_sdk.dart';

void main() => runApp(const OdometerExampleApp());

class OdometerExampleApp extends StatelessWidget {
  const OdometerExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Odometer SDK Demo',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: const ScanScreen(),
    );
  }
}

class ScanScreen extends StatefulWidget {
  const ScanScreen({super.key});
  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  StreamSubscription<OdometerLiveStatus>? _sub;
  OdometerLiveStatus _status = const OdometerSearching();
  OdometerResult? _result;
  bool _scanning = false;

  Future<void> _startScan() async {
    setState(() {
      _result = null;
      _scanning = true;
    });
    await OdometerSdk.startScan(previousKnownReading: 152340); // e.g. last known reading for this vehicle
    _sub = OdometerSdk.liveStatusStream.listen((status) async {
      setState(() => _status = status);
      if (status is OdometerReady) {
        await _finalize();
      }
    });
  }

  Future<void> _finalize() async {
    final result = await OdometerSdk.captureAndFinalize();
    await _sub?.cancel();
    await OdometerSdk.stopScan();
    setState(() {
      _result = result;
      _scanning = false;
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Scan Odometer')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // In a real app, the native CameraX preview renders here via a
            // PlatformView registered by the plugin (OdometerCameraView).
            AspectRatio(
              aspectRatio: 3 / 4,
              child: Container(
                color: Colors.black,
                alignment: Alignment.center,
                child: Text(
                  '[ CameraX preview PlatformView ]',
                  style: TextStyle(color: Colors.white.withOpacity(0.6)),
                ),
              ),
            ),
            const SizedBox(height: 16),
            if (_scanning) _buildLiveStatus(_status),
            if (_result != null) _buildResult(_result!),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _scanning ? null : _startScan,
              icon: const Icon(Icons.camera_alt),
              label: Text(_scanning ? 'Scanning…' : 'Start Scan'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLiveStatus(OdometerLiveStatus status) {
    return switch (status) {
      OdometerSearching() => const _StatusBanner(
          icon: Icons.search,
          text: 'Point the camera at the odometer display',
          color: Colors.grey,
        ),
      OdometerLocked(:final samplesCollected, :final samplesNeeded) => _StatusBanner(
          icon: Icons.center_focus_strong,
          text: 'Locked on — hold steady ($samplesCollected/$samplesNeeded)',
          color: Colors.orange,
        ),
      OdometerReady() => const _StatusBanner(
          icon: Icons.check_circle,
          text: 'Reading captured, finalizing…',
          color: Colors.green,
        ),
    };
  }

  Widget _buildResult(OdometerResult result) {
    if (!result.isValid) {
      return _StatusBanner(icon: Icons.error_outline, text: result.message, color: Colors.red);
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Reading: ${result.reading}', style: Theme.of(context).textTheme.headlineSmall),
            Text('Confidence: ${(result.confidence * 100).toStringAsFixed(0)}%'),
            if (result.croppedImage != null) ...[
              const SizedBox(height: 8),
              Image.memory(result.croppedImage!, height: 80),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatusBanner extends StatelessWidget {
  final IconData icon;
  final String text;
  final Color color;
  const _StatusBanner({required this.icon, required this.text, required this.color});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: color),
        const SizedBox(width: 8),
        Expanded(child: Text(text)),
      ],
    );
  }
}
