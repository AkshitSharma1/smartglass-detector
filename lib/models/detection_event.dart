class DetectionEvent {
  const DetectionEvent({
    required this.timestampMs,
    required this.deviceAddress,
    required this.deviceName,
    required this.companyId,
    required this.companyName,
    required this.manufacturerDataHex,
    required this.reasonCodes,
    required this.reasonText,
    required this.confidence,
    required this.rssi,
    required this.txPower,
    required this.serviceUuids,
  });

  final int timestampMs;
  final String deviceAddress;
  final String? deviceName;
  final String? companyId;
  final String? companyName;
  final String? manufacturerDataHex;
  final List<String> reasonCodes;
  final String reasonText;
  final String confidence;
  final int rssi;
  final int? txPower;
  final List<String> serviceUuids;

  DateTime get timestamp =>
      DateTime.fromMillisecondsSinceEpoch(timestampMs).toLocal();

  String get displayName => deviceName ?? 'Unnamed smartglass';

  String get presentationReason {
    if (reasonCodes.any((code) => code == 'meta_service_fd5f')) {
      return 'Smartglass Bluetooth service';
    }
    if (reasonCodes.any((code) => code.contains('name'))) {
      return 'Smartglass device-name signature';
    }
    if (companyId != null) {
      return 'Smartglass manufacturer signature';
    }
    return 'Smartglass signal signature';
  }

  factory DetectionEvent.fromMap(Map<Object?, Object?> map) {
    return DetectionEvent(
      timestampMs: (map['timestampMs'] as num).toInt(),
      deviceAddress: map['deviceAddress'] as String? ?? 'Unavailable',
      deviceName: map['deviceName'] as String?,
      companyId: map['companyId'] as String?,
      companyName: map['companyName'] as String?,
      manufacturerDataHex: map['manufacturerDataHex'] as String?,
      reasonCodes: (map['reasonCodes'] as List<Object?>? ?? const <Object?>[])
          .whereType<String>()
          .toList(growable: false),
      reasonText: map['reasonText'] as String? ?? 'Smartglass signal signature',
      confidence: map['confidence'] as String? ?? 'medium',
      rssi: (map['rssi'] as num?)?.toInt() ?? -127,
      txPower: (map['txPower'] as num?)?.toInt(),
      serviceUuids: (map['serviceUuids'] as List<Object?>? ?? const <Object?>[])
          .whereType<String>()
          .toList(growable: false),
    );
  }

  String toLogLine() {
    final fields = <String>[
      formatDateTime(timestamp),
      'Address: $deviceAddress',
      'Name: $displayName',
      'Company ID: ${companyId ?? 'Not advertised'}',
      'Manufacturer payload: ${manufacturerDataHex ?? 'Not advertised'}',
      'Reason: $presentationReason',
      'Confidence: $confidence',
      'RSSI: $rssi dBm',
      'TX power: ${txPower == null ? 'Not advertised' : '$txPower dBm'}',
    ];
    return fields.join('\n');
  }
}

String formatDateTime(DateTime value) {
  String two(int number) => number.toString().padLeft(2, '0');
  return '${value.year}-${two(value.month)}-${two(value.day)} '
      '${two(value.hour)}:${two(value.minute)}:${two(value.second)}';
}
