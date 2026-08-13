import 'dart:async';

import 'package:flutter/material.dart';

import '../controllers/detector_controller.dart';
import '../models/media_transfer_candidate.dart';
import '../models/nearby_device.dart';
import '../models/wifi_discovery_state.dart';
import '../theme/app_theme.dart';
import '../widgets/app_components.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    super.key,
    required this.controller,
    this.showMediaTransferExplainer = true,
  });

  final DetectorController controller;
  final bool showMediaTransferExplainer;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedTab = 0;
  Timer? _relativeTimeTimer;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_refresh);
    _relativeTimeTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted &&
          (widget.controller.recentDevices.isNotEmpty ||
              widget.controller.mediaTransferCandidates.isNotEmpty)) {
        setState(() {});
      }
    });
  }

  @override
  void dispose() {
    _relativeTimeTimer?.cancel();
    widget.controller.removeListener(_refresh);
    super.dispose();
  }

  void _refresh() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    return Scaffold(
      appBar: AppBar(
        centerTitle: true,
        toolbarHeight: _selectedTab == 2 ? 72 : null,
        title: Text(
          switch (_selectedTab) {
            0 => 'Smartglass Detector',
            1 => 'Nearby smartglasses',
            _ => 'Nearby Image/Video Activity',
          },
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
        ),
        actions: [
          IconButton(
            tooltip: 'Settings',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => SettingsScreen(controller: controller),
              ),
            ),
            icon: const Icon(Icons.settings_outlined),
          ),
        ],
      ),
      body: SafeArea(
        child: IndexedStack(
          index: _selectedTab,
          children: [
            _HomeTab(controller: controller),
            _NearbySmartglassesTab(controller: controller),
            _MediaTransfersTab(
              controller: controller,
              showExplainer: widget.showMediaTransferExplainer,
            ),
          ],
        ),
      ),
      bottomNavigationBar: _DetectorBottomNavigationBar(
        selectedIndex: _selectedTab,
        onSelected: (index) => setState(() => _selectedTab = index),
      ),
    );
  }
}

class _DetectorBottomNavigationBar extends StatelessWidget {
  const _DetectorBottomNavigationBar({
    required this.selectedIndex,
    required this.onSelected,
  });

  final int selectedIndex;
  final ValueChanged<int> onSelected;

