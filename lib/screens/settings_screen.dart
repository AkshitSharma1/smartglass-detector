import 'package:flutter/material.dart';

import '../controllers/detector_controller.dart';
import '../models/app_settings.dart';
import '../theme/app_theme.dart';
import '../widgets/app_components.dart';
import 'onboarding_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.controller});

  final DetectorController controller;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late double _threshold;

  @override
  void initState() {
    super.initState();
    _threshold = widget.controller.settings.alertThresholdRssi.toDouble();
    widget.controller.addListener(_refresh);
  }

  @override
  void dispose() {
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
    final settings = controller.settings;
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          const AppSectionHeading(
            title: 'Appearance',
            subtitle: 'Choose how the app looks on this phone.',
          ),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  DropdownButtonFormField<AppThemePreference>(
                    key: const ValueKey<String>('theme-mode-selector'),
                    isExpanded: true,
                    initialValue: settings.themePreference,
                    decoration: const InputDecoration(
                      labelText: 'Theme',
                      prefixIcon: Icon(Icons.contrast_outlined),
                      border: OutlineInputBorder(),
                    ),
                    items: AppThemePreference.values
                        .map(
                          (preference) => DropdownMenuItem(
                            value: preference,
                            child: Text(preference.label),
                          ),
                        )
                        .toList(growable: false),
                    onChanged: (preference) {
                      if (preference != null) {
                        controller.updateSettings(
                          settings.copyWith(themePreference: preference),
                        );
                      }
                    },
                  ),
                  const SizedBox(height: 18),
                  Text(
                    'Theme color',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Used for controls and selections. Status colors remain consistent.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: AppAccentColor.values
                        .map((accentColor) {
                          final selected = settings.accentColor == accentColor;
                          return ChoiceChip(
                            key: ValueKey<String>(
                              'theme-color-${accentColor.serialized}',
                            ),
                            selected: selected,
                            showCheckmark: true,
                            avatar: DecoratedBox(
                              decoration: BoxDecoration(
                                color: AppTheme.accentPreview(
                                  accentColor,
                                  Theme.of(context).brightness,
                                ),
                                shape: BoxShape.circle,
                                border: Border.all(
                                  color: context.appColors.border,
                                ),
                              ),
                              child: const SizedBox.square(dimension: 18),
                            ),
                            label: Text(accentColor.label),
                            onSelected: (_) => controller.updateSettings(
                              settings.copyWith(accentColor: accentColor),
                            ),
                          );
                        })
                        .toList(growable: false),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const AppSectionHeading(title: 'Proximity alerts'),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('Notifications, sound, and vibration'),
                  subtitle: const Text(
                    'Alert once per smartglass encounter or image/video activity session. Notifications are always attempted. Shake the phone to dismiss everything.',
                  ),
                  secondary: const Icon(Icons.notifications_active_outlined),
                  value: settings.alertsEnabled,
                  onChanged: (value) async {
                    await controller.updateSettings(
                      settings.copyWith(alertsEnabled: value),
                    );
                    if (value && !controller.notificationPermissionGranted) {
                      await controller.requestNotificationPermission();
                    }
                  },
                ),
                const Divider(height: 1),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Alert threshold: ${_threshold.round()} dBm',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      const Text(
                        'Stronger signals are closer to zero. This threshold only controls smartglass proximity alerts; it never hides devices and does not apply to image/video activity alerts.',
                      ),
                      Slider(
                        value: _threshold.clamp(-100, -30),
                        min: -100,
                        max: -30,
                        divisions: 70,
                        label: '${_threshold.round()} dBm',
                        onChanged: settings.alertsEnabled
                            ? (value) => setState(() => _threshold = value)
                            : null,
                        onChangeEnd: (value) => controller.updateSettings(
                          settings.copyWith(alertThresholdRssi: value.round()),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          const AppSectionHeading(title: 'Alert behavior'),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  DropdownButtonFormField<AlertMode>(
                    key: const ValueKey<String>('alert-mode-selector'),
                    isExpanded: true,
                    initialValue: settings.alertMode,
                    decoration: const InputDecoration(
                      labelText: 'Alert mode',
                      prefixIcon: Icon(Icons.campaign_outlined),
                      border: OutlineInputBorder(),
                    ),
                    items: AlertMode.values
                        .map(
                          (mode) => DropdownMenuItem(
                            value: mode,
                            child: Text(mode.label),
                          ),
                        )
                        .toList(growable: false),
                    onChanged: settings.alertsEnabled
                        ? (mode) {
                            if (mode != null) {
                              controller.updateSettings(
                                settings.copyWith(alertMode: mode),
                              );
                            }
                          }
                        : null,
                  ),
                  const SizedBox(height: 12),
                  ListTile(
                    key: const ValueKey<String>('notification-sound-picker'),
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.music_note_outlined),
                    title: const Text('Notification sound'),
                    subtitle: Text(settings.alertSoundName),
                    trailing: const Icon(Icons.chevron_right),
                    enabled:
                        settings.alertsEnabled && settings.alertMode.usesSound,
                    onTap:
                        settings.alertsEnabled && settings.alertMode.usesSound
                        ? controller.chooseAlertSound
                        : null,
                  ),
                  OutlinedButton.icon(
                    key: const ValueKey<String>('preview-alert-sound'),
                    onPressed:
                        settings.alertsEnabled &&
                            settings.alertMode.usesSound &&
                            settings.alertSoundUri.isNotEmpty
                        ? controller.previewAlertSound
                        : null,
                    icon: const Icon(Icons.volume_up_outlined),
                    label: const Text('Preview sound'),
                  ),
                  const SizedBox(height: 16),
                  const Divider(height: 1),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<VibrationPreset>(
                    isExpanded: true,
                    key: const ValueKey<String>('vibration-pattern-selector'),
                    initialValue: settings.vibrationPreset,
                    decoration: const InputDecoration(
                      labelText: 'Pattern',
                      prefixIcon: Icon(Icons.vibration),
                      border: OutlineInputBorder(),
                    ),
                    items: VibrationPreset.values
                        .map(
                          (preset) => DropdownMenuItem(
                            value: preset,
                            child: Text(preset.label),
                          ),
                        )
                        .toList(growable: false),
                    onChanged:
                        settings.alertsEnabled &&
                            settings.alertMode.usesVibration
                        ? (preset) {
                            if (preset != null) {
                              controller.updateSettings(
                                settings.copyWith(vibrationPreset: preset),
                              );
                            }
                          }
                        : null,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Vibration uses the maximum intensity supported by this phone.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    isExpanded: true,
                    initialValue: settings.alertDurationMs,
                    decoration: const InputDecoration(
                      labelText: 'Sound and vibration duration',
                      prefixIcon: Icon(Icons.timer_outlined),
                      border: OutlineInputBorder(),
                    ),
                    items: AppSettings.supportedDurationsMs
                        .map(
                          (duration) => DropdownMenuItem(
                            value: duration,
                            child: Text('${duration ~/ 1000} seconds'),
                          ),
                        )
                        .toList(growable: false),
                    onChanged: settings.alertsEnabled
                        ? (duration) {
                            if (duration != null) {
                              controller.updateSettings(
                                settings.copyWith(alertDurationMs: duration),
                              );
                            }
                          }
                        : null,
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed:
                          settings.alertsEnabled &&
                              settings.alertMode.usesVibration
                          ? () => controller.previewVibration(
                              settings.vibrationPreset,
                            )
                          : null,
                      icon: const Icon(Icons.play_arrow),
                      label: const Text('Preview vibration'),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const AppSectionHeading(
            title: 'Background scanning',
            subtitle: 'Keep detection reliable while the app is minimized.',
          ),
          const SizedBox(height: 8),
          AppGradientPanel(
            key: const ValueKey<String>('battery-optimization-status'),
            tone: controller.onboardingState.batteryOptimizationExempt
                ? AppTone.safe
                : AppTone.warning,
            solid: true,
            radius: 20,
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    AppIconTile(
                      icon: controller.onboardingState.batteryOptimizationExempt
                          ? Icons.battery_full_outlined
                          : Icons.battery_alert_outlined,
                      tone: controller.onboardingState.batteryOptimizationExempt
                          ? AppTone.safe
                          : AppTone.warning,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            controller.onboardingState.batteryOptimizationExempt
                                ? 'Battery optimization is disabled'
                                : 'Battery optimization is active',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            controller.onboardingState.batteryOptimizationExempt
                                ? 'Android’s standard battery optimization is not applied to this app.'
                                : 'Background scanning and alerts may be delayed or missed.',
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                if (!controller.onboardingState.batteryOptimizationExempt) ...[
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    key: const ValueKey<String>('disable-battery-optimization'),
                    onPressed: controller.requestBatteryOptimizationExemption,
                    icon: const Icon(Icons.open_in_new),
                    label: const Text('Disable battery optimization'),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 24),
          const AppSectionHeading(title: 'Image/video activity detection'),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  DropdownButtonFormField<int>(
                    isExpanded: true,
                    key: ValueKey<int>(settings.wifiScanIntervalSeconds),
                    initialValue: settings.wifiScanIntervalSeconds,
                    decoration: const InputDecoration(
                      labelText: 'Wi-Fi observation interval',
                      prefixIcon: Icon(Icons.wifi_find),
                      border: OutlineInputBorder(),
                    ),
                    items: AppSettings.supportedWifiScanIntervalsSeconds
                        .map(
                          (seconds) => DropdownMenuItem(
                            value: seconds,
                            child: Text('$seconds seconds'),
                          ),
                        )
                        .toList(growable: false),
                    onChanged: (seconds) {
                      if (seconds != null) {
                        controller.updateSettings(
                          settings.copyWith(wifiScanIntervalSeconds: seconds),
                        );
                      }
                    },
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Controls both Wi-Fi Direct peer sampling and nearby-network '
                    'scan requests. Android controls conventional scan acceptance '
                    'and result freshness.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 14),
                  OutlinedButton.icon(
                    key: const ValueKey<String>('open-setup-guide'),
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => OnboardingScreen(
                          controller: controller,
                          reviewMode: true,
                        ),
                      ),
                    ),
                    icon: const Icon(Icons.menu_book_outlined),
                    label: const Text('Open setup guide'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          const AppSectionHeading(title: 'Diagnostics'),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text('In-app event log'),
                  subtitle: const Text(
                    'Keep up to 200 matching advertisements in memory.',
                  ),
                  secondary: const Icon(Icons.receipt_long_outlined),
                  value: settings.loggingEnabled,
                  onChanged: (value) => controller.updateSettings(
                    settings.copyWith(loggingEnabled: value),
                  ),
                ),
                const Divider(height: 1),
                SwitchListTile(
                  title: const Text('Wireless debug activity'),
                  subtitle: const Text(
                    'Show Bluetooth advertisements and Wi-Fi discovery messages. Restart scanning after changing this.',
                  ),
                  secondary: const Icon(Icons.bug_report_outlined),
                  value: settings.debugEnabled,
                  onChanged: (value) => controller.updateSettings(
                    settings.copyWith(debugEnabled: value),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          const AppGradientPanel(
            tone: AppTone.info,
            solid: true,
            radius: 20,
            padding: EdgeInsets.all(16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                AppIconTile(
                  icon: Icons.phone_android_outlined,
                  tone: AppTone.info,
                ),
                SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'After you press Start, Android keeps scanning with an ongoing foreground-service notification until you press Stop. Force-stopping the app still stops all scanning.',
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Semantics(
            label: 'Built with love by Akshit Sharma',
            child: ExcludeSemantics(
              child: Text.rich(
                TextSpan(
                  children: [
                    const TextSpan(text: 'Built with '),
                    WidgetSpan(
                      alignment: PlaceholderAlignment.middle,
                      child: Icon(
                        Icons.favorite_rounded,
                        size: 13,
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                    const TextSpan(text: ' by Akshit Sharma'),
                  ],
                ),
                key: const ValueKey<String>('settings-credits'),
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: context.appColors.textSecondary,
                  fontSize: 11,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
