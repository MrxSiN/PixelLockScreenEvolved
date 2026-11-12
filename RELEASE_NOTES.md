# Pixel Lock Screen Evolved v0.0.8

**Fully compatible with Android 17 QPR1 (November 2026, `CP4A.261105.040.A1`),
tested on a Pixel 8 Pro.**

## Fixes

- After a double tap to turn off the screen, the lock screen wallpaper and the
  backdrop behind notifications stayed black on the next wake. They no longer do.

## Survives renames

- Every numbered host name the module relies on (Kotlin and R8 lambdas in
  SystemUI and Wallpaper & style) can now be found again by what it is, with
  DexKit, when a monthly build renumbers it. Compatibility & diagnostics shows
  such a member as found by fallback.

See `CHANGELOG.md` for the full list, including v0.0.7.