  static const _destinations = <_BottomDestinationData>[
    _BottomDestinationData(
      label: 'Home',
      semanticLabel: 'Home',
      icon: Icons.home_outlined,
      selectedIcon: Icons.home,
    ),
    _BottomDestinationData(
      label: 'Nearby\nSmartglasses',
      semanticLabel: 'Nearby Smartglasses',
      icon: Icons.visibility_outlined,
      selectedIcon: Icons.visibility,
    ),
    _BottomDestinationData(
      label: 'Nearby Image/\nVideo Activity',
      semanticLabel: 'Nearby Image/Video Activity',
      icon: Icons.wifi_tethering_outlined,
      selectedIcon: Icons.wifi_tethering,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final appColors = context.appColors;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: appColors.surface,
        border: Border(top: BorderSide(color: appColors.border)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(
              alpha: Theme.of(context).brightness == Brightness.dark
                  ? 0.28
                  : 0.08,
            ),
            blurRadius: 20,
            offset: const Offset(0, -6),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 96,
          child: Row(
            children: List.generate(_destinations.length, (index) {
              final destination = _destinations[index];
              final selected = selectedIndex == index;
              return Expanded(
                child: Semantics(
                  button: true,
                  selected: selected,
                  label: destination.semanticLabel,
                  child: Tooltip(
                    message: destination.semanticLabel,
                    child: InkWell(
                      key: ValueKey<String>('bottom-tab-$index'),
                      onTap: () => onSelected(index),
                      child: ExcludeSemantics(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 4,
                            vertical: 7,
                          ),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                width: 64,
                                height: 32,
                                decoration: BoxDecoration(
                                  gradient: selected
                                      ? LinearGradient(
                                          colors: [
                                            appColors.primaryStart,
                                            appColors.primaryEnd,
                                          ],
                                        )
                                      : null,
                                  borderRadius: BorderRadius.circular(20),
                                ),
                                alignment: Alignment.center,
                                child: Icon(
                                  selected
                                      ? destination.selectedIcon
                                      : destination.icon,
                                  color: selected
                                      ? appColors.onPrimary
                                      : appColors.textSecondary,
                                ),
                              ),
                              const SizedBox(height: 3),
                              SizedBox(
                                height: 38,
                                child: Center(
                                  child: FittedBox(
                                    fit: BoxFit.scaleDown,
                                    child: Text(
                                      destination.label,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                      textAlign: TextAlign.center,
                                      style: Theme.of(context)
                                          .textTheme
                                          .labelMedium
                                          ?.copyWith(
                                            color: selected
                                                ? appColors.textPrimary
                                                : appColors.textSecondary,
                                          ),
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              );
            }),
          ),
        ),
      ),
    );
  }
}

class _BottomDestinationData {
  const _BottomDestinationData({
    required this.label,
    required this.semanticLabel,
    required this.icon,
    required this.selectedIcon,
  });

  final String label;
  final String semanticLabel;
  final IconData icon;
  final IconData selectedIcon;
}

class _RecentSmartglassDetection {
  const _RecentSmartglassDetection._({this.device, this.mediaCandidate})
    : assert((device == null) != (mediaCandidate == null));

  factory _RecentSmartglassDetection.bluetooth(NearbyDevice device) =>
      _RecentSmartglassDetection._(device: device);

  factory _RecentSmartglassDetection.wifi(MediaTransferCandidate candidate) =>
      _RecentSmartglassDetection._(mediaCandidate: candidate);

  final NearbyDevice? device;
  final MediaTransferCandidate? mediaCandidate;

  int get lastSeenMs => device?.lastSeenMs ?? mediaCandidate!.lastSeenMs;

  String get stableKey => device?.deviceId ?? mediaCandidate!.sessionId;
}

List<_RecentSmartglassDetection> _combinedRecentDetections(
  DetectorController controller,
) {
  final detections = <_RecentSmartglassDetection>[
    ...controller.recentDevices.map(_RecentSmartglassDetection.bluetooth),
    ...controller.mediaTransferCandidates.map(_RecentSmartglassDetection.wifi),
  ];
  detections.sort((a, b) {
    final byLastSeen = b.lastSeenMs.compareTo(a.lastSeenMs);
    return byLastSeen != 0 ? byLastSeen : a.stableKey.compareTo(b.stableKey);
  });
  return detections;
}

class _HomeTab extends StatelessWidget {
  const _HomeTab({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) {
    final recentDetections = _combinedRecentDetections(
      controller,
    ).take(5).toList(growable: false);
    return ListView(
      key: const PageStorageKey<String>('home-tab'),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        _StatusCard(controller: controller),
        const SizedBox(height: 16),
        _ScanControl(controller: controller),
        if (_needsGuidance(controller)) ...[
          const SizedBox(height: 12),
          _GuidanceCard(controller: controller),
        ],
        if (controller.isScanning &&
            controller.settings.alertsEnabled &&
            !controller.notificationPermissionGranted) ...[
          const SizedBox(height: 12),
          _NotificationGuidance(controller: controller),
        ],
        if (controller.errorMessage != null) ...[
          const SizedBox(height: 12),
          _ErrorCard(message: controller.errorMessage!),
        ],
        const SizedBox(height: 24),
        _RecentDetectionsSection(
          detections: recentDetections,
          observationIntervalSeconds:
              controller.settings.wifiScanIntervalSeconds,
        ),
        if (controller.settings.debugEnabled) ...[
          const SizedBox(height: 24),
          _DebugPanel(messages: controller.debugMessages),
        ],
      ],
    );
  }
}

class _NearbySmartglassesTab extends StatelessWidget {
  const _NearbySmartglassesTab({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) {
    final detections = _combinedRecentDetections(controller);
    return ListView(
      key: const PageStorageKey<String>('nearby-smartglasses-tab'),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        _NearbyCountCard(
          count: detections.length,
          enabled: controller.isScanning,
        ),
        const SizedBox(height: 24),
        const AppSectionHeading(
          title: 'Detection logs',
          subtitle:
              'Bluetooth devices and Wi-Fi activity detections from the last 5 minutes, newest first.',
        ),
        const SizedBox(height: 12),
        if (detections.isEmpty)
          const _EmptyDetectionsCard()
        else
          ...detections.map(
            (detection) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _SmartglassDetectionCard(
                detection: detection,
                observationIntervalSeconds:
                    controller.settings.wifiScanIntervalSeconds,
              ),
            ),
          ),
      ],
    );
  }
}

class _MediaTransfersTab extends StatelessWidget {
  const _MediaTransfersTab({
    required this.controller,
    required this.showExplainer,
  });

  final DetectorController controller;
  final bool showExplainer;

  @override
  Widget build(BuildContext context) {
    final candidates = controller.mediaTransferCandidates;
    final discoveryActive =
        controller.isScanning &&
        controller.wifiDiscoveryState.status == WifiDiscoveryStatus.discovering;
    return ListView(
      key: const PageStorageKey<String>('media-transfers-tab'),
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        _MediaTransferCountCard(
          count: candidates.length,
          enabled: discoveryActive,
        ),
        const SizedBox(height: 12),
        _WifiDiscoveryStatusCard(controller: controller),
        if (showExplainer) ...[
          const SizedBox(height: 12),
          const _MediaTransferDisclaimer(),
        ],
        const SizedBox(height: 24),
        const AppSectionHeading(
          title: 'Recent image/video transfers',
          subtitle:
              'Active and completed transfers detected within the last 5 minutes.',
        ),
        const SizedBox(height: 12),
        if (candidates.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(18),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  AppIconTile(icon: Icons.wifi_find, tone: AppTone.neutral),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'No image/video transfers detected in the last 5 minutes.',
                    ),
                  ),
                ],
              ),
            ),
          )
        else
          ...candidates.map(
            (candidate) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _MediaTransferCandidateCard(
                candidate: candidate,
                observationIntervalSeconds:
                    controller.settings.wifiScanIntervalSeconds,
              ),
            ),
          ),
      ],
    );
  }
}

