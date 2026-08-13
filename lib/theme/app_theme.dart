import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/app_settings.dart';

enum AppTone { safe, danger, warning, info, neutral }

@immutable
class AppTonePalette {
  const AppTonePalette({
    required this.start,
    required this.end,
    required this.foreground,
    required this.border,
  });

  final Color start;
  final Color end;
  final Color foreground;
  final Color border;

  static AppTonePalette lerp(AppTonePalette a, AppTonePalette b, double t) =>
      AppTonePalette(
        start: Color.lerp(a.start, b.start, t)!,
        end: Color.lerp(a.end, b.end, t)!,
        foreground: Color.lerp(a.foreground, b.foreground, t)!,
        border: Color.lerp(a.border, b.border, t)!,
      );
}

@immutable
class AppSemanticColors extends ThemeExtension<AppSemanticColors> {
  const AppSemanticColors({
    required this.canvas,
    required this.surface,
    required this.surfaceAlt,
    required this.surfaceDeep,
    required this.surfaceHighest,
    required this.border,
    required this.outline,
    required this.textPrimary,
    required this.textSecondary,
    required this.primaryStart,
    required this.primaryEnd,
    required this.onPrimary,
    required this.safe,
    required this.danger,
    required this.warning,
    required this.info,
    required this.neutral,
  });

  final Color canvas;
  final Color surface;
  final Color surfaceAlt;
  final Color surfaceDeep;
  final Color surfaceHighest;
  final Color border;
  final Color outline;
  final Color textPrimary;
  final Color textSecondary;
  final Color primaryStart;
  final Color primaryEnd;
  final Color onPrimary;
  final AppTonePalette safe;
  final AppTonePalette danger;
  final AppTonePalette warning;
  final AppTonePalette info;
  final AppTonePalette neutral;

  AppTonePalette paletteFor(AppTone tone) => switch (tone) {
    AppTone.safe => safe,
    AppTone.danger => danger,
    AppTone.warning => warning,
    AppTone.info => info,
    AppTone.neutral => neutral,
  };

  @override
  AppSemanticColors copyWith({
    Color? canvas,
    Color? surface,
    Color? surfaceAlt,
    Color? surfaceDeep,
    Color? surfaceHighest,
    Color? border,
    Color? outline,
    Color? textPrimary,
    Color? textSecondary,
    Color? primaryStart,
    Color? primaryEnd,
    Color? onPrimary,
    AppTonePalette? safe,
    AppTonePalette? danger,
    AppTonePalette? warning,
    AppTonePalette? info,
    AppTonePalette? neutral,
  }) => AppSemanticColors(
    canvas: canvas ?? this.canvas,
    surface: surface ?? this.surface,
    surfaceAlt: surfaceAlt ?? this.surfaceAlt,
    surfaceDeep: surfaceDeep ?? this.surfaceDeep,
    surfaceHighest: surfaceHighest ?? this.surfaceHighest,
    border: border ?? this.border,
    outline: outline ?? this.outline,
    textPrimary: textPrimary ?? this.textPrimary,
    textSecondary: textSecondary ?? this.textSecondary,
    primaryStart: primaryStart ?? this.primaryStart,
    primaryEnd: primaryEnd ?? this.primaryEnd,
    onPrimary: onPrimary ?? this.onPrimary,
    safe: safe ?? this.safe,
    danger: danger ?? this.danger,
    warning: warning ?? this.warning,
    info: info ?? this.info,
    neutral: neutral ?? this.neutral,
  );

