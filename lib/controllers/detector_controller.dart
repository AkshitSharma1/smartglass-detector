import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/app_settings.dart';
import '../models/detection_event.dart';
import '../models/media_transfer_candidate.dart';
import '../models/nearby_device.dart';
import '../models/onboarding_state.dart';
import '../models/wifi_discovery_state.dart';
import '../services/detector_platform.dart';

enum DetectorState {
  stopped,
  permissionsRequired,
  bluetoothDisabled,
  unsupported,
  starting,
  scanning,
  stopping,
  error,
}

class DetectorController extends ChangeNotifier {
  DetectorController(this._platform);

  final DetectorPlatform _platform;
  StreamSubscription<Map<Object?, Object?>>? _eventSubscription;
  bool _initialized = false;

  AppSettings settings = const AppSettings.defaults();
  OnboardingState onboardingState = const OnboardingState.initial();
  DetectorState state = DetectorState.stopped;
  bool initializing = true;
  bool bleSupported = true;
  bool bluetoothEnabled = false;
  bool permissionsGranted = false;
  bool permanentlyDenied = false;
  bool notificationPermissionGranted = true;
  bool notificationPermanentlyDenied = false;
  bool wifiDiscoveryPermissionGranted = false;
  bool wifiPermissionPermanentlyDenied = false;
  String? errorMessage;
  DetectionEvent? lastDetection;
  List<NearbyDevice> nearbyDevices = const <NearbyDevice>[];
  List<NearbyDevice> recentDevices = const <NearbyDevice>[];
  List<MediaTransferCandidate> mediaTransferCandidates =
      const <MediaTransferCandidate>[];
  WifiDiscoveryState wifiDiscoveryState = const WifiDiscoveryState.inactive();
  final List<DetectionEvent> detections = <DetectionEvent>[];
  final List<MediaTransferObservation> mediaTransferObservations =
      <MediaTransferObservation>[];
  final List<String> debugMessages = <String>[];

  bool get isScanning => state == DetectorState.scanning;

  int get activeMediaTransferCount =>
      mediaTransferCandidates.where((candidate) => candidate.active).length;

  bool get isBusy =>
      initializing ||
      state == DetectorState.starting ||
      state == DetectorState.stopping;

  Future<void> initialize() async {
    if (_initialized) {
      return;
    }
    _initialized = true;
    _eventSubscription = _platform.events.listen(
      _handlePlatformEvent,
      onError: (Object error) {
        state = DetectorState.error;
        errorMessage = error.toString();
        notifyListeners();
      },
    );

    try {
      final results = await Future.wait<Map<Object?, Object?>>([
        _platform.getSettings(),
        _platform.getAppState(),
        _platform.getOnboardingState(),
      ]);
      settings = AppSettings.fromMap(results[0]);
      _applyAppState(results[1]);
      _applyOnboardingState(results[2]);
    } catch (error) {
      state = DetectorState.error;
      errorMessage = 'Could not initialize the detector: $error';
    } finally {
      initializing = false;
      notifyListeners();
    }
  }

  Future<void> start() async {
    if (isBusy || isScanning) {
      return;
    }
    errorMessage = null;
    permanentlyDenied = false;
    state = DetectorState.starting;
    notifyListeners();

    try {
      final permissionResult = await _platform.requestCoreScanPermissions();
      permissionsGranted = permissionResult['granted'] as bool? ?? false;
      permanentlyDenied =
          permissionResult['permanentlyDenied'] as bool? ?? false;
      await refreshOnboardingState();
      if (!permissionsGranted) {
        state = DetectorState.permissionsRequired;
        notifyListeners();
        return;
      }

      final bluetoothResult = await _platform.requestEnableBluetooth();
      final bluetoothStatus = bluetoothResult['status'] as String?;
      if (bluetoothStatus == 'unsupported') {
        bleSupported = false;
        state = DetectorState.unsupported;
        notifyListeners();
        return;
      }
      if (bluetoothStatus != 'enabled') {
        bluetoothEnabled = false;
        state = DetectorState.bluetoothDisabled;
        notifyListeners();
        return;
      }
      bluetoothEnabled = true;

      final scanResult = await _platform.startScan();
      switch (scanResult['status']) {
        case 'started':
          state = DetectorState.starting;
        case 'permissionRequired':
          permissionsGranted = false;
          state = DetectorState.permissionsRequired;
        case 'bluetoothDisabled':
          bluetoothEnabled = false;
          state = DetectorState.bluetoothDisabled;
        case 'unsupported':
          bleSupported = false;
          state = DetectorState.unsupported;
        default:
          state = DetectorState.error;
          errorMessage = 'Android could not start BLE scanning.';
      }
      notifyListeners();
    } catch (error) {
      state = DetectorState.error;
      errorMessage = 'Could not start scanning: $error';
      notifyListeners();
    }
  }

