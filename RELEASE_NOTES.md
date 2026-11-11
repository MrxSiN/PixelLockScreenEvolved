# Pixel Lock Screen Evolved v0.0.8

**Fully compatible with Android 17 QPR1 (November 2026, `CP4A.261105.040.A1`),
tested on a Pixel 8 Pro.**

## Fixes

- The November build had switched off the clock size slider, the Position
  tab, double tap to turn off screen, and the date row and smartspace
  placement. All four work again.
- SystemUI's helper processes (such as screenshots) no longer load the module.

## Survives renames

- When a monthly build renames something the module relies on, the module now
  finds it again by what it is, once per build (DexKit), instead of switching
  the feature off. Compatibility & diagnostics shows it as found by fallback.

## Lighter

- Less memory held by the depth effect, and less work per lock screen frame.

See `CHANGELOG.md` for the full list.
