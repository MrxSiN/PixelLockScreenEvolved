# Pixel Lock Screen Evolved v0.2.0

Adds a **Clock size** slider to the iOS lock screen clock.

Open Wallpaper & style → Clock, choose the iOS clock, and drag the slider under
the clock list. It has seven steps: the smallest is the classic iOS clock, and
the largest is the tall, full-width numerals of the biggest iOS lock screen
clock. The preview follows the slider as you drag, and Apply puts it on the lock
screen and the always-on display.

## Requirements

- Pixel on Android 17
- Vector (JingMatrix/Vector) or another framework with libxposed API 101 or newer
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after installing

## Changes

- Clock size slider for the iOS clock, labelled "Clock size" in the picker
- The iOS time is set in Roboto Flex; the date sits closer to the time
- Signed release builds from GitHub Actions
