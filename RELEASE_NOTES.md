# Pixel Lock Screen Evolved v0.0.1

The first release on GitHub: an **iOS** lock screen clock for Pixel on Android
17, chosen in Wallpaper & style beside Google's own clocks.

Open Wallpaper & style → Clock and choose the iOS clock. The Style tab's slider
picks the font: Roboto Flex or Inter, the open font closest to SF Pro. The Size
tab's slider, in place of the Large switch, has six steps, each taller than the
last, in bolder numerals with even strokes at every size. With notifications
showing, the clock drops to the smallest step with Pixel's date and weather
centred below it, and moves between the two sizes in one piece.

Switch on **Depth effect** at the bottom of Wallpaper & style's Lock screen list
to put the subject of a photo wallpaper in front of the time. The subject is
found on the device.

On unlock, the time flies up to the status bar clock and turns into it, the
status icons stay put, and the notification icons and chips come in beside the
clock on Material 3 Expressive springs.

## Requirements

- Pixel on Android 17
- Vector (JingMatrix/Vector) or another framework with libxposed API 101 or newer
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after installing

## In this release

- iOS clock on the lock screen, in the picker preview and on the always-on display
- Font slider (Roboto Flex or Inter) in the Style tab
- Clock size slider in the Size tab
- Depth effect for photo wallpapers
- Smooth size changes as notifications come and go
- Unlock motion for the clock, the status icons and the notification icons
