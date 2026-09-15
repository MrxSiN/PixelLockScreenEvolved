# Pixel Lock Screen Evolved v0.0.2

A sharper depth effect that tells you when it is ready, an always-on display
clock that is kinder to the screen, and a steadier status bar on unlock.

## Depth effect

- The subject of your lock screen photo is now found with **BiRefNet_lite**,
  which looks at the photo at 1024 by 1024: hair, thin shapes and the whole
  subject are kept, where the previous model often kept only part of it.
- A **Preparing depth effect** notification shows while the subject is being
  found after you apply a photo (about 12 seconds on a Pixel 8 Pro). It is
  silent and never pops up; its icon shows in the status bar. It turns into
  **Depth effect ready** for a few seconds, and can be turned off in System
  UI's notification settings under "Depth effect".
- The APK is larger (about 104 MB), as it carries the new model.

## Always-on display

- The iOS clock is drawn in **outline** on the always-on display, so its wide
  digits do not keep the same pixels lit.

## Unlock

- The status icons no longer jump sideways or double up as the status bar
  takes them over, and no longer blink out as an unlock begins.

## Requirements

- Pixel on Android 17
- Vector (JingMatrix/Vector) or another framework with libxposed API 101 or newer
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after updating
