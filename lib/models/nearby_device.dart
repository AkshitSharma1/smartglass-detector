class NearbyDevice {
  const NearbyDevice({
    required this.deviceId,
    required this.deviceAddress,
    required this.deviceName,
    required this.companyId,
    required this.companyName,
    required this.manufacturerDataHex,
    required this.serviceUuids,
    required this.reasonText,
    required this.confidence,
    required this.rawRssi,
    required this.smoothedRssi,
    required this.txPower,
    required this.distanceMeters,
    required this.distanceMinMeters,
    required this.distanceMaxMeters,
    required this.distanceConfidence,
    required this.sampleCount,
    required this.lastSeenMs,
  });

  final String deviceId;
  final String deviceAddress;
  final String? deviceName;
  final String? companyId;
  final String? companyName;
  final String? manufacturerDataHex;
  final List<String> serviceUuids;
  final String reasonText;
  final String confidence;
  final int rawRssi;
  final double smoothedRssi;
  final int? txPower;
  final double distanceMeters;
  final double distanceMinMeters;
  final double distanceMaxMeters;
  final String distanceConfidence;
  final int sampleCount;
  final int lastSeenMs;

  String get displayName => deviceName ?? 'Unnamed smartglass';

  String get presentationReason {
    final normalized = reasonText.toLowerCase();
    if (normalized.contains('service uuid')) {
      return 'Smartglass Bluetooth service';
    }
    if (normalized.contains('device name')) {
      return 'Smartglass device-name signature';
    }
    if (companyId != null) {
      return 'Smartglass manufacturer signature';
    }
    return 'Smartglass signal signature';
  }

  factory NearbyDevice.fromMap(Map<Object?, Object?> map) {
    return NearbyDevice(
      deviceId: map['deviceId'] as String? ?? 'unknown',
      deviceAddress: map['deviceAddress'] as String? ?? 'Unavailable',
      deviceName: map['deviceName'] as String?,
      companyId: map['companyId'] as String?,
      companyName: map['companyName'] as String?,
      manufacturerDataHex: map['manufacturerDataHex'] as String?,
      serviceUuids: (map['serviceUuids'] as List<Object?>? ?? const [])
          .whereType<String>()
          .toList(growable: false),
      reasonText: map['reasonText'] as String? ?? 'Smartglass signal signature',
      confidence: map['confidence'] as String? ?? 'medium',
      rawRssi: (map['rawRssi'] as num?)?.toInt() ?? -127,
      smoothedRssi: (map['smoothedRssi'] as num?)?.toDouble() ?? -127,
      txPower: (map['txPower'] as num?)?.toInt(),
      distanceMeters: (map['distanceMeters'] as num?)?.toDouble() ?? 100,
      distanceMinMeters: (map['distanceMinMeters'] as num?)?.toDouble() ?? 0.1,
      distanceMaxMeters: (map['distanceMaxMeters'] as num?)?.toDouble() ?? 100,
      distanceConfidence: map['distanceConfidence'] as String? ?? 'low',
      sampleCount: (map['sampleCount'] as num?)?.toInt() ?? 1,
      lastSeenMs:
          (map['lastSeenMs'] as num?)?.toInt() ??
          DateTime.now().millisecondsSinceEpoch,
    );
  }
}
