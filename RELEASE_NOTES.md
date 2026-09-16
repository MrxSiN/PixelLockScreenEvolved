# Pixel Lock Screen Evolved v0.0.4

A steadier always-on display, and the depth effect's subject finally keeping
step with the photo it is cut from.

## Always-on display

- **Dots** are lit nearly as brightly as the clock beside them, instead of
  being dimmed to a grey. The always-on display holds greys unsteadily, and
  dimmed dots flickered. The brightness they are given goes into their width
  instead, so about 4% of the screen is lit where 11% was, for the same light
  and the same power.
- Dots no longer appear where the photo has no subject: the black around it is
  black again.
- **Black & white** now sits above **Dots** in the list.
- No box around the time. The always-on wallpaper is drawn over the depth
  effect's subject as well, so it no longer stops at the subject's edges.
- The clock is always on top. Nothing is drawn in front of the time on the
  always-on display.

## Fixes

- No block of bright photo in the time's bounds while the display wakes or goes
  to sleep. The subject is darkened with the light reveal, pixel for pixel, as
  the photo around it is.
- No seam across the screen as the display wakes: the subject and the photo
  come up as one.
- No sharp block in the time's bounds while unlocking with a launcher that
  blurs the wallpaper.

## Removed

- The **Always-on brightness** slider. The always-on wallpaper keeps a quarter
  of the photo's light, which is what the slider started at.

## Requirements

- Pixel on Android 17
- Vector (JingMatrix/Vector) or another framework with libxposed API 101 or newer
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after updating
