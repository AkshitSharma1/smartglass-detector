enum WifiDiscoveryStatus {
  inactive,
  discovering,
  permissionRequired,
  wifiDisabled,
  locationDisabled,
  unsupported,
  error,
}

class WifiDiscoveryState {
  const WifiDiscoveryState({
    required this.status,
    required this.message,
    required this.updatedAtMs,
  });

  const WifiDiscoveryState.inactive()
    : status = WifiDiscoveryStatus.inactive,
      message = null,
      updatedAtMs = 0;

  final WifiDiscoveryStatus status;
  final String? message;
  final int updatedAtMs;

  factory WifiDiscoveryState.fromMap(Map<Object?, Object?> map) {
    final serialized = map['status'] as String? ?? 'inactive';
    return WifiDiscoveryState(
      status: WifiDiscoveryStatus.values.firstWhere(
        (value) => value.name == serialized,
        orElse: () => WifiDiscoveryStatus.error,
      ),
      message: map['message'] as String?,
      updatedAtMs: (map['updatedAtMs'] as num?)?.toInt() ?? 0,
    );
  }
}