class _MediaTransferCountCard extends StatelessWidget {
  const _MediaTransferCountCard({required this.count, required this.enabled});

  final int count;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final tone = !enabled
        ? AppTone.neutral
        : count == 0
        ? AppTone.safe
        : AppTone.danger;
    final palette = context.appColors.paletteFor(tone);
    return AppGradientPanel(
      key: const ValueKey<String>('media-count-hero'),
      tone: tone,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 26),
      child: Column(
        children: [
          Icon(Icons.wifi_tethering, size: 42, color: palette.foreground),
          const SizedBox(height: 8),
          Text(
            '$count',
            style: Theme.of(
              context,
            ).textTheme.displayLarge?.copyWith(color: palette.foreground),
          ),
          const SizedBox(height: 8),
          Text(
            count == 1
                ? 'image/video transfer detected'
                : 'image/video transfers detected',
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(color: palette.foreground),
          ),
          const SizedBox(height: 4),
          Text(
            'within the last 5 minutes',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: palette.foreground.withValues(alpha: 0.82),
            ),
          ),
        ],
      ),
    );
  }
}

class _WifiDiscoveryStatusCard extends StatelessWidget {
  const _WifiDiscoveryStatusCard({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) {
    final state = controller.wifiDiscoveryState;
    final presentation = _wifiPresentation(state, controller.isScanning);
    final palette = context.appColors.paletteFor(presentation.tone);
    return AppGradientPanel(
      tone: presentation.tone,
      solid: true,
      radius: 20,
      padding: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppIconTile(icon: presentation.icon, tone: presentation.tone),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  presentation.title,
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(color: palette.foreground),
                ),
                const SizedBox(height: 3),
                Text(
                  state.message ?? presentation.message,
                  style: TextStyle(
                    color: palette.foreground.withValues(alpha: 0.9),
                  ),
                ),
                if (state.status == WifiDiscoveryStatus.permissionRequired) ...[
                  const SizedBox(height: 6),
                  TextButton.icon(
                    onPressed: controller.wifiPermissionPermanentlyDenied
                        ? controller.openAppSettings
                        : controller.requestWifiDiscoveryPermission,
                    icon: Icon(
                      controller.wifiPermissionPermanentlyDenied
                          ? Icons.open_in_new
                          : Icons.wifi_find,
                    ),
                    label: Text(
                      controller.wifiPermissionPermanentlyDenied
                          ? 'Open app settings'
                          : 'Allow Nearby Wi-Fi',
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  _WifiStatusPresentation _wifiPresentation(
    WifiDiscoveryState state,
    bool isScanning,
  ) {
    return switch (state.status) {
      WifiDiscoveryStatus.discovering => const _WifiStatusPresentation(
        title: 'Image/video activity detection active',
        message: 'Monitoring Wi-Fi Direct and nearby Wi-Fi network activity.',
        icon: Icons.wifi_find,
        tone: AppTone.safe,
      ),
      WifiDiscoveryStatus.permissionRequired => const _WifiStatusPresentation(
        title: 'Nearby Wi-Fi permission required',
        message: 'BLE scanning remains available without this permission.',
        icon: Icons.lock_outline,
        tone: AppTone.warning,
      ),
      WifiDiscoveryStatus.wifiDisabled => const _WifiStatusPresentation(
        title: 'Wi-Fi discovery unavailable',
        message: 'Turn on Wi-Fi; BLE scanning continues independently.',
        icon: Icons.wifi_off,
        tone: AppTone.warning,
      ),
      WifiDiscoveryStatus.locationDisabled => const _WifiStatusPresentation(
        title: 'Location services required by Android',
        message: 'Turn on Location services for Wi-Fi Direct discovery.',
        icon: Icons.location_off_outlined,
        tone: AppTone.warning,
      ),
      WifiDiscoveryStatus.unsupported => const _WifiStatusPresentation(
        title: 'Wi-Fi Direct unsupported',
        message: 'This phone cannot run peer discovery; BLE still works.',
        icon: Icons.phonelink_erase,
        tone: AppTone.danger,
      ),
      WifiDiscoveryStatus.error => const _WifiStatusPresentation(
        title: 'Wi-Fi discovery needs attention',
        message: 'Android reported a discovery error. The app will retry.',
        icon: Icons.error_outline,
        tone: AppTone.danger,
      ),
      WifiDiscoveryStatus.inactive => _WifiStatusPresentation(
        title: isScanning ? 'Wi-Fi discovery starting' : 'Wi-Fi discovery off',
        message: isScanning
            ? 'Waiting for Android’s Wi-Fi Direct discovery service.'
            : 'Press Start on Home to enable BLE and Wi-Fi discovery.',
        icon: isScanning ? Icons.hourglass_top : Icons.wifi_tethering_off,
        tone: isScanning ? AppTone.info : AppTone.neutral,
      ),
    };
  }
}

class _MediaTransferDisclaimer extends StatelessWidget {
  const _MediaTransferDisclaimer();

  @override
  Widget build(BuildContext context) => const AppGradientPanel(
    tone: AppTone.info,
    solid: true,
    radius: 20,
    padding: EdgeInsets.all(16),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppIconTile(icon: Icons.science_outlined, tone: AppTone.info),
        SizedBox(width: 12),
        Expanded(
          child: Text(
            'This tab detects image/video transfer activity from nearby smartglasses. Transferred media may have been recorded during the activity; the app does not observe the exact capture time.',
          ),
        ),
      ],
    ),
  );
}

class _MediaTransferCandidateCard extends StatelessWidget {
  const _MediaTransferCandidateCard({
    required this.candidate,
    required this.observationIntervalSeconds,
  });

  final MediaTransferCandidate candidate;
  final int observationIntervalSeconds;

  @override
  Widget build(BuildContext context) => Card(
    key: ValueKey<String>('media-transfer-${candidate.sessionId}'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              AppIconTile(
                icon: candidate.active ? Icons.wifi_tethering : Icons.history,
                tone: candidate.active ? AppTone.danger : AppTone.neutral,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      candidate.observedName,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const Text(
                      'Image/video transfer detected from nearby smartglasses',
                    ),
                  ],
                ),
              ),
              Chip(
                backgroundColor: candidate.active
                    ? context.appColors.danger.start
                    : context.appColors.surfaceAlt,
                side: BorderSide(
                  color: candidate.active
                      ? context.appColors.danger.border
                      : context.appColors.border,
                ),
                label: Text(
                  candidate.active ? 'Active' : 'Completed',
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: candidate.active
                        ? context.appColors.danger.foreground
                        : context.appColors.textSecondary,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Text(
            'The transferred media may have been recorded during this activity.',
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 16,
            runSpacing: 10,
            children: [
              if (candidate.rssi != null)
                _DeviceFact(
                  icon: Icons.network_cell,
                  label: '${candidate.rssi} dBm',
                ),
            ],
          ),
          if (candidate.rssi != null) const SizedBox(height: 12),
          _WifiTransferDetails(
            candidate: candidate,
            observationIntervalSeconds: observationIntervalSeconds,
          ),
          const SizedBox(height: 12),
          Text(
            'Sources: ${candidate.sources.map(_formatWifiSource).join(', ')}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          ...candidate.evidence.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Padding(
                    padding: EdgeInsets.only(top: 5),
                    child: Icon(Icons.circle, size: 6),
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: Text(item)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          const Divider(),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerRight,
            child: Text(
              'Last Detected: ${_formatTimeAgo(candidate.lastSeenMs)}',
              key: ValueKey<String>(
                'media-last-detected-${candidate.sessionId}',
              ),
              textAlign: TextAlign.right,
              style: Theme.of(context).textTheme.labelMedium,
            ),
          ),
        ],
      ),
    ),
  );
}

class _WifiStatusPresentation {
  const _WifiStatusPresentation({
    required this.title,
    required this.message,
    required this.icon,
    required this.tone,
  });

