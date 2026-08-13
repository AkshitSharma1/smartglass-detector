class MediaTransferCandidate {
  const MediaTransferCandidate({
    required this.sessionId,
    required this.observedName,
    required this.address,
    required this.sources,
    required this.rssi,
    required this.frequencyMhz,
    required this.channel,
    required this.firstSeenMs,
    required this.lastSeenMs,
    required this.durationMs,
    required this.active,
    required this.evidence,
  });

  final String sessionId;
  final String observedName;
  final String? address;
  final List<String> sources;
  final int? rssi;
  final int? frequencyMhz;
  final int? channel;
  final int firstSeenMs;
  final int lastSeenMs;
  final int durationMs;
  final bool active;
  final List<String> evidence;

  factory MediaTransferCandidate.fromMap(Map<Object?, Object?> map) {
    return MediaTransferCandidate(
      sessionId: map['sessionId'] as String? ?? 'unknown-session',
      observedName:
          map['observedName'] as String? ?? 'Unknown Wi-Fi Direct peer',
      address: map['address'] as String?,
      sources: (map['sources'] as List<Object?>? ?? const <Object?>[])
          .whereType<String>()
          .toList(growable: false),
      rssi: (map['rssi'] as num?)?.toInt(),
      frequencyMhz: (map['frequencyMhz'] as num?)?.toInt(),
      channel: (map['channel'] as num?)?.toInt(),
      firstSeenMs:
          (map['firstSeenMs'] as num?)?.toInt() ??
          DateTime.now().millisecondsSinceEpoch,
      lastSeenMs:
          (map['lastSeenMs'] as num?)?.toInt() ??
          DateTime.now().millisecondsSinceEpoch,
      durationMs: (map['durationMs'] as num?)?.toInt() ?? 0,
      active: map['active'] as bool? ?? false,
      evidence: (map['evidence'] as List<Object?>? ?? const <Object?>[])
          .whereType<String>()
          .toList(growable: false),
    );
  }
}

class MediaTransferObservation {
  const MediaTransferObservation({
    required this.observedName,
    required this.address,
    required this.source,
    required this.rssi,
    required this.frequencyMhz,
    required this.channel,
    required this.timestampMs,
    required this.nearbyMetaBle,
  });

  final String observedName;
  final String? address;
  final String source;
  final int? rssi;
  final int? frequencyMhz;
  final int? channel;
  final int timestampMs;
  final bool nearbyMetaBle;

  factory MediaTransferObservation.fromMap(Map<Object?, Object?> map) {
    return MediaTransferObservation(
      observedName: map['observedName'] as String? ?? 'Unknown',
      address: map['address'] as String?,
      source: map['source'] as String? ?? 'unknown',
      rssi: (map['rssi'] as num?)?.toInt(),
      frequencyMhz: (map['frequencyMhz'] as num?)?.toInt(),
      channel: (map['channel'] as num?)?.toInt(),
      timestampMs:
          (map['timestampMs'] as num?)?.toInt() ??
          DateTime.now().millisecondsSinceEpoch,
      nearbyMetaBle: map['nearbyMetaBle'] as bool? ?? false,
    );
  }

  String toLogLine() {
    final timestamp = DateTime.fromMillisecondsSinceEpoch(timestampMs);
    return <String>[
      'Image/video transfer detected from nearby smartglasses',
      'Time: ${timestamp.toIso8601String()}',
      'Observed name: $observedName',
      if (address != null) 'Address: $address',
      'Source: $source',
      if (rssi != null) 'RSSI: $rssi dBm',
      if (frequencyMhz != null) 'Frequency: $frequencyMhz MHz',
      if (channel != null) 'Channel: $channel',
      'Nearby smartglass Bluetooth signal: ${nearbyMetaBle ? 'Yes' : 'No'}',
      'Recording status: Transferred media may have been recorded during this activity.',
    ].join('\n');
  }
}
