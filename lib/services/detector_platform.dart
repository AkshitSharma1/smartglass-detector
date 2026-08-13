import 'package:flutter/services.dart';

abstract class DetectorPlatform {
  Stream<Map<Object?, Object?>> get events;

  Future<Map<Object?, Object?>> getAppState();
  Future<Map<Object?, Object?>> getOnboardingState();
  Future<Map<Object?, Object?>> requestCoreScanPermissions();
  Future<Map<Object?, Object?>> requestWifiDiscoveryPermission();
  Future<Map<Object?, Object?>> requestNotificationPermission();
  Future<Map<Object?, Object?>> requestEnableBluetooth();
  Future<Map<Object?, Object?>> startScan();
  Future<Map<Object?, Object?>> stopScan();
  Future<Map<Object?, Object?>> getSettings();
  Future<Map<Object?, Object?>> updateSettings(Map<String, Object> values);
  Future<Map<Object?, Object?>> completeOnboarding();
  Future<void> previewVibration(String preset);
  Future<Map<Object?, Object?>> pickAlertSound(String currentUri);
  Future<void> previewAlertSound(String uri);
  Future<void> openAppSettings();
  Future<void> openAboutPhone();
  Future<void> openDeveloperOptions();
  Future<void> requestBatteryOptimizationExemption();
  Future<void> exportLog(String content);
}

class NativeDetectorPlatform implements DetectorPlatform {
  static const _methods = MethodChannel('com.smartglassdetector.app/control');
  static const _eventChannel = EventChannel(
    'com.smartglassdetector.app/events',
  );

  late final Stream<Map<Object?, Object?>> _events = _eventChannel
      .receiveBroadcastStream()
      .map((event) => Map<Object?, Object?>.from(event as Map));

  @override
  Stream<Map<Object?, Object?>> get events => _events;

  @override
  Future<Map<Object?, Object?>> getAppState() => _invokeMap('getAppState');

  @override
  Future<Map<Object?, Object?>> getOnboardingState() =>
      _invokeMap('getOnboardingState');

  @override
  Future<Map<Object?, Object?>> requestCoreScanPermissions() =>
      _invokeMap('requestCoreScanPermissions');

  @override
  Future<Map<Object?, Object?>> requestWifiDiscoveryPermission() =>
      _invokeMap('requestWifiDiscoveryPermission');

  @override
  Future<Map<Object?, Object?>> requestNotificationPermission() =>
      _invokeMap('requestNotificationPermission');

  @override
  Future<Map<Object?, Object?>> requestEnableBluetooth() =>
      _invokeMap('requestEnableBluetooth');

  @override
  Future<Map<Object?, Object?>> startScan() => _invokeMap('startScan');

  @override
  Future<Map<Object?, Object?>> stopScan() => _invokeMap('stopScan');

  @override
  Future<Map<Object?, Object?>> getSettings() => _invokeMap('getSettings');

  @override
  Future<Map<Object?, Object?>> updateSettings(Map<String, Object> values) =>
      _invokeMap('updateSettings', values);

  @override
  Future<Map<Object?, Object?>> completeOnboarding() =>
      _invokeMap('completeOnboarding');

  @override
  Future<void> previewVibration(String preset) async {
    await _methods.invokeMethod<bool>('previewVibration', <String, Object>{
      'preset': preset,
    });
  }

  @override
  Future<Map<Object?, Object?>> pickAlertSound(String currentUri) =>
      _invokeMap('pickAlertSound', <String, Object>{'currentUri': currentUri});

  @override
  Future<void> previewAlertSound(String uri) async {
    await _methods.invokeMethod<bool>('previewAlertSound', <String, Object>{
      'uri': uri,
    });
  }

  @override
  Future<void> openAppSettings() async {
    await _methods.invokeMethod<bool>('openAppSettings');
  }

  @override
  Future<void> openAboutPhone() async {
    await _methods.invokeMethod<bool>('openAboutPhone');
  }

  @override
  Future<void> openDeveloperOptions() async {
    await _methods.invokeMethod<bool>('openDeveloperOptions');
  }

  @override
  Future<void> requestBatteryOptimizationExemption() async {
    await _methods.invokeMethod<bool>('requestBatteryOptimizationExemption');
  }

  @override
  Future<void> exportLog(String content) async {
    await _methods.invokeMethod<bool>('exportLog', <String, Object>{
      'content': content,
    });
  }

  Future<Map<Object?, Object?>> _invokeMap(
    String method, [
    Object? arguments,
  ]) async {
    final value = await _methods.invokeMethod<Map<Object?, Object?>>(
      method,
      arguments,
    );
    return value ?? <Object?, Object?>{};
  }
}
