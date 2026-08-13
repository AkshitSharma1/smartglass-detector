import 'package:flutter/material.dart';

import '../controllers/detector_controller.dart';
import '../models/app_settings.dart';
import '../theme/app_theme.dart';
import '../widgets/app_components.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({
    super.key,
    required this.controller,
    this.reviewMode = false,
  });

  final DetectorController controller;
  final bool reviewMode;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen>
    with WidgetsBindingObserver {
  static const _pageCount = 7;

  final PageController _pageController = PageController();
  int _page = 0;
  bool _busy = false;
  bool _manualThrottleConfirmation = false;

  DetectorController get controller => widget.controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    controller.addListener(_refresh);
    controller.refreshOnboardingState();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    controller.removeListener(_refresh);
    _pageController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      controller.refreshOnboardingState();
    }
  }

  void _refresh() {
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _goTo(int page) async {
    final target = page.clamp(0, _pageCount - 1).toInt();
    await _pageController.animateToPage(
      target,
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOut,
    );
  }

  Future<void> _primaryAction() async {
    if (_busy) return;
    switch (_page) {
      case 0:
        await _goTo(1);
        return;
      case 1:
        final setup = controller.onboardingState;
        if (setup.corePermissionsGranted) {
          await _goTo(2);
        } else if (setup.corePermanentlyDenied ||
            setup.backgroundLocationSettingsRequired) {
          await controller.openAppSettings();
        } else {
          await _runBusy(controller.requestCoreScanPermissions);
          if (controller.onboardingState.corePermissionsGranted) {
            await _goTo(2);
          }
        }
        return;
      case 2:
        final setup = controller.onboardingState;
        if (!setup.wifiRuntimePermissionRequired ||
            setup.wifiPermissionGranted) {
          await _goTo(3);
        } else if (setup.wifiPermissionPermanentlyDenied) {
          await controller.openAppSettings();
        } else {
          await _runBusy(controller.requestWifiDiscoveryPermission);
          if (controller.onboardingState.wifiPermissionGranted) {
            await _goTo(3);
          }
        }
        return;
      case 3:
        final setup = controller.onboardingState;
        if (!setup.notificationRuntimePermissionRequired ||
            setup.notificationPermissionGranted) {
          await _goTo(4);
        } else if (setup.notificationPermanentlyDenied) {
          await controller.openAppSettings();
        } else {
          await _runBusy(controller.requestNotificationPermission);
          if (controller.onboardingState.notificationPermissionGranted) {
            await _goTo(4);
          }
        }
        return;
      case 4:
        final setup = controller.onboardingState;
        if (setup.batteryOptimizationExempt) {
          await _goTo(5);
        } else {
          await _runBusy(controller.requestBatteryOptimizationExemption);
          if (controller.onboardingState.batteryOptimizationExempt) {
            await _goTo(5);
          }
        }
        return;
      case 5:
        if (_throttleStepSatisfied) {
          await _goTo(6);
        } else {
          await _showDeveloperOptionsGuidance();
        }
        return;
      case 6:
        await _finish();
        return;
    }
  }

  Future<void> _runBusy(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _finish() async {
    if (widget.reviewMode) {
      Navigator.of(context).pop();
      return;
    }
    setState(() => _busy = true);
    try {
      await controller.completeOnboarding();
      await controller.start();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  bool get _throttleStepSatisfied {
    final setup = controller.onboardingState;
    return setup.wifiScanThrottleQuerySupported
        ? setup.wifiScanThrottleDisabled
        : _manualThrottleConfirmation;
  }

  Future<void> _skipOptionalPage() async {
    if (_page == 4) {
      final skip = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Keep battery optimization on?'),
          content: const Text(
            'Background scanning might not work reliably while battery '
            'optimization remains enabled. Detections and alerts may be '
            'delayed or missed.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Continue setup'),
            ),
            FilledButton.tonal(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Skip anyway'),
            ),
          ],
        ),
      );
      if (skip != true) return;
    }
    if (!mounted) return;
    if (_page == 5) {
      final skip = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Leave Wi-Fi scan throttling on?'),
          content: const Text(
            'Wi-Fi scan throttling limits nearby network scans and reduces '
            'detection of brief image/video transfers. Bluetooth and '
            'Wi-Fi Direct discovery remain active.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Continue setup'),
            ),
            FilledButton.tonal(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Skip anyway'),
            ),
          ],
        ),
      );
      if (skip != true) return;
    }
    await _goTo(_page + 1);
  }

  Future<void> _showDeveloperOptionsGuidance() async {
    final choice = await showDialog<_DeveloperOptionsChoice>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Enable Developer Options first'),
        content: const Text(
          'Open About phone and find Build number. On some phones it is under '
          'Software information. Tap Build number seven times, confirm your '
          'device PIN, then return here.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Not now'),
          ),
          TextButton(
            onPressed: () =>
                Navigator.pop(context, _DeveloperOptionsChoice.aboutPhone),
            child: const Text('Open About phone'),
          ),
          FilledButton.tonal(
            onPressed: () => Navigator.pop(
              context,
              _DeveloperOptionsChoice.developerOptions,
            ),
            child: const Text('Already enabled'),
          ),
        ],
      ),
    );
    switch (choice) {
      case _DeveloperOptionsChoice.aboutPhone:
        await controller.openAboutPhone();
        return;
      case _DeveloperOptionsChoice.developerOptions:
        await controller.openDeveloperOptions();
        return;
      case null:
        return;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        leading: widget.reviewMode || _page > 0
            ? IconButton(
                tooltip: widget.reviewMode && _page == 0 ? 'Close' : 'Back',
                onPressed: _busy
                    ? null
                    : () {
                        if (_page > 0) {
                          _goTo(_page - 1);
                        } else {
                          Navigator.of(context).pop();
                        }
                      },
                icon: Icon(
                  widget.reviewMode && _page == 0
                      ? Icons.close
                      : Icons.arrow_back,
                ),
              )
            : null,
        title: Text(widget.reviewMode ? 'Setup guide' : 'First-time setup'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: Center(child: Text('${_page + 1}/$_pageCount')),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(5),
          child: LinearProgressIndicator(value: (_page + 1) / _pageCount),
        ),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: _pageController,
                physics: const NeverScrollableScrollPhysics(),
                onPageChanged: (page) => setState(() => _page = page),
                children: [
                  _welcomePage(),
                  _corePermissionPage(),
                  _wifiPermissionPage(),
                  _notificationPage(),
                  _batteryOptimizationPage(),
                  _developerOptionsPage(),
                  _readyPage(),
                ],
              ),
            ),
            _footer(),
          ],
        ),
      ),
    );
  }

  Widget _welcomePage() => const _SetupPage(
    icon: Icons.visibility_outlined,
    title: 'Find smartglasses nearby',
    intro:
        'Smartglass Detector monitors nearby wireless signals and keeps you '
        'informed.',
    children: [
      _InfoRow(
        icon: Icons.bluetooth_searching,
        title: 'Nearby smartglasses',
        body: 'See detected smartglasses and their estimated distance.',
      ),
      _InfoRow(
        icon: Icons.wifi_tethering,
        title: 'Nearby image/video activity',
        body: 'Track smartglass image/video transfer activity.',
      ),
      _InfoRow(
        icon: Icons.notifications_active_outlined,
        title: 'Background alerts',
        body: 'Keep scanning and receive your selected sound or vibration.',
      ),
      _Notice(
        text:
            'Smartglass detection analyzes public BLE '
            'advertisement packets for supported manufacturer IDs, service '
            'UUIDs, and device-name signatures. The scanner measures RSSI for '
            'each packet and uses advertised TX power, when available, to '
            'estimate distance. Image/video activity detection monitors Wi-Fi '
            'Direct peer records and nearby Wi-Fi scan results for the supported '
            'transfer identifier pattern; it does not reveal the exact capture '
            'time.',
      ),
    ],
  );

  Widget _corePermissionPage() {
    final setup = controller.onboardingState;
    return _SetupPage(
      icon: Icons.radar,
      title: 'Allow nearby scanning',
      intro:
          'These permissions are required before Smartglass Detector can scan.',
      children: [
        const _InfoRow(
          icon: Icons.bluetooth,
          title: 'Nearby devices and Bluetooth',
          body:
              'Read public BLE advertisement packets - including manufacturer '
              'data, service UUIDs, and advertised names - and scan '
              'metadata such as RSSI and TX power when available.',
        ),
        const _InfoRow(
          icon: Icons.location_on_outlined,
          title: 'Precise location access',
          body:
              'Android protects Bluetooth and Wi-Fi scan results as location '
              'data. RSSI and advertised TX power, when available, are used to '
              'estimate signal distance.',
        ),
        if (setup.backgroundLocationRequired)
          const _InfoRow(
            icon: Icons.phone_android_outlined,
            title: 'Background location',
            body:
                'Android 10–11 require this to continue scanning while the app '
                'is minimized or the screen is locked.',
          ),
        const _Notice(
          text:
              'The app does not read GPS coordinates, pair with observed '
              'devices, or upload observations.',
        ),
        _PermissionStatus(
          granted: setup.corePermissionsGranted,
          grantedText: 'Required scanning access is allowed',
          deniedText: setup.backgroundLocationSettingsRequired
              ? 'Open app settings and choose “Allow all the time” for Location.'
              : 'Required scanning access has not been granted',
        ),
      ],
    );
  }

  Widget _wifiPermissionPage() {
    final setup = controller.onboardingState;
    final available =
        !setup.wifiRuntimePermissionRequired || setup.wifiPermissionGranted;
    return _SetupPage(
      icon: Icons.wifi_find,
      title: 'Allow image/video activity detection',
      intro:
          'Nearby Wi-Fi access enables detection of smartglass image/video '
          'transfers.',
      children: [
        const _InfoRow(
          icon: Icons.manage_search,
          title: 'Activity signal',
          body:
              'The app checks Wi-Fi Direct peer records and nearby Wi-Fi '
              'network scan results for smartglass model-specific identifiers.',
        ),
        const _InfoRow(
          icon: Icons.link_off,
          title: 'Passive observation',
          body:
              'It never connects to, joins, or interrogates a discovered group.',
        ),
        const _Notice(
          text:
              'Image/video transfer activity does not report the exact moment '
              'a photo or video is captured.',
        ),
        _PermissionStatus(
          granted: available,
          grantedText: setup.wifiRuntimePermissionRequired
              ? 'Image/video activity detection is enabled'
              : 'This Android version needs no separate Nearby Wi-Fi prompt',
          deniedText: 'Image/video activity detection is currently off',
        ),
      ],
    );
  }

  Widget _notificationPage() {
    final setup = controller.onboardingState;
    final available =
        !setup.notificationRuntimePermissionRequired ||
        setup.notificationPermissionGranted;
    return _SetupPage(
      icon: Icons.notifications_active_outlined,
      title: 'Stay informed',
      intro:
          'Notifications show the ongoing scan and a card for eligible nearby '
          'detections.',
      children: [
        const _InfoRow(
          icon: Icons.notifications_outlined,
          title: 'Notification cards',
          body:
              'See scanner status and individual detection alerts outside the app.',
        ),
        const _InfoRow(
          icon: Icons.vibration,
          title: 'Sound and vibration continue',
          body:
              'If notification permission is denied, scanning and app-controlled '
              'sound or vibration still work.',
        ),
        const _Notice(
          text:
              'Android grants vibration, wake-lock, and foreground-service '
              'capabilities without separate permission dialogs.',
        ),
        _PermissionStatus(
          granted: available,
          grantedText: setup.notificationRuntimePermissionRequired
              ? 'Notifications are allowed'
              : 'This Android version needs no notification prompt',
          deniedText: 'Notification cards are optional and currently hidden',
        ),
      ],
    );
  }

  Widget _developerOptionsPage() {
    final setup = controller.onboardingState;
    return _SetupPage(
      icon: Icons.speed,
      title: 'Increase image/video activity scan frequency',
      intro:
          'Turn off Android’s Wi-Fi scan throttling on a dedicated detection '
          'phone.',
      children: [
        const _InstructionCard(
          steps: [
            'Open Developer Options.',
            'Find Networking.',
            'Turn “Wi-Fi scan throttling” off.',
            'Return to Smartglass Detector.',
          ],
        ),
        const _Notice(
          text:
              'Turning this setting off increases scan frequency and battery '
              'use. Bluetooth and Wi-Fi Direct discovery operate independently.',
        ),
        if (setup.wifiScanThrottleQuerySupported)
          _PermissionStatus(
            granted: setup.wifiScanThrottleDisabled,
            grantedText: 'Wi-Fi scan throttling is off',
            deniedText: 'Wi-Fi scan throttling is still on',
          )
        else
          CheckboxListTile(
            value: _manualThrottleConfirmation,
            onChanged: (value) =>
                setState(() => _manualThrottleConfirmation = value ?? false),
            contentPadding: EdgeInsets.zero,
            title: const Text('I turned Wi-Fi scan throttling off'),
            subtitle: const Text(
              'This Android version cannot report the setting back to the app.',
            ),
          ),
        const SizedBox(height: 8),
        Text(
          'Wi-Fi observation interval',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 4),
        const Text(
          'This controls both Wi-Fi Direct peer sampling and nearby-network '
          'scan requests. Android controls conventional scan acceptance and '
          'result freshness.',
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: AppSettings.supportedWifiScanIntervalsSeconds
              .map(
                (seconds) => ChoiceChip(
                  label: Text('$seconds seconds'),
                  selected:
                      controller.settings.wifiScanIntervalSeconds == seconds,
                  onSelected: (_) => controller.updateSettings(
                    controller.settings.copyWith(
                      wifiScanIntervalSeconds: seconds,
                    ),
                  ),
                ),
              )
              .toList(growable: false),
        ),
      ],
    );
  }

  Widget _batteryOptimizationPage() {
    final exempt = controller.onboardingState.batteryOptimizationExempt;
    return _SetupPage(
      icon: Icons.battery_saver_outlined,
      title: 'Allow reliable background scanning',
      intro:
          'Exclude Smartglass Detector from Android battery optimization so '
          'its scanner can keep working while the app is minimized.',
      children: [
        const _InfoRow(
          icon: Icons.phone_android_outlined,
          title: 'Background reliability',
          body:
              'Battery optimization may pause the app and delay or stop '
              'background detections and alerts.',
        ),
        const _Notice(
          text:
              'This step is optional. Keeping optimization enabled can reduce '
              'battery use, but background scanning might not work reliably.',
        ),
        _PermissionStatus(
          granted: exempt,
          grantedText: 'Battery optimization is disabled for this app',
          deniedText: 'Battery optimization is still active for this app',
        ),
      ],
    );
  }

  Widget _readyPage() {
    final setup = controller.onboardingState;
    return _SetupPage(
      icon: Icons.check_circle_outline,
      title: 'Ready to scan',
      intro: widget.reviewMode
          ? 'Your setup status is shown below.'
          : 'Smartglass Detector has the required access and is ready to start.',
      children: [
        _SummaryRow(
          label: 'Smartglass scanning',
          enabled: setup.corePermissionsGranted,
          required: true,
        ),
        _SummaryRow(
          label: 'Image/video activity detection',
          enabled: setup.wifiPermissionGranted,
        ),
        _SummaryRow(
          label: 'Notification cards',
          enabled: setup.notificationPermissionGranted,
        ),
        _SummaryRow(
          label: 'Battery optimization disabled',
          enabled: setup.batteryOptimizationExempt,
        ),
        _SummaryRow(
          label: 'Wi-Fi scan throttling off',
          enabled:
              setup.wifiScanThrottleDisabled ||
              (!setup.wifiScanThrottleQuerySupported &&
                  _manualThrottleConfirmation),
        ),
        _SummaryRow(
          label:
              'Wi-Fi observations every ${controller.settings.wifiScanIntervalSeconds}s',
          enabled: true,
        ),
        if (!widget.reviewMode)
          const _Notice(
            text:
                'If Bluetooth is off, Android will ask you to turn it on. If '
                'you decline, Home will explain how to start later.',
          ),
      ],
    );
  }

  Widget _footer() {
    final setup = controller.onboardingState;
    final optional = _page == 2 || _page == 3 || _page == 4 || _page == 5;
    final label = switch (_page) {
      0 => 'Get started',
      1 when setup.corePermissionsGranted => 'Continue',
      1
          when setup.corePermanentlyDenied ||
              setup.backgroundLocationSettingsRequired =>
        'Open app settings',
      1 => 'Allow scanning access',
      2
          when !setup.wifiRuntimePermissionRequired ||
              setup.wifiPermissionGranted =>
        'Continue',
      2 when setup.wifiPermissionPermanentlyDenied => 'Open app settings',
      2 => 'Allow Nearby Wi-Fi',
      3
          when !setup.notificationRuntimePermissionRequired ||
              setup.notificationPermissionGranted =>
        'Continue',
      3 when setup.notificationPermanentlyDenied => 'Open app settings',
      3 => 'Allow notifications',
      4 when setup.batteryOptimizationExempt => 'Continue',
      4 => 'Disable battery optimization',
      5 when _throttleStepSatisfied => 'Continue',
      5 => 'Open Developer Options',
      6 when widget.reviewMode => 'Done',
      _ => 'Start scanning',
    };
    return Container(
      decoration: BoxDecoration(
        color: context.appColors.surface,
        border: Border(top: BorderSide(color: context.appColors.border)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(
              alpha: Theme.of(context).brightness == Brightness.dark
                  ? 0.24
                  : 0.08,
            ),
            blurRadius: 20,
            offset: const Offset(0, -6),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FilledButton.icon(
              key: ValueKey<String>('onboarding-primary-$_page'),
              onPressed: _busy ? null : _primaryAction,
              icon: _busy
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(_page == 6 ? Icons.play_arrow : Icons.arrow_forward),
              label: Text(label),
            ),
            if (optional && !_optionalStepAlreadyAvailable) ...[
              const SizedBox(height: 4),
              TextButton(
                key: ValueKey<String>('onboarding-skip-$_page'),
                onPressed: _busy ? null : _skipOptionalPage,
                child: const Text('Not now'),
              ),
            ],
          ],
        ),
      ),
    );
  }

  bool get _optionalStepAlreadyAvailable {
    final setup = controller.onboardingState;
    return switch (_page) {
      2 => !setup.wifiRuntimePermissionRequired || setup.wifiPermissionGranted,
      3 =>
        !setup.notificationRuntimePermissionRequired ||
            setup.notificationPermissionGranted,
      4 => setup.batteryOptimizationExempt,
      5 => _throttleStepSatisfied,
      _ => false,
    };
  }
}