  Future<void> stop() async {
    if ((isBusy && state != DetectorState.starting) ||
        state == DetectorState.stopped) {
      return;
    }
    state = DetectorState.stopping;
    notifyListeners();
    try {
      await _platform.stopScan();
      nearbyDevices = const <NearbyDevice>[];
      recentDevices = const <NearbyDevice>[];
      mediaTransferCandidates = const <MediaTransferCandidate>[];
      wifiDiscoveryState = const WifiDiscoveryState.inactive();
      state = DetectorState.stopped;
      notifyListeners();
    } catch (error) {
      state = DetectorState.error;
      errorMessage = 'Could not stop scanning: $error';
      notifyListeners();
    }
  }

  Future<void> updateSettings(AppSettings updated) async {
    try {
      final response = await _platform.updateSettings(updated.toMap());
      settings = AppSettings.fromMap(response);
      notifyListeners();
    } catch (error) {
      errorMessage = 'Could not save settings: $error';
      notifyListeners();
    }
  }

  Future<void> requestNotificationPermission() async {
    try {
      final result = await _platform.requestNotificationPermission();
      notificationPermissionGranted = result['granted'] as bool? ?? false;
      notificationPermanentlyDenied =
          result['permanentlyDenied'] as bool? ?? false;
      await refreshOnboardingState();
      notifyListeners();
    } catch (_) {
      notificationPermissionGranted = false;
      notifyListeners();
    }
  }

  Future<void> previewVibration(VibrationPreset preset) =>
      _platform.previewVibration(preset.serialized);

  Future<void> chooseAlertSound() async {
    try {
      final result = await _platform.pickAlertSound(settings.alertSoundUri);
      if (result['cancelled'] as bool? ?? true) {
        return;
      }
      final uri = result['uri'] as String?;
      final name = result['name'] as String?;
      if (uri == null || name == null) {
        return;
      }
      await updateSettings(
        settings.copyWith(alertSoundUri: uri, alertSoundName: name),
      );
    } catch (error) {
      errorMessage = 'Could not choose an alert sound: $error';
      notifyListeners();
    }
  }

  Future<void> previewAlertSound() =>
      _platform.previewAlertSound(settings.alertSoundUri);

  Future<void> openAppSettings() => _platform.openAppSettings();

  Future<void> openAboutPhone() => _platform.openAboutPhone();

  Future<void> openDeveloperOptions() => _platform.openDeveloperOptions();

  Future<void> requestBatteryOptimizationExemption() async {
    try {
      await _platform.requestBatteryOptimizationExemption();
      await refreshOnboardingState();
    } catch (error) {
      errorMessage = 'Could not open battery optimization settings: $error';
      notifyListeners();
    }
  }

  Future<void> refreshSystemState() async {
    try {
      final results = await Future.wait<Map<Object?, Object?>>([
        _platform.getAppState(),
        _platform.getOnboardingState(),
      ]);
      _applyAppState(results[0]);
      _applyOnboardingState(results[1]);
      notifyListeners();
    } catch (error) {
      errorMessage = 'Could not refresh app status: $error';
      notifyListeners();
    }
  }

  Future<void> refreshOnboardingState() async {
    try {
      _applyOnboardingState(await _platform.getOnboardingState());
      notifyListeners();
    } catch (error) {
      errorMessage = 'Could not refresh setup status: $error';
      notifyListeners();
    }
  }

  Future<void> requestCoreScanPermissions() async {
    try {
      final result = await _platform.requestCoreScanPermissions();
      permissionsGranted = result['granted'] as bool? ?? false;
      permanentlyDenied = result['permanentlyDenied'] as bool? ?? false;
      await refreshOnboardingState();
    } catch (error) {
      errorMessage = 'Could not request scanning access: $error';
      notifyListeners();
    }
  }