  @override
  AppSemanticColors lerp(covariant AppSemanticColors? other, double t) {
    if (other == null) return this;
    return AppSemanticColors(
      canvas: Color.lerp(canvas, other.canvas, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      surfaceAlt: Color.lerp(surfaceAlt, other.surfaceAlt, t)!,
      surfaceDeep: Color.lerp(surfaceDeep, other.surfaceDeep, t)!,
      surfaceHighest: Color.lerp(surfaceHighest, other.surfaceHighest, t)!,
      border: Color.lerp(border, other.border, t)!,
      outline: Color.lerp(outline, other.outline, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      primaryStart: Color.lerp(primaryStart, other.primaryStart, t)!,
      primaryEnd: Color.lerp(primaryEnd, other.primaryEnd, t)!,
      onPrimary: Color.lerp(onPrimary, other.onPrimary, t)!,
      safe: AppTonePalette.lerp(safe, other.safe, t),
      danger: AppTonePalette.lerp(danger, other.danger, t),
      warning: AppTonePalette.lerp(warning, other.warning, t),
      info: AppTonePalette.lerp(info, other.info, t),
      neutral: AppTonePalette.lerp(neutral, other.neutral, t),
    );
  }
}

extension AppThemeContext on BuildContext {
  AppSemanticColors get appColors =>
      Theme.of(this).extension<AppSemanticColors>()!;
}

class AppTheme {
  const AppTheme._();

  static const lightColors = AppSemanticColors(
    canvas: Color(0xFFF5F7FB),
    surface: Color(0xFFFFFFFF),
    surfaceAlt: Color(0xFFEEF2F7),
    surfaceDeep: Color(0xFF111827),
    surfaceHighest: Color(0xFFE7ECF3),
    border: Color(0xFFDCE3EC),
    outline: Color(0xFF9AA6B6),
    textPrimary: Color(0xFF172033),
    textSecondary: Color(0xFF667085),
    primaryStart: Color(0xFF3F63E9),
    primaryEnd: Color(0xFF3457D5),
    onPrimary: Color(0xFFFFFFFF),
    safe: AppTonePalette(
      start: Color(0xFFEAF8F1),
      end: Color(0xFFD7F2E4),
      foreground: Color(0xFF12653F),
      border: Color(0xFFBDE6D1),
    ),
    danger: AppTonePalette(
      start: Color(0xFFFFF0F2),
      end: Color(0xFFFFDCE2),
      foreground: Color(0xFF912236),
      border: Color(0xFFF5B7C1),
    ),
    warning: AppTonePalette(
      start: Color(0xFFFFF6E6),
      end: Color(0xFFFFEAC3),
      foreground: Color(0xFF7A4A00),
      border: Color(0xFFF3D399),
    ),
    info: AppTonePalette(
      start: Color(0xFFEDF2FF),
      end: Color(0xFFDFE8FF),
      foreground: Color(0xFF274BBA),
      border: Color(0xFFC9D5FA),
    ),
    neutral: AppTonePalette(
      start: Color(0xFFFFFFFF),
      end: Color(0xFFEDF2F7),
      foreground: Color(0xFF475467),
      border: Color(0xFFDCE3EC),
    ),
  );

  static const darkColors = AppSemanticColors(
    canvas: Color(0xFF0F1420),
    surface: Color(0xFF171E2B),
    surfaceAlt: Color(0xFF202A39),
    surfaceDeep: Color(0xFF0B101A),
    surfaceHighest: Color(0xFF263142),
    border: Color(0xFF303B4C),
    outline: Color(0xFF627086),
    textPrimary: Color(0xFFF4F7FB),
    textSecondary: Color(0xFFAAB5C4),
    primaryStart: Color(0xFF8AA4FF),
    primaryEnd: Color(0xFF627EF0),
    onPrimary: Color(0xFF10162A),
    safe: AppTonePalette(
      start: Color(0xFF123B2D),
      end: Color(0xFF0F2D24),
      foreground: Color(0xFF70E0AA),
      border: Color(0xFF245B47),
    ),
    danger: AppTonePalette(
      start: Color(0xFF491A23),
      end: Color(0xFF30151B),
      foreground: Color(0xFFFF9AAA),
      border: Color(0xFF74313D),
    ),
    warning: AppTonePalette(
      start: Color(0xFF422F13),
      end: Color(0xFF2E2415),
      foreground: Color(0xFFF4C66C),
      border: Color(0xFF6E5427),
    ),
    info: AppTonePalette(
      start: Color(0xFF1C2C53),
      end: Color(0xFF17213D),
      foreground: Color(0xFFA6B9FF),
      border: Color(0xFF314675),
    ),
    neutral: AppTonePalette(
      start: Color(0xFF202A39),
      end: Color(0xFF171E2B),
      foreground: Color(0xFFD7DFEA),
      border: Color(0xFF303B4C),
    ),
  );

  static const amoledColors = AppSemanticColors(
    canvas: Color(0xFF000000),
    surface: Color(0xFF000000),
    surfaceAlt: Color(0xFF0A0A0A),
    surfaceDeep: Color(0xFF000000),
    surfaceHighest: Color(0xFF161616),
    border: Color(0xFF2A2A2A),
    outline: Color(0xFF666666),
    textPrimary: Color(0xFFF5F5F5),
    textSecondary: Color(0xFFB3B3B3),
    primaryStart: Color(0xFF8AA4FF),
    primaryEnd: Color(0xFF627EF0),
    onPrimary: Color(0xFF000000),
    safe: AppTonePalette(
      start: Color(0xFF123B2D),
      end: Color(0xFF0F2D24),
      foreground: Color(0xFF70E0AA),
      border: Color(0xFF245B47),
    ),
    danger: AppTonePalette(
      start: Color(0xFF491A23),
      end: Color(0xFF30151B),
      foreground: Color(0xFFFF9AAA),
      border: Color(0xFF74313D),
    ),
    warning: AppTonePalette(
      start: Color(0xFF422F13),
      end: Color(0xFF2E2415),
      foreground: Color(0xFFF4C66C),
      border: Color(0xFF6E5427),
    ),
    info: AppTonePalette(
      start: Color(0xFF1C2C53),
      end: Color(0xFF17213D),
      foreground: Color(0xFFA6B9FF),
      border: Color(0xFF314675),
    ),
    neutral: AppTonePalette(
      start: Color(0xFF0A0A0A),
      end: Color(0xFF000000),
      foreground: Color(0xFFD7D7D7),
      border: Color(0xFF2A2A2A),
    ),
  );

  static ThemeData light([AppAccentColor accentColor = AppAccentColor.blue]) =>
      _build(
        Brightness.light,
        _withAccent(lightColors, accentColor, Brightness.light),
      );

  static ThemeData dark([AppAccentColor accentColor = AppAccentColor.blue]) =>
      _build(
        Brightness.dark,
        _withAccent(darkColors, accentColor, Brightness.dark),
      );

  static ThemeData amoled([AppAccentColor accentColor = AppAccentColor.blue]) =>
      _build(
        Brightness.dark,
        _withAccent(amoledColors, accentColor, Brightness.dark),
      );

  static SystemUiOverlayStyle systemUiOverlayStyle(
    Brightness brightness,
    AppSemanticColors appColors,
  ) {
    final iconBrightness = brightness == Brightness.dark
        ? Brightness.light
        : Brightness.dark;
    return SystemUiOverlayStyle(
      statusBarColor: appColors.canvas,
      statusBarIconBrightness: iconBrightness,
      statusBarBrightness: brightness,
      systemNavigationBarColor: appColors.canvas,
      systemNavigationBarDividerColor: appColors.border,
      systemNavigationBarIconBrightness: iconBrightness,
    );
  }

  static Color accentPreview(
    AppAccentColor accentColor,
    Brightness brightness,
  ) => _accentPair(accentColor, brightness).$1;

  static AppSemanticColors _withAccent(
    AppSemanticColors colors,
    AppAccentColor accentColor,
    Brightness brightness,
  ) {
    final accent = _accentPair(accentColor, brightness);
    return colors.copyWith(primaryStart: accent.$1, primaryEnd: accent.$2);
  }

  static (Color, Color) _accentPair(
    AppAccentColor accentColor,
    Brightness brightness,
  ) {
    final dark = brightness == Brightness.dark;
    return switch (accentColor) {
      AppAccentColor.blue =>
        dark
            ? (const Color(0xFF8AA4FF), const Color(0xFF627EF0))
            : (const Color(0xFF3F63E9), const Color(0xFF3457D5)),
      AppAccentColor.purple =>
        dark
            ? (const Color(0xFFB69CFF), const Color(0xFF8D72E6))
            : (const Color(0xFF7451D8), const Color(0xFF5F3FC4)),
      AppAccentColor.teal =>
        dark
            ? (const Color(0xFF66D8CA), const Color(0xFF39AFA3))
            : (const Color(0xFF087F73), const Color(0xFF05665D)),
      AppAccentColor.orange =>
        dark
            ? (const Color(0xFFFFB56B), const Color(0xFFDB8440))
            : (const Color(0xFFB85C0A), const Color(0xFF914507)),
      AppAccentColor.rose =>
        dark
            ? (const Color(0xFFFF91B5), const Color(0xFFD9658D))
            : (const Color(0xFFC53F6A), const Color(0xFFA72E56)),
    };
  }

  static ThemeData _build(Brightness brightness, AppSemanticColors appColors) {
    final isDark = brightness == Brightness.dark;
    final overlayStyle = systemUiOverlayStyle(brightness, appColors);
    final baseScheme = ColorScheme.fromSeed(
      seedColor: appColors.primaryStart,
      brightness: brightness,
    );
    final scheme = baseScheme.copyWith(
      primary: appColors.primaryStart,
      onPrimary: appColors.onPrimary,
      primaryContainer: baseScheme.primaryContainer,
      onPrimaryContainer: baseScheme.onPrimaryContainer,
      secondary: baseScheme.secondary,
      secondaryContainer: baseScheme.secondaryContainer,
      onSecondaryContainer: baseScheme.onSecondaryContainer,
      tertiary: isDark ? const Color(0xFFF4C66C) : const Color(0xFFA66400),
      tertiaryContainer: appColors.warning.start,
      onTertiaryContainer: appColors.warning.foreground,
      error: isDark ? const Color(0xFFFF7286) : const Color(0xFFC92A3F),
      onError: Colors.white,
      errorContainer: appColors.danger.start,
      onErrorContainer: appColors.danger.foreground,
      surface: appColors.surface,
      onSurface: appColors.textPrimary,
      surfaceContainerLowest: appColors.surface,
      surfaceContainerLow: appColors.surface,
      surfaceContainer: appColors.surfaceAlt,
      surfaceContainerHigh: appColors.surfaceAlt,
      surfaceContainerHighest: appColors.surfaceHighest,
      onSurfaceVariant: appColors.textSecondary,
      outline: appColors.outline,
      outlineVariant: appColors.border,
    );

    final base = ThemeData(
      brightness: brightness,
      colorScheme: scheme,
      useMaterial3: true,
    );
    final baseTextTheme = base.textTheme.apply(
      bodyColor: appColors.textPrimary,
      displayColor: appColors.textPrimary,
    );
    final textTheme = baseTextTheme.copyWith(
      displayLarge: baseTextTheme.displayLarge?.copyWith(
        fontSize: 48,
        height: 52 / 48,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.8,
      ),
      displayMedium: baseTextTheme.displayMedium?.copyWith(
        fontSize: 40,
        height: 44 / 40,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.6,
      ),
      displaySmall: baseTextTheme.displaySmall?.copyWith(
        fontSize: 32,
        height: 38 / 32,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.4,
      ),
      headlineLarge: baseTextTheme.headlineLarge?.copyWith(
        fontSize: 28,
        height: 34 / 28,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.3,
      ),
      headlineMedium: baseTextTheme.headlineMedium?.copyWith(
        fontSize: 26,
        height: 32 / 26,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.3,
      ),
      headlineSmall: baseTextTheme.headlineSmall?.copyWith(
        fontSize: 24,
        height: 30 / 24,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.25,
      ),
      titleLarge: baseTextTheme.titleLarge?.copyWith(
        fontSize: 20,
        height: 26 / 20,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.15,
      ),
      titleMedium: baseTextTheme.titleMedium?.copyWith(
        fontSize: 16,
        height: 22 / 16,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
      ),
      titleSmall: baseTextTheme.titleSmall?.copyWith(
        fontSize: 14,
        height: 20 / 14,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
      ),
      bodyLarge: baseTextTheme.bodyLarge?.copyWith(
        fontSize: 16,
        height: 24 / 16,
        fontWeight: FontWeight.w400,
        letterSpacing: 0,
      ),
      bodyMedium: baseTextTheme.bodyMedium?.copyWith(
        fontSize: 14,
        height: 20 / 14,
        fontWeight: FontWeight.w400,
        letterSpacing: 0,
      ),
      bodySmall: baseTextTheme.bodySmall?.copyWith(
        fontSize: 12,
        height: 17 / 12,
        fontWeight: FontWeight.w400,
        letterSpacing: 0,
        color: appColors.textSecondary,
      ),
      labelLarge: baseTextTheme.labelLarge?.copyWith(
        fontSize: 14,
        height: 18 / 14,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
      ),
      labelMedium: baseTextTheme.labelMedium?.copyWith(
        fontSize: 12,
        height: 14 / 12,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
      ),
      labelSmall: baseTextTheme.labelSmall?.copyWith(
        fontSize: 11,
        height: 14 / 11,
        fontWeight: FontWeight.w600,
        letterSpacing: 0,
      ),
    );

    final cardShape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(20),
      side: BorderSide(color: appColors.border),
    );
    final controlShape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(14),
    );

    return base.copyWith(
      extensions: <ThemeExtension<dynamic>>[appColors],
      scaffoldBackgroundColor: appColors.canvas,
      canvasColor: appColors.canvas,
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        backgroundColor: appColors.canvas,
        foregroundColor: appColors.textPrimary,
        surfaceTintColor: Colors.transparent,
        systemOverlayStyle: overlayStyle,
        titleTextStyle: textTheme.titleLarge,
      ),
      cardTheme: CardThemeData(
        margin: EdgeInsets.zero,
        color: appColors.surface,
        surfaceTintColor: Colors.transparent,
        shadowColor: Colors.black.withValues(alpha: isDark ? 0.28 : 0.08),
        elevation: isDark ? 1.5 : 1,
        clipBehavior: Clip.antiAlias,
        shape: cardShape,
      ),
      dividerTheme: DividerThemeData(
        color: appColors.border,
        thickness: 1,
        space: 1,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 52),
          shape: controlShape,
          textStyle: textTheme.labelLarge,
          elevation: 0,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 50),
          shape: controlShape,
          side: BorderSide(color: appColors.border),
          textStyle: textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: textTheme.labelLarge,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: appColors.surfaceAlt,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 15,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: appColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: appColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: appColors.primaryStart, width: 1.5),
        ),
      ),
      chipTheme: base.chipTheme.copyWith(
        backgroundColor: appColors.surfaceAlt,
        side: BorderSide(color: appColors.border),
        shape: const StadiumBorder(),
        labelStyle: textTheme.labelMedium?.copyWith(
          color: appColors.textPrimary,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 4),
      ),
      listTileTheme: ListTileThemeData(
        iconColor: appColors.textSecondary,
        textColor: appColors.textPrimary,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: appColors.surface,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        titleTextStyle: textTheme.titleLarge,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: appColors.primaryStart,
        linearTrackColor: appColors.surfaceAlt,
      ),
      sliderTheme: base.sliderTheme.copyWith(
        activeTrackColor: appColors.primaryStart,
        thumbColor: appColors.primaryStart,
        overlayColor: appColors.primaryStart.withValues(alpha: 0.12),
        inactiveTrackColor: appColors.border,
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? appColors.onPrimary
              : appColors.textSecondary,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? appColors.primaryStart
              : appColors.surfaceAlt,
        ),
        trackOutlineColor: WidgetStatePropertyAll(appColors.border),
      ),
      tooltipTheme: TooltipThemeData(
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFFEEF2F7) : const Color(0xFF202A39),
          borderRadius: BorderRadius.circular(10),
        ),
        textStyle: TextStyle(
          color: isDark ? const Color(0xFF172033) : const Color(0xFFF4F7FB),
        ),
      ),
    );
  }
}

extension AppThemePreferenceMaterial on AppThemePreference {
  ThemeMode get materialThemeMode => switch (this) {
    AppThemePreference.system => ThemeMode.system,
    AppThemePreference.light => ThemeMode.light,
    AppThemePreference.dark => ThemeMode.dark,
    AppThemePreference.amoled => ThemeMode.dark,
  };
}
