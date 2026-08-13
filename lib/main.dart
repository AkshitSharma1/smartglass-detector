import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'controllers/detector_controller.dart';
import 'models/app_settings.dart';
import 'screens/home_screen.dart';
import 'screens/onboarding_screen.dart';
import 'services/detector_platform.dart';
import 'theme/app_theme.dart';
import 'widgets/app_components.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await configurePortraitOrientation();
  runApp(
    SmartglassDetectorApp(
      controller: DetectorController(NativeDetectorPlatform()),
    ),
  );
}

Future<void> configurePortraitOrientation() =>
    SystemChrome.setPreferredOrientations(const <DeviceOrientation>[
      DeviceOrientation.portraitUp,
    ]);

class SmartglassDetectorApp extends StatefulWidget {
  const SmartglassDetectorApp({
    super.key,
    required this.controller,
    this.showMediaTransferExplainer = true,
  });

  final DetectorController controller;
  final bool showMediaTransferExplainer;

  @override
  State<SmartglassDetectorApp> createState() => _SmartglassDetectorAppState();
}

class _SmartglassDetectorAppState extends State<SmartglassDetectorApp> {
  late AppThemePreference _themePreference;
  late AppAccentColor _accentColor;

  @override
  void initState() {
    super.initState();
    _readAppearance();
    widget.controller.addListener(_handleControllerChanged);
  }

  @override
  void didUpdateWidget(covariant SmartglassDetectorApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_handleControllerChanged);
      _readAppearance();
      widget.controller.addListener(_handleControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleControllerChanged);
    super.dispose();
  }

  void _readAppearance() {
    _themePreference = widget.controller.settings.themePreference;
    _accentColor = widget.controller.settings.accentColor;
  }

  void _handleControllerChanged() {
    final settings = widget.controller.settings;
    if (settings.themePreference == _themePreference &&
        settings.accentColor == _accentColor) {
      return;
    }
    setState(() {
      _themePreference = settings.themePreference;
      _accentColor = settings.accentColor;
    });
  }

  @override
  Widget build(BuildContext context) {
    final darkTheme = _themePreference == AppThemePreference.amoled
        ? AppTheme.amoled(_accentColor)
        : AppTheme.dark(_accentColor);
    return MaterialApp(
      title: 'Smartglass Detector',
      debugShowCheckedModeBanner: false,
      themeMode: _themePreference.materialThemeMode,
      theme: AppTheme.light(_accentColor),
      darkTheme: darkTheme,
      builder: (context, child) {
        final theme = Theme.of(context);
        final appColors = theme.extension<AppSemanticColors>()!;
        return AnnotatedRegion<SystemUiOverlayStyle>(
          value: AppTheme.systemUiOverlayStyle(theme.brightness, appColors),
          child: child ?? const SizedBox.shrink(),
        );
      },
      home: _LaunchGate(
        controller: widget.controller,
        showMediaTransferExplainer: widget.showMediaTransferExplainer,
      ),
    );
  }
}

class _LaunchGate extends StatefulWidget {
  const _LaunchGate({
    required this.controller,
    required this.showMediaTransferExplainer,
  });

  final DetectorController controller;
  final bool showMediaTransferExplainer;

  @override
  State<_LaunchGate> createState() => _LaunchGateState();
}

class _LaunchGateState extends State<_LaunchGate> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_refresh);
    widget.controller.initialize();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_refresh);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !widget.controller.initializing) {
      widget.controller.refreshSystemState();
    }
  }

  void _refresh() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.controller.initializing) {
      return Scaffold(
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: AppGradientPanel(
                tone: AppTone.info,
                animate: false,
                padding: const EdgeInsets.symmetric(
                  horizontal: 36,
                  vertical: 32,
                ),
                child: const Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    AppIconTile(
                      icon: Icons.visibility_outlined,
                      tone: AppTone.info,
                      size: 64,
                      iconSize: 32,
                    ),
                    SizedBox(height: 22),
                    CircularProgressIndicator(),
                    SizedBox(height: 16),
                    Text(
                      'Preparing Smartglass Detector…',
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }
    if (!widget.controller.onboardingState.completed) {
      return OnboardingScreen(controller: widget.controller);
    }
    return HomeScreen(
      controller: widget.controller,
      showMediaTransferExplainer: widget.showMediaTransferExplainer,
    );
  }
}