  final String title;
  final String message;
  final IconData icon;
  final AppTone tone;
}

bool _needsGuidance(DetectorController controller) =>
    controller.state == DetectorState.permissionsRequired ||
    controller.state == DetectorState.bluetoothDisabled ||
    controller.state == DetectorState.unsupported;

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) {
    final presentation = _statusPresentation(controller);
    final palette = context.appColors.paletteFor(presentation.tone);
    return AppGradientPanel(
      key: const ValueKey<String>('home-status-hero'),
      tone: presentation.tone,
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          Container(
            width: 84,
            height: 84,
            decoration: BoxDecoration(
              color: palette.foreground.withValues(alpha: 0.1),
              shape: BoxShape.circle,
              border: Border.all(
                color: palette.foreground.withValues(alpha: 0.18),
              ),
            ),
            alignment: Alignment.center,
            child: Icon(presentation.icon, size: 48, color: palette.foreground),
          ),
          const SizedBox(height: 14),
          Text(
            presentation.title,
            textAlign: TextAlign.center,
            style: Theme.of(
              context,
            ).textTheme.headlineSmall?.copyWith(color: palette.foreground),
          ),
          const SizedBox(height: 6),
          Text(
            presentation.subtitle,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: palette.foreground.withValues(alpha: 0.86),
            ),
          ),
          if (controller.isScanning) ...[
            const SizedBox(height: 14),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 8,
              runSpacing: 8,
              children: [
                _StatusChip(
                  icon: Icons.phone_android_outlined,
                  label: 'Background active',
                  color: palette.foreground,
                ),
                _StatusChip(
                  icon: Icons.notifications_active_outlined,
                  label: controller.settings.alertsEnabled
                      ? 'Alerts ≥ ${controller.settings.alertThresholdRssi} dBm'
                      : 'Alerts off',
                  color: palette.foreground,
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  _StatusPresentation _statusPresentation(DetectorController controller) {
    if (controller.isScanning) {
      final activeMediaCount = controller.activeMediaTransferCount;
      final count = controller.nearbyDevices.length + activeMediaCount;
      return _StatusPresentation(
        title: 'Scanning nearby',
        subtitle: activeMediaCount > 0
            ? 'Smartglass detected nearby; also possibly took a photo/video recently.'
            : count == 0
            ? 'No smartglass signals detected.'
            : count == 1
            ? '1 smartglass signal detected.'
            : '$count smartglass signals detected.',
        icon: count == 0 ? Icons.radar : Icons.visibility_outlined,
        tone: count == 0 ? AppTone.safe : AppTone.danger,
      );
    }
    return switch (controller.state) {
      DetectorState.starting => _StatusPresentation(
        title: 'Starting scanner',
        subtitle: 'Preparing passive Bluetooth Low Energy scanning.',
        icon: Icons.bluetooth_searching,
        tone: AppTone.info,
      ),
      DetectorState.stopping => _StatusPresentation(
        title: 'Stopping scanner',
        subtitle: 'Closing the active BLE scan.',
        icon: Icons.hourglass_bottom,
        tone: AppTone.info,
      ),
      DetectorState.permissionsRequired => _StatusPresentation(
        title: 'Permission required',
        subtitle:
            'Nearby-device and location access are required for scanning.',
        icon: Icons.lock_outline,
        tone: AppTone.warning,
      ),
      DetectorState.bluetoothDisabled => _StatusPresentation(
        title: 'Bluetooth is off',
        subtitle: 'Turn on Bluetooth to receive nearby advertisements.',
        icon: Icons.bluetooth_disabled,
        tone: AppTone.warning,
      ),
      DetectorState.unsupported => _StatusPresentation(
        title: 'BLE is unavailable',
        subtitle: 'This phone does not provide the required BLE scanner.',
        icon: Icons.phonelink_erase,
        tone: AppTone.danger,
      ),
      DetectorState.error => _StatusPresentation(
        title: 'Scanner needs attention',
        subtitle: 'Review the error below, then try starting again.',
        icon: Icons.error_outline,
        tone: AppTone.danger,
      ),
      DetectorState.stopped || DetectorState.scanning => _StatusPresentation(
        title: 'Scanner is off',
        subtitle:
            'Start once to keep scanning in the background until you stop it.',
        icon: Icons.shield_moon_outlined,
        tone: AppTone.neutral,
      ),
    };
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
    decoration: BoxDecoration(
      color: color.withValues(alpha: 0.1),
      borderRadius: BorderRadius.circular(100),
      border: Border.all(color: color.withValues(alpha: 0.25)),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.labelMedium?.copyWith(color: color),
          ),
        ),
      ],
    ),
  );
}