enum _DeveloperOptionsChoice { aboutPhone, developerOptions }

class _SetupPage extends StatelessWidget {
  const _SetupPage({
    required this.icon,
    required this.title,
    required this.intro,
    required this.children,
  });

  final IconData icon;
  final String title;
  final String intro;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.fromLTRB(20, 24, 20, 32),
    children: [
      Center(
        child: Container(
          width: 88,
          height: 88,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                context.appColors.primaryStart,
                context.appColors.primaryEnd,
              ],
            ),
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: context.appColors.primaryStart.withValues(alpha: 0.2),
                blurRadius: 24,
                offset: const Offset(0, 10),
              ),
            ],
          ),
          alignment: Alignment.center,
          child: Icon(icon, size: 44, color: context.appColors.onPrimary),
        ),
      ),
      const SizedBox(height: 18),
      Text(
        title,
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.headlineSmall,
      ),
      const SizedBox(height: 8),
      Text(
        intro,
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodyLarge,
      ),
      const SizedBox(height: 28),
      ...children,
    ],
  );
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.title, required this.body});

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 18),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppIconTile(icon: icon, tone: AppTone.info),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 3),
              Text(body),
            ],
          ),
        ),
      ],
    ),
  );
}

class _Notice extends StatelessWidget {
  const _Notice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => AppGradientPanel(
    tone: AppTone.info,
    solid: true,
    radius: 18,
    padding: const EdgeInsets.all(14),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const AppIconTile(
          icon: Icons.info_outline,
          tone: AppTone.info,
          size: 36,
          iconSize: 19,
        ),
        const SizedBox(width: 10),
        Expanded(child: Text(text)),
      ],
    ),
  );
}

