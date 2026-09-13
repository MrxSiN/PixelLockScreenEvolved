# Pixel Lock Screen Evolved v0.2.0

Adds **Font** and **Clock size** sliders to the iOS lock screen clock.

Open Wallpaper & style → Clock and choose the iOS clock. The Style tab's slider
picks the font: Roboto Flex or Inter, the open font closest to SF Pro. The Size
tab's slider, which replaces the Large switch for this clock, has six steps,
each taller than the last, in bolder numerals with
even strokes at every size. With notifications showing, the clock drops to the
smallest step, with Pixel's date and weather centred below it. The preview
follows both sliders as you drag, and Apply puts them on the lock screen and
the always-on display.

## Requirements

- Pixel on Android 17
- Vector (JingMatrix/Vector) or another framework with libxposed API 101 or newer
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after installing

## Changes

- Font slider (Roboto Flex or Inter) in the Style tab
- Clock size slider in the Size tab
- Smallest step while notifications are showing
- The iOS time is set in Roboto Flex; the date sits closer to the time
- Signed release builds from GitHub Actions