class _ScanControl extends StatelessWidget {
  const _ScanControl({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) {
    final active =
        controller.isScanning || controller.state == DetectorState.starting;
    return SizedBox(
      width: double.infinity,
      height: 58,
      child: active
          ? FilledButton.tonalIcon(
              onPressed: controller.state == DetectorState.stopping
                  ? null
                  : controller.stop,
              icon: const Icon(Icons.stop_circle_outlined),
              label: const Text('Stop scanning'),
            )
          : FilledButton.icon(
              onPressed: controller.isBusy ? null : controller.start,
              icon: controller.isBusy
                  ? const SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.bluetooth_searching),
              label: const Text('Start scanning'),
            ),
    );
  }
}

class _RecentDetectionsSection extends StatelessWidget {
  const _RecentDetectionsSection({
    required this.detections,
    required this.observationIntervalSeconds,
  });

  final List<_RecentSmartglassDetection> detections;
  final int observationIntervalSeconds;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const AppSectionHeading(
        title: 'Most Recent Detections',
        subtitle:
            'Up to 5 Bluetooth or Wi-Fi activity detections, newest first.',
      ),
      const SizedBox(height: 12),
      if (detections.isEmpty)
        const _EmptyDetectionsCard()
      else
        ...detections.map(
          (detection) => Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _SmartglassDetectionCard(
              detection: detection,
              observationIntervalSeconds: observationIntervalSeconds,
            ),
          ),
        ),
    ],
  );
}

class _SmartglassDetectionCard extends StatelessWidget {
  const _SmartglassDetectionCard({
    required this.detection,
    required this.observationIntervalSeconds,
  });

  final _RecentSmartglassDetection detection;
  final int observationIntervalSeconds;

