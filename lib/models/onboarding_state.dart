class OnboardingState {
  const OnboardingState({
    required this.completed,
    required this.onboardingVersion,
    required this.sdkInt,
    required this.corePermissionsGranted,
    required this.corePermanentlyDenied,
    required this.basePermissionsGranted,
    required this.backgroundLocationRequired,
    required this.backgroundLocationGranted,
    required this.backgroundLocationSettingsRequired,
    required this.wifiRuntimePermissionRequired,
    required this.wifiPermissionGranted,
    required this.wifiPermissionPermanentlyDenied,
    required this.notificationRuntimePermissionRequired,
    required this.notificationPermissionGranted,
    required this.notificationPermanentlyDenied,
    required this.batteryOptimizationExempt,
    required this.wifiScanThrottleQuerySupported,
    required this.wifiScanThrottleEnabled,
  });

  const OnboardingState.initial()
    : completed = false,
      onboardingVersion = 0,
      sdkInt = 0,
      corePermissionsGranted = false,
      corePermanentlyDenied = false,
      basePermissionsGranted = false,
      backgroundLocationRequired = false,
      backgroundLocationGranted = false,
      backgroundLocationSettingsRequired = false,
      wifiRuntimePermissionRequired = true,
      wifiPermissionGranted = false,
      wifiPermissionPermanentlyDenied = false,
      notificationRuntimePermissionRequired = true,
      notificationPermissionGranted = false,
      notificationPermanentlyDenied = false,
      batteryOptimizationExempt = false,
      wifiScanThrottleQuerySupported = false,
      wifiScanThrottleEnabled = null;

  final bool completed;
  final int onboardingVersion;
  final int sdkInt;
  final bool corePermissionsGranted;
  final bool corePermanentlyDenied;
  final bool basePermissionsGranted;
  final bool backgroundLocationRequired;
  final bool backgroundLocationGranted;
  final bool backgroundLocationSettingsRequired;
  final bool wifiRuntimePermissionRequired;
  final bool wifiPermissionGranted;
  final bool wifiPermissionPermanentlyDenied;
  final bool notificationRuntimePermissionRequired;
  final bool notificationPermissionGranted;
  final bool notificationPermanentlyDenied;
  final bool batteryOptimizationExempt;
  final bool wifiScanThrottleQuerySupported;
  final bool? wifiScanThrottleEnabled;

  bool get wifiScanThrottleDisabled => wifiScanThrottleEnabled == false;

  factory OnboardingState.fromMap(Map<Object?, Object?> map) => OnboardingState(
    completed: map['completed'] as bool? ?? false,
    onboardingVersion: (map['onboardingVersion'] as num?)?.toInt() ?? 0,
    sdkInt: (map['sdkInt'] as num?)?.toInt() ?? 0,
    corePermissionsGranted: map['corePermissionsGranted'] as bool? ?? false,
    corePermanentlyDenied: map['corePermanentlyDenied'] as bool? ?? false,
    basePermissionsGranted: map['basePermissionsGranted'] as bool? ?? false,
    backgroundLocationRequired:
        map['backgroundLocationRequired'] as bool? ?? false,
    backgroundLocationGranted:
        map['backgroundLocationGranted'] as bool? ?? false,
    backgroundLocationSettingsRequired:
        map['backgroundLocationSettingsRequired'] as bool? ?? false,
    wifiRuntimePermissionRequired:
        map['wifiRuntimePermissionRequired'] as bool? ?? false,
    wifiPermissionGranted: map['wifiPermissionGranted'] as bool? ?? false,
    wifiPermissionPermanentlyDenied:
        map['wifiPermissionPermanentlyDenied'] as bool? ?? false,
    notificationRuntimePermissionRequired:
        map['notificationRuntimePermissionRequired'] as bool? ?? false,
    notificationPermissionGranted:
        map['notificationPermissionGranted'] as bool? ?? false,
    notificationPermanentlyDenied:
        map['notificationPermanentlyDenied'] as bool? ?? false,
    batteryOptimizationExempt:
        map['batteryOptimizationExempt'] as bool? ?? false,
    wifiScanThrottleQuerySupported:
        map['wifiScanThrottleQuerySupported'] as bool? ?? false,
    wifiScanThrottleEnabled: map['wifiScanThrottleEnabled'] as bool?,
  );
}
