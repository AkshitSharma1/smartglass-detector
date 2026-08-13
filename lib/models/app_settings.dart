enum VibrationPreset { gentle, doublePulse, heartbeat, urgent }

enum AlertMode { soundOnly, vibrationOnly, both }

enum AppThemePreference { system, light, dark, amoled }

enum AppAccentColor { blue, purple, teal, orange, rose }

extension AppThemePreferencePresentation on AppThemePreference {
  String get serialized => name;

  String get label => switch (this) {
    AppThemePreference.system => 'System default',
    AppThemePreference.light => 'Light',
    AppThemePreference.dark => 'Dark',
    AppThemePreference.amoled => 'AMOLED (pure black)',
  };
}

extension AppAccentColorPresentation on AppAccentColor {
  String get serialized => name;

  String get label => switch (this) {
    AppAccentColor.blue => 'Blue',
    AppAccentColor.purple => 'Purple',
    AppAccentColor.teal => 'Teal',
    AppAccentColor.orange => 'Orange',
    AppAccentColor.rose => 'Rose',
  };
}

extension AlertModePresentation on AlertMode {
  String get serialized => switch (this) {
    AlertMode.soundOnly => 'soundOnly',
    AlertMode.vibrationOnly => 'vibrationOnly',
    AlertMode.both => 'both',
  };

  String get label => switch (this) {
    AlertMode.soundOnly => 'Sound only',
    AlertMode.vibrationOnly => 'Vibration only',
    AlertMode.both => 'Sound + vibration',
  };

  bool get usesSound => this != AlertMode.vibrationOnly;
  bool get usesVibration => this != AlertMode.soundOnly;
}

extension VibrationPresetPresentation on VibrationPreset {
  String get serialized => switch (this) {
    VibrationPreset.gentle => 'gentle',
    VibrationPreset.doublePulse => 'doublePulse',
    VibrationPreset.heartbeat => 'heartbeat',
    VibrationPreset.urgent => 'urgent',
  };

  String get label => switch (this) {
    VibrationPreset.gentle => 'Gentle',
    VibrationPreset.doublePulse => 'Double Pulse',
    VibrationPreset.heartbeat => 'Heartbeat',
    VibrationPreset.urgent => 'Urgent',
  };
}

class AppSettings {
  const AppSettings({
    required this.alertsEnabled,
    required this.loggingEnabled,
    required this.debugEnabled,
    required this.alertThresholdRssi,
    required this.alertMode,
    required this.alertSoundUri,
    required this.alertSoundName,
    required this.vibrationPreset,
    required this.alertDurationMs,
    required this.wifiScanIntervalSeconds,
    required this.themePreference,
    required this.accentColor,
  });

  const AppSettings.defaults()
    : alertsEnabled = true,
      loggingEnabled = true,
      debugEnabled = false,
      alertThresholdRssi = -75,
      alertMode = AlertMode.both,
      alertSoundUri = defaultAlertSoundUri,
      alertSoundName = 'Default notification sound',
      vibrationPreset = VibrationPreset.doublePulse,
      alertDurationMs = 10000,
      wifiScanIntervalSeconds = 5,
      themePreference = AppThemePreference.system,
      accentColor = AppAccentColor.blue;

  static const supportedDurationsMs = <int>[5000, 10000, 20000, 30000, 60000];
  static const supportedWifiScanIntervalsSeconds = <int>[3, 5, 10, 15];
  static const defaultAlertSoundUri =
      'content://settings/system/notification_sound';

  final bool alertsEnabled;
  final bool loggingEnabled;
  final bool debugEnabled;
  final int alertThresholdRssi;
  final AlertMode alertMode;
  final String alertSoundUri;
  final String alertSoundName;
  final VibrationPreset vibrationPreset;
  final int alertDurationMs;
  final int wifiScanIntervalSeconds;
  final AppThemePreference themePreference;
  final AppAccentColor accentColor;