  @override
  Widget build(BuildContext context) {
    final device = detection.device;
    if (device != null) {
      return _RecentDeviceCard(device: device);
    }
    return _WifiSmartglassDetectionCard(
      candidate: detection.mediaCandidate!,
      observationIntervalSeconds: observationIntervalSeconds,
    );
  }
}

class _WifiSmartglassDetectionCard extends StatelessWidget {
  const _WifiSmartglassDetectionCard({
    required this.candidate,
    required this.observationIntervalSeconds,
  });

  final MediaTransferCandidate candidate;
  final int observationIntervalSeconds;

  @override
  Widget build(BuildContext context) => Card(
    key: ValueKey<String>('wifi-smartglass-${candidate.sessionId}'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              AppIconTile(
                icon: candidate.active ? Icons.wifi_tethering : Icons.history,
                tone: candidate.active ? AppTone.danger : AppTone.info,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Smartglass detected',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const Text(
                      'Also transmitted image/video to their device- possibly recorded right now',
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          _WifiTransferDetails(
            candidate: candidate,
            observationIntervalSeconds: observationIntervalSeconds,
          ),
          const SizedBox(height: 14),
          const Divider(),
          const SizedBox(height: 10),
          LayoutBuilder(
            builder: (context, constraints) {
              final source = _WifiDetectionSource(candidate: candidate);
              final lastDetected = Text(
                'Last Detected: ${_formatTimeAgo(candidate.lastSeenMs)}',
                textAlign: TextAlign.right,
                style: Theme.of(context).textTheme.labelMedium,
              );
              if (constraints.maxWidth < 300) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    source,
                    const SizedBox(height: 10),
                    Align(
                      alignment: Alignment.centerRight,
                      child: lastDetected,
                    ),
                  ],
                );
              }
              return Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(child: source),
                  const SizedBox(width: 12),
                  Flexible(child: lastDetected),
                ],
              );
            },
          ),
        ],
      ),
    ),
  );
}

class _WifiDetectionSource extends StatelessWidget {
  const _WifiDetectionSource({required this.candidate});

  final MediaTransferCandidate candidate;

  @override
  Widget build(BuildContext context) {
    final formattedSources = candidate.sources.isEmpty
        ? 'Not reported'
        : candidate.sources.map(_formatWifiSource).join(', ');
    return Semantics(
      label: 'Detected via $formattedSources, SSID ${candidate.observedName}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Detected via: $formattedSources',
            style: Theme.of(context).textTheme.labelMedium,
          ),
          const SizedBox(height: 3),
          Text(
            'SSID: ${candidate.observedName}',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              fontFamily: 'monospace',
              color: context.appColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}

class _WifiTransferDetails extends StatelessWidget {
  const _WifiTransferDetails({
    required this.candidate,
    required this.observationIntervalSeconds,
  });

  final MediaTransferCandidate candidate;
  final int observationIntervalSeconds;

  @override
  Widget build(BuildContext context) {
    final address = candidate.address?.trim();
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: context.appColors.surfaceAlt,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: context.appColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TechnicalDetailRow(
            label: 'Source address',
            value: address?.isNotEmpty == true ? address! : 'Not reported',
            monospace: address?.isNotEmpty == true,
          ),
          _TechnicalDetailRow(
            label: 'Wi-Fi channel',
            value: _formatWifiChannel(candidate),
          ),
          _TechnicalDetailRow(
            label: 'Observed duration',
            value: _formatDuration(
              candidate.durationMs,
              observationIntervalSeconds,
            ),
            isLast: true,
          ),
        ],
      ),
    );
  }
}

class _NearbyCountCard extends StatelessWidget {
  const _NearbyCountCard({required this.count, required this.enabled});

  final int count;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final tone = !enabled
        ? AppTone.neutral
        : count == 0
        ? AppTone.safe
        : AppTone.danger;
    final palette = context.appColors.paletteFor(tone);
    return AppGradientPanel(
      key: const ValueKey<String>('nearby-count-hero'),
      tone: tone,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
      child: Column(
        children: [
          Icon(Icons.radar, size: 42, color: palette.foreground),
          const SizedBox(height: 8),
          Text(
            '$count',
            style: Theme.of(
              context,
            ).textTheme.displayLarge?.copyWith(color: palette.foreground),
          ),
          const SizedBox(height: 8),
          Text(
            count == 1 ? 'smartglass detected' : 'smartglasses detected',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(color: palette.foreground),
          ),
          const SizedBox(height: 4),
          Text(
            'within the last 5 minutes',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: palette.foreground.withValues(alpha: 0.82),
            ),
          ),
        ],
      ),
    );
  }
}

class _RecentDeviceCard extends StatelessWidget {
  const _RecentDeviceCard({required this.device});

  final NearbyDevice device;