  Future<void> requestWifiDiscoveryPermission() async {
    try {
      final result = await _platform.requestWifiDiscoveryPermission();
      wifiDiscoveryPermissionGranted = result['granted'] as bool? ?? false;
      wifiPermissionPermanentlyDenied =
          result['permanentlyDenied'] as bool? ?? false;
      await refreshOnboardingState();
    } catch (error) {
      errorMessage = 'Could not request Nearby Wi-Fi access: $error';
      notifyListeners();
    }
  }

  Future<void> completeOnboarding() async {
    try {
      _applyOnboardingState(await _platform.completeOnboarding());
      notifyListeners();
    } catch (error) {
      errorMessage = 'Could not finish setup: $error';
      notifyListeners();
      rethrow;
    }
  }

  Future<void> exportLog() => _platform.exportLog(formattedLog);

  void clearLog() {
    detections.clear();
    mediaTransferObservations.clear();
    notifyListeners();
  }

  String get formattedLog {
    final header =
        'Smartglass Detector event log\n'
        'Wireless smartglass and image/video activity detections.\n';
    final bleEntries = detections.reversed.map((event) => event.toLogLine());
    final mediaEntries = mediaTransferObservations.reversed.map(
      (observation) => observation.toLogLine(),
    );
    final sections = <String>[
      if (bleEntries.isNotEmpty)
        'BLE detections\n\n${bleEntries.join('\n\n---\n\n')}',
      if (mediaEntries.isNotEmpty)
        'Image/video activity\n\n${mediaEntries.join('\n\n---\n\n')}',
    ];
    return '$header\n${sections.join('\n\n=====\n\n')}';
  }

  void _applyAppState(Map<Object?, Object?> appState) {
    bleSupported = appState['bleSupported'] as bool? ?? false;
    bluetoothEnabled = appState['bluetoothEnabled'] as bool? ?? false;
    permissionsGranted = appState['permissionsGranted'] as bool? ?? false;
    notificationPermissionGranted =
        appState['notificationPermissionGranted'] as bool? ?? true;
    wifiDiscoveryPermissionGranted =
        appState['wifiDiscoveryPermissionGranted'] as bool? ?? false;
    _replaceNearbyDevices(appState['nearbyDevices']);
    _replaceRecentDevices(appState['recentDevices']);
    _replaceMediaTransferCandidates(appState['mediaTransferCandidates']);
    _replaceWifiDiscoveryState(appState['wifiDiscoveryState']);
    final scanning = appState['scanning'] as bool? ?? false;
    state = !bleSupported
        ? DetectorState.unsupported
        : !permissionsGranted
        ? DetectorState.permissionsRequired
        : !bluetoothEnabled
        ? DetectorState.bluetoothDisabled
        : scanning
        ? DetectorState.scanning
        : DetectorState.stopped;
    errorMessage = null;
  }

  void _applyOnboardingState(Map<Object?, Object?> value) {
    onboardingState = OnboardingState.fromMap(value);
    permissionsGranted = onboardingState.corePermissionsGranted;
    permanentlyDenied = onboardingState.corePermanentlyDenied;
    wifiDiscoveryPermissionGranted = onboardingState.wifiPermissionGranted;
    wifiPermissionPermanentlyDenied =
        onboardingState.wifiPermissionPermanentlyDenied;
    notificationPermissionGranted =
        onboardingState.notificationPermissionGranted;
    notificationPermanentlyDenied =
        onboardingState.notificationPermanentlyDenied;
  }

  void _handlePlatformEvent(Map<Object?, Object?> event) {
    switch (event['type']) {
      case 'scanState':
        _handleScanState(event);
      case 'bluetoothState':
        _handleBluetoothState(event['enabled'] as bool? ?? false);
      case 'nearbyDevices':
        _replaceNearbyDevices(event['devices']);
        notifyListeners();
      case 'recentDevices':
        _replaceRecentDevices(event['devices']);
        notifyListeners();
      case 'mediaTransferCandidates':
        _replaceMediaTransferCandidates(event['candidates']);
        notifyListeners();
      case 'mediaTransferObservation':
        _handleMediaTransferObservation(
          MediaTransferObservation.fromMap(event),
        );
      case 'wifiDiscoveryState':
        _replaceWifiDiscoveryState(event['state']);
        notifyListeners();
      case 'detection':
        _handleDetection(DetectionEvent.fromMap(event));
      case 'debug':
        if (settings.debugEnabled) {
          final message = event['message'] as String?;
          if (message != null) {
            debugMessages.insert(0, message);
            if (debugMessages.length > 200) {
              debugMessages.removeRange(200, debugMessages.length);
            }
            notifyListeners();
          }
        }
    }
  }

