import 'dart:convert';
import 'dart:typed_data';

enum OdometerResultCode {
  ok,
  noOdometerDetected,
  imageTooBlurry,
  ocrLowConfidence,
  formatInvalid,
  unstableAcrossFrames,
  unknown,
}

OdometerResultCode _codeFromString(String? s) {
  switch (s) {
    case 'OK':
      return OdometerResultCode.ok;
    case 'NO_ODOMETER_DETECTED':
      return OdometerResultCode.noOdometerDetected;
    case 'IMAGE_TOO_BLURRY':
      return OdometerResultCode.imageTooBlurry;
    case 'OCR_LOW_CONFIDENCE':
      return OdometerResultCode.ocrLowConfidence;
    case 'FORMAT_INVALID':
      return OdometerResultCode.formatInvalid;
    case 'UNSTABLE_ACROSS_FRAMES':
      return OdometerResultCode.unstableAcrossFrames;
    default:
      return OdometerResultCode.unknown;
  }
}

/// Exposes the same detection/OCR/temporal/format breakdown the native side
/// computes, so a caller can build their own accept/retry policy instead of
/// trusting a single opaque confidence float.
class ConfidenceBreakdown {
  final double detectionScore;
  final double ocrScore;
  final double temporalConsistency;
  final double formatScore;

  const ConfidenceBreakdown({
    required this.detectionScore,
    required this.ocrScore,
    required this.temporalConsistency,
    required this.formatScore,
  });

  factory ConfidenceBreakdown.fromMap(Map<dynamic, dynamic> m) => ConfidenceBreakdown(
        detectionScore: (m['detectionScore'] as num).toDouble(),
        ocrScore: (m['ocrScore'] as num).toDouble(),
        temporalConsistency: (m['temporalConsistency'] as num).toDouble(),
        formatScore: (m['formatScore'] as num).toDouble(),
      );
}

class OdometerResult {
  final bool isValid;
  final String? reading;
  final String? units;
  final double confidence;
  final OdometerResultCode code;
  final String message;
  final Uint8List? croppedImage;
  final List<double>? boundingBox; // [left, top, right, bottom], normalized 0..1
  final ConfidenceBreakdown? breakdown;

  const OdometerResult({
    required this.isValid,
    required this.reading,
    required this.units,
    required this.confidence,
    required this.code,
    required this.message,
    required this.croppedImage,
    required this.boundingBox,
    required this.breakdown,
  });

  factory OdometerResult.fromMap(Map<dynamic, dynamic> m) {
    final b64 = m['croppedImageBase64'] as String?;
    return OdometerResult(
      isValid: m['isValid'] as bool? ?? false,
      reading: m['reading'] as String?,
      units: m['units'] as String?,
      confidence: (m['confidence'] as num?)?.toDouble() ?? 0.0,
      code: _codeFromString(m['code'] as String?),
      message: m['message'] as String? ?? '',
      croppedImage: b64 != null ? base64Decode(b64) : null,
      boundingBox: (m['boundingBox'] as List?)?.map((e) => (e as num).toDouble()).toList(),
      breakdown: m['breakdown'] != null ? ConfidenceBreakdown.fromMap(m['breakdown']) : null,
    );
  }
}

/// Live feedback while the camera is scanning, before a final result exists.
sealed class OdometerLiveStatus {
  const OdometerLiveStatus();

  factory OdometerLiveStatus.fromMap(Map<dynamic, dynamic> m) {
    switch (m['state']) {
      case 'searching':
        return const OdometerSearching();
      case 'locked':
        return OdometerLocked(
          detectionScore: (m['detectionScore'] as num).toDouble(),
          samplesCollected: m['samplesCollected'] as int,
          samplesNeeded: m['samplesNeeded'] as int,
        );
      case 'ready':
        return const OdometerReady();
      default:
        return const OdometerSearching();
    }
  }
}

class OdometerSearching extends OdometerLiveStatus {
  const OdometerSearching();
}

class OdometerLocked extends OdometerLiveStatus {
  final double detectionScore;
  final int samplesCollected;
  final int samplesNeeded;
  const OdometerLocked({
    required this.detectionScore,
    required this.samplesCollected,
    required this.samplesNeeded,
  });
}

class OdometerReady extends OdometerLiveStatus {
  const OdometerReady();
}