  @override
  Widget build(BuildContext context) => Card(
    key: ValueKey<String>('ble-smartglass-${device.deviceId}'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const AppIconTile(
                icon: Icons.visibility_outlined,
                tone: AppTone.info,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      device.displayName,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const Text('Smartglass detected'),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 18,
            runSpacing: 10,
            children: [
              _DeviceFact(
                icon: Icons.straighten,
                label:
                    'Estimated distance: ${_formatDistance(device.distanceMeters)}',
              ),
            ],
          ),
          const SizedBox(height: 12),
          _DeviceTechnicalDetails(device: device),
          const SizedBox(height: 10),
          Text(
            device.presentationReason,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 10),
          const Divider(),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerRight,
            child: Text(
              'Last Detected: ${_formatTimeAgo(device.lastSeenMs)}',
              key: ValueKey<String>('ble-last-detected-${device.deviceId}'),
              textAlign: TextAlign.right,
              style: Theme.of(context).textTheme.labelMedium,
            ),
          ),
        ],
      ),
    ),
  );
}

class _DeviceTechnicalDetails extends StatelessWidget {
  const _DeviceTechnicalDetails({required this.device});

  final NearbyDevice device;

  @override
  Widget build(BuildContext context) {
    final manufacturer = device.companyName ?? device.companyId;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: context.appColors.surfaceAlt,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: context.appColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TechnicalDetailRow(
            label: 'Manufacturer',
            value: manufacturer?.trim().isNotEmpty == true
                ? manufacturer!
                : 'Not advertised',
          ),
          _TechnicalDetailRow(
            label: 'Service UUID',
            value: device.serviceUuids.isEmpty
                ? 'Not advertised'
                : device.serviceUuids.join(', '),
            monospace: device.serviceUuids.isNotEmpty,
          ),
          _TechnicalDetailRow(
            label: 'Raw name',
            value: device.deviceName?.trim().isNotEmpty == true
                ? device.deviceName!
                : 'Not advertised',
          ),
          _TechnicalDetailRow(
            label: 'RSSI',
            value: '${device.rawRssi} dBm',
            isLast: true,
          ),
        ],
      ),
    );
  }
}

class _TechnicalDetailRow extends StatelessWidget {
  const _TechnicalDetailRow({
    required this.label,
    required this.value,
    this.monospace = false,
    this.isLast = false,
  });

  final String label;
  final String value;
  final bool monospace;
  final bool isLast;

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: isLast ? 0 : 7),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 96,
          child: Text(
            '$label:',
            style: Theme.of(context).textTheme.labelMedium,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            value,
            softWrap: true,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              fontFamily: monospace ? 'monospace' : null,
              color: context.appColors.textSecondary,
            ),
          ),
        ),
      ],
    ),
  );
}

class _DeviceFact extends StatelessWidget {
  const _DeviceFact({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
    decoration: BoxDecoration(
      color: context.appColors.surfaceAlt,
      borderRadius: BorderRadius.circular(12),
      border: Border.all(color: context.appColors.border),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: context.appColors.textSecondary),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelMedium,
          ),
        ),
      ],
    ),
  );
}

class _EmptyDetectionsCard extends StatelessWidget {
  const _EmptyDetectionsCard();

  @override
  Widget build(BuildContext context) => const Card(
    child: Padding(
      padding: EdgeInsets.all(18),
      child: Row(
        children: [
          AppIconTile(icon: Icons.bluetooth_searching, tone: AppTone.neutral),
          SizedBox(width: 12),
          Expanded(child: Text('No nearby smartglasses detected yet.')),
        ],
      ),
    ),
  );
}

class _GuidanceCard extends StatelessWidget {
  const _GuidanceCard({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) {
    final message = switch (controller.state) {
      DetectorState.permissionsRequired =>
        'Scanning needs Nearby devices and location access. Android 10–11 also require background location for continuous scanning.',
      DetectorState.bluetoothDisabled =>
        'The app will use Android’s Bluetooth enable prompt when you start again.',
      DetectorState.unsupported =>
        'Smartglass Detector requires a phone with Bluetooth Low Energy hardware.',
      _ => '',
    };
    return AppGradientPanel(
      tone: controller.state == DetectorState.unsupported
          ? AppTone.danger
          : AppTone.warning,
      solid: true,
      radius: 20,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(message),
          if (controller.state == DetectorState.permissionsRequired &&
              (controller.permanentlyDenied ||
                  controller
                      .onboardingState
                      .backgroundLocationSettingsRequired)) ...[
            const SizedBox(height: 10),
            TextButton.icon(
              onPressed: controller.openAppSettings,
              icon: const Icon(Icons.open_in_new),
              label: const Text('Open app settings'),
            ),
          ],
        ],
      ),
    );
  }
}

class _NotificationGuidance extends StatelessWidget {
  const _NotificationGuidance({required this.controller});

  final DetectorController controller;