class _PermissionStatus extends StatelessWidget {
  const _PermissionStatus({
    required this.granted,
    required this.grantedText,
    required this.deniedText,
  });

  final bool granted;
  final String grantedText;
  final String deniedText;

  @override
  Widget build(BuildContext context) {
    final tone = granted ? AppTone.safe : AppTone.warning;
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: AppGradientPanel(
        tone: tone,
        solid: true,
        radius: 16,
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            AppIconTile(
              icon: granted ? Icons.check_circle : Icons.pending_outlined,
              tone: tone,
              size: 36,
              iconSize: 19,
            ),
            const SizedBox(width: 10),
            Expanded(child: Text(granted ? grantedText : deniedText)),
          ],
        ),
      ),
    );
  }
}

class _InstructionCard extends StatelessWidget {
  const _InstructionCard({required this.steps});

  final List<String> steps;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: List.generate(
          steps.length,
          (index) => Padding(
            padding: EdgeInsets.only(
              bottom: index == steps.length - 1 ? 0 : 12,
            ),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 15,
                  backgroundColor: context.appColors.info.start,
                  foregroundColor: context.appColors.info.foreground,
                  child: Text(
                    '${index + 1}',
                    style: Theme.of(context).textTheme.labelMedium,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(steps[index])),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.label,
    required this.enabled,
    this.required = false,
  });

  final String label;
  final bool enabled;
  final bool required;

  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    leading: AppIconTile(
      icon: enabled ? Icons.check_circle : Icons.remove_circle_outline,
      tone: enabled ? AppTone.safe : AppTone.neutral,
    ),
    title: Text(label),
    subtitle: Text(
      enabled
          ? 'Ready'
          : required
          ? 'Required'
          : 'Skipped',
    ),
  );
}