  factory AppSettings.fromMap(Map<Object?, Object?> map) {
    return AppSettings(
      alertsEnabled: map['alertsEnabled'] as bool? ?? true,
      loggingEnabled: map['loggingEnabled'] as bool? ?? true,
      debugEnabled: map['debugEnabled'] as bool? ?? false,
      alertThresholdRssi: ((map['alertThresholdRssi'] as num?)?.toInt() ?? -75)
          .clamp(-100, -30),
      alertMode: _alertModeFromString(map['alertMode'] as String?),
      alertSoundUri: map['alertSoundUri'] as String? ?? defaultAlertSoundUri,
      alertSoundName:
          map['alertSoundName'] as String? ?? 'Default notification sound',
      vibrationPreset: _presetFromString(map['vibrationPreset'] as String?),
      alertDurationMs: _normalizeDuration(
        (map['alertDurationMs'] as num?)?.toInt() ?? 10000,
      ),
      wifiScanIntervalSeconds: _normalizeWifiScanInterval(
        (map['wifiScanIntervalSeconds'] as num?)?.toInt() ?? 5,
      ),
      themePreference: _themePreferenceFromString(
        map['themePreference'] as String?,
      ),
      accentColor: _accentColorFromString(map['accentColor'] as String?),
    );
  }

  AppSettings copyWith({
    bool? alertsEnabled,
    bool? loggingEnabled,
    bool? debugEnabled,
    int? alertThresholdRssi,
    AlertMode? alertMode,
    String? alertSoundUri,
    String? alertSoundName,
    VibrationPreset? vibrationPreset,
    int? alertDurationMs,
    int? wifiScanIntervalSeconds,
    AppThemePreference? themePreference,
    AppAccentColor? accentColor,
  }) {
    return AppSettings(
      alertsEnabled: alertsEnabled ?? this.alertsEnabled,
      loggingEnabled: loggingEnabled ?? this.loggingEnabled,
      debugEnabled: debugEnabled ?? this.debugEnabled,
      alertThresholdRssi: (alertThresholdRssi ?? this.alertThresholdRssi).clamp(
        -100,
        -30,
      ),
      alertMode: alertMode ?? this.alertMode,
      alertSoundUri: alertSoundUri ?? this.alertSoundUri,
      alertSoundName: alertSoundName ?? this.alertSoundName,
      vibrationPreset: vibrationPreset ?? this.vibrationPreset,
      alertDurationMs: _normalizeDuration(
        alertDurationMs ?? this.alertDurationMs,
      ),
      wifiScanIntervalSeconds: _normalizeWifiScanInterval(
        wifiScanIntervalSeconds ?? this.wifiScanIntervalSeconds,
      ),
      themePreference: themePreference ?? this.themePreference,
      accentColor: accentColor ?? this.accentColor,
    );
  }

  Map<String, Object> toMap() => <String, Object>{
    'alertsEnabled': alertsEnabled,
    'loggingEnabled': loggingEnabled,
    'debugEnabled': debugEnabled,
    'alertThresholdRssi': alertThresholdRssi,
    'alertMode': alertMode.serialized,
    'alertSoundUri': alertSoundUri,
    'alertSoundName': alertSoundName,
    'vibrationPreset': vibrationPreset.serialized,
    'alertDurationMs': alertDurationMs,
    'wifiScanIntervalSeconds': wifiScanIntervalSeconds,
    'themePreference': themePreference.serialized,
    'accentColor': accentColor.serialized,
  };

  static VibrationPreset _presetFromString(String? value) =>
      VibrationPreset.values.firstWhere(
        (preset) => preset.serialized == value,
        orElse: () => VibrationPreset.doublePulse,
      );

  static AlertMode _alertModeFromString(String? value) =>
      AlertMode.values.firstWhere(
        (mode) => mode.serialized == value,
        orElse: () => AlertMode.both,
      );

  static AppThemePreference _themePreferenceFromString(String? value) =>
      AppThemePreference.values.firstWhere(
        (preference) => preference.serialized == value,
        orElse: () => AppThemePreference.system,
      );

  static AppAccentColor _accentColorFromString(String? value) =>
      AppAccentColor.values.firstWhere(
        (color) => color.serialized == value,
        orElse: () => AppAccentColor.blue,
      );

  static int _normalizeDuration(int value) => supportedDurationsMs.reduce(
    (best, candidate) =>
        (candidate - value).abs() < (best - value).abs() ? candidate : best,
  );

  static int _normalizeWifiScanInterval(int value) =>
      supportedWifiScanIntervalsSeconds.reduce(
        (best, candidate) =>
            (candidate - value).abs() < (best - value).abs() ? candidate : best,
      );
}