  @override
  Widget build(BuildContext context) => AppGradientPanel(
    tone: AppTone.warning,
    solid: true,
    radius: 20,
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Scanning, sound, and vibration alerts remain active, but Android notification permission is off, so notification cards cannot be shown.',
        ),
        const SizedBox(height: 8),
        TextButton.icon(
          onPressed: controller.notificationPermanentlyDenied
              ? controller.openAppSettings
              : controller.requestNotificationPermission,
          icon: const Icon(Icons.notifications_outlined),
          label: Text(
            controller.notificationPermanentlyDenied
                ? 'Open app settings'
                : 'Allow notifications',
          ),
        ),
      ],
    ),
  );
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final palette = context.appColors.danger;
    return AppGradientPanel(
      tone: AppTone.danger,
      solid: true,
      radius: 20,
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          AppIconTile(icon: Icons.error_outline, tone: AppTone.danger),
          const SizedBox(width: 12),
          Expanded(
            child: Text(message, style: TextStyle(color: palette.foreground)),
          ),
        ],
      ),
    );
  }
}

class _DebugPanel extends StatelessWidget {
  const _DebugPanel({required this.messages});

  final List<String> messages;

  @override
  Widget build(BuildContext context) => Card(
    key: const ValueKey<String>('wireless-debug-panel'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const AppIconTile(
                icon: Icons.terminal_outlined,
                tone: AppTone.info,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Wireless debug activity',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Recent BLE and Wi-Fi scanner messages',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 5,
                ),
                decoration: BoxDecoration(
                  color: context.appColors.info.start,
                  borderRadius: BorderRadius.circular(100),
                  border: Border.all(color: context.appColors.info.border),
                ),
                child: Text(
                  '${messages.length}',
                  semanticsLabel: '${messages.length} debug messages',
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: context.appColors.info.foreground,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(),
          const SizedBox(height: 14),
          Container(
            key: const ValueKey<String>('wireless-debug-console'),
            height: messages.isEmpty ? 96 : 240,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              color: context.appColors.surfaceDeep,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: context.appColors.border),
            ),
            child: messages.isEmpty
                ? const Center(
                    child: Padding(
                      padding: EdgeInsets.all(14),
                      child: Text(
                        'Restart scanning to collect BLE and Wi-Fi debug activity.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Color(0xFFD1D5DB)),
                      ),
                    ),
                  )
                : Scrollbar(
                    child: ListView.separated(
                      primary: false,
                      padding: const EdgeInsets.all(12),
                      itemCount: messages.length,
                      separatorBuilder: (_, _) => const Padding(
                        padding: EdgeInsets.symmetric(vertical: 8),
                        child: Divider(color: Color(0xFF344054)),
                      ),
                      itemBuilder: (context, index) => SelectableText(
                        messages[index],
                        key: ValueKey<String>('debug-message-$index'),
                        textWidthBasis: TextWidthBasis.parent,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          fontFamily: 'monospace',
                          color: const Color(0xFFD1D5DB),
                        ),
                      ),
                    ),
                  ),
          ),
        ],
      ),
    ),
  );
}

class _StatusPresentation {
  const _StatusPresentation({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.tone,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final AppTone tone;
}

String _formatDistance(double meters) => switch (meters) {
  < 1 => '${(meters * 100).round()} cm',
  < 10 => '${meters.toStringAsFixed(1)} m',
  _ => '${meters.round()} m',
};

String _formatTimeAgo(int lastSeenMs) {
  final elapsedMs = DateTime.now().millisecondsSinceEpoch - lastSeenMs;
  final seconds = elapsedMs <= 0 ? 0 : elapsedMs ~/ 1000;
  if (seconds < 60) {
    return '$seconds ${seconds == 1 ? 'second' : 'seconds'} ago';
  }
  final minutes = seconds ~/ 60;
  return '$minutes ${minutes == 1 ? 'minute' : 'minutes'} ago';
}

String _formatDuration(int durationMs, int observationIntervalSeconds) {
  if (durationMs <= 0) {
    final interval = observationIntervalSeconds.clamp(1, 60);
    return 'Less than $interval ${interval == 1 ? 'second' : 'seconds'} observed';
  }
  final seconds = durationMs ~/ 1000;
  if (seconds == 0) {
    return 'Less than 1 second observed';
  }
  if (seconds < 60) {
    return '${seconds.clamp(0, 59)} ${seconds == 1 ? 'second' : 'seconds'} observed';
  }
  final minutes = seconds ~/ 60;
  return '$minutes ${minutes == 1 ? 'minute' : 'minutes'} observed';
}

String _formatWifiSource(String source) => switch (source) {
  'wifiP2p' => 'Wi-Fi Direct',
  'wifiScan' => 'Wi-Fi scan',
  _ => source,
};

String _formatWifiChannel(MediaTransferCandidate candidate) {
  final channel = candidate.channel;
  final frequency = candidate.frequencyMhz;
  if (channel != null && frequency != null) {
    return 'Channel $channel · $frequency MHz';
  }
  if (channel != null) {
    return 'Channel $channel';
  }
  if (frequency != null) {
    return 'Not reported by Android · $frequency MHz';
  }
  return 'Not reported by Android';
}
