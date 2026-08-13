import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

class AppGradientPanel extends StatelessWidget {
  const AppGradientPanel({
    super.key,
    required this.tone,
    required this.child,
    this.padding = const EdgeInsets.all(24),
    this.radius = 28,
    this.solid = false,
    this.animate = true,
  });

  final AppTone tone;
  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;
  final bool solid;
  final bool animate;

  @override
  Widget build(BuildContext context) {
    final palette = context.appColors.paletteFor(tone);
    final decoration = BoxDecoration(
      color: solid ? palette.start : null,
      gradient: solid
          ? null
          : LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [palette.start, palette.end],
            ),
      borderRadius: BorderRadius.circular(radius),
      border: Border.all(color: palette.border),
      boxShadow: [
        BoxShadow(
          color: palette.foreground.withValues(
            alpha: Theme.of(context).brightness == Brightness.dark
                ? 0.12
                : 0.08,
          ),
          blurRadius: 24,
          offset: const Offset(0, 10),
        ),
      ],
    );
    final content = DefaultTextStyle.merge(
      style: TextStyle(color: palette.foreground),
      child: IconTheme.merge(
        data: IconThemeData(color: palette.foreground),
        child: child,
      ),
    );

    if (!animate) {
      return Container(
        padding: padding,
        decoration: decoration,
        child: content,
      );
    }
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOutCubic,
      padding: padding,
      decoration: decoration,
      child: content,
    );
  }
}

class AppIconTile extends StatelessWidget {
  const AppIconTile({
    super.key,
    required this.icon,
    this.tone = AppTone.info,
    this.size = 40,
    this.iconSize = 21,
  });

  final IconData icon;
  final AppTone tone;
  final double size;
  final double iconSize;

  @override
  Widget build(BuildContext context) {
    final palette = context.appColors.paletteFor(tone);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: palette.start,
        borderRadius: BorderRadius.circular(size * 0.32),
        border: Border.all(color: palette.border),
      ),
      alignment: Alignment.center,
      child: Icon(icon, size: iconSize, color: palette.foreground),
    );
  }
}

class AppSectionHeading extends StatelessWidget {
  const AppSectionHeading({super.key, required this.title, this.subtitle});

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(title, style: Theme.of(context).textTheme.titleLarge),
      if (subtitle != null) ...[
        const SizedBox(height: 4),
        Text(subtitle!, style: Theme.of(context).textTheme.bodySmall),
      ],
    ],
  );
}
