# Pixel Lock Screen Evolved v0.0.3

Your wallpaper on the always-on display, a clock that rolls to each minute,
and smoother, quicker motion from the lock screen to your phone.

## Always-on display

- **Wallpaper on always-on display**, in Wallpaper & style → Lock screen, keeps
  your lock screen photo on the always-on display, dimmed, as on iOS. Below it:
  - **Dots** draws it as sparse white dots on black, only the photo's subject
    where the depth effect found one: about 8-12% of the screen lit, for the
    least power.
  - **Black & white** dims it in greys.
  - **Always-on brightness** sets it from 5% to 60%.
- Everything shifts a little every minute, so no pixel stays lit, to protect
  the screen from burn-in.
- It needs **Always show time and info** on, and uses more battery than a black
  always-on display.

## Clock

- The digits **roll** to each new minute, on the lock screen and the always-on
  display.
- Moving between the small and large clock is a smooth spring rather than a
  jump.
- With the depth effect on, the time comes in front of the subject when the
  subject would hide more than half of it, so it can always be read.

## Unlock

- The time **flies to the status bar** on every unlock: without a smartspace
  card, and on a quick flick too.
- The flight is smoother from the first touch to the landing, and starts at
  once: it is got ready while the lock screen is up.

## Fixes

- Waking from the always-on display, the time no longer shows through the
  subject before going behind it.
- Previewing a new photo in Wallpaper & style no longer shows the current
  photo's subject over the preview.
- No faint box around the time as the lock screen wakes.

## Requirements

- Pixel on Android 17
- Vector (JingMatrix/Vector) or another framework with libxposed API 101 or newer
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after updating