  void _handleScanState(Map<Object?, Object?> event) {
    switch (event['state']) {
      case 'stopped':
        state = DetectorState.stopped;
        errorMessage = null;
      case 'starting':
        state = DetectorState.starting;
        bluetoothEnabled = true;
        errorMessage = null;
      case 'scanning':
        state = DetectorState.scanning;
        bluetoothEnabled = true;
        errorMessage = null;
      case 'stopping':
        state = DetectorState.stopping;
        errorMessage = null;
      case 'error':
        if ((event['errorCode'] as num?)?.toInt() == -101) {
          bluetoothEnabled = false;
          state = DetectorState.bluetoothDisabled;
          errorMessage = null;
        } else {
          state = DetectorState.error;
          errorMessage =
              event['message'] as String? ?? 'Bluetooth scan failed.';
        }
    }
    notifyListeners();
  }

  void _handleBluetoothState(bool enabled) {
    bluetoothEnabled = enabled;
    if (!bleSupported) {
      state = DetectorState.unsupported;
    } else if (!permissionsGranted) {
      state = DetectorState.permissionsRequired;
    } else if (!enabled) {
      state = DetectorState.bluetoothDisabled;
      errorMessage = null;
    } else if (state == DetectorState.bluetoothDisabled ||
        errorMessage?.toLowerCase().contains('bluetooth is disabled') == true) {
      state = DetectorState.stopped;
      errorMessage = null;
    }
    notifyListeners();
  }

  void _handleDetection(DetectionEvent event) {
    lastDetection = event;
    if (settings.loggingEnabled) {
      detections.add(event);
      if (detections.length > 200) {
        detections.removeRange(0, detections.length - 200);
      }
    }
    notifyListeners();
  }

  void _replaceNearbyDevices(Object? value) {
    final list = value as List<Object?>? ?? const <Object?>[];
    nearbyDevices =
        list
            .whereType<Map<Object?, Object?>>()
            .map(NearbyDevice.fromMap)
            .toList(growable: false)
          ..sort((a, b) => a.distanceMeters.compareTo(b.distanceMeters));
  }

  void _replaceRecentDevices(Object? value) {
    final list = value as List<Object?>? ?? const <Object?>[];
    recentDevices =
        list
            .whereType<Map<Object?, Object?>>()
            .map(NearbyDevice.fromMap)
            .toList(growable: false)
          ..sort((a, b) {
            final byLastSeen = b.lastSeenMs.compareTo(a.lastSeenMs);
            return byLastSeen != 0
                ? byLastSeen
                : a.deviceId.compareTo(b.deviceId);
          });
  }

  void _replaceMediaTransferCandidates(Object? value) {
    final list = value as List<Object?>? ?? const <Object?>[];
    mediaTransferCandidates =
        list
            .whereType<Map<Object?, Object?>>()
            .map(MediaTransferCandidate.fromMap)
            .toList(growable: false)
          ..sort((a, b) {
            final byLastSeen = b.lastSeenMs.compareTo(a.lastSeenMs);
            return byLastSeen != 0
                ? byLastSeen
                : a.sessionId.compareTo(b.sessionId);
          });
  }

  void _replaceWifiDiscoveryState(Object? value) {
    final map = value as Map<Object?, Object?>?;
    wifiDiscoveryState = map == null
        ? const WifiDiscoveryState.inactive()
        : WifiDiscoveryState.fromMap(map);
  }

  void _handleMediaTransferObservation(MediaTransferObservation observation) {
    if (settings.loggingEnabled) {
      mediaTransferObservations.add(observation);
      if (mediaTransferObservations.length > 200) {
        mediaTransferObservations.removeRange(
          0,
          mediaTransferObservations.length - 200,
        );
      }
    }
    notifyListeners();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }
}
