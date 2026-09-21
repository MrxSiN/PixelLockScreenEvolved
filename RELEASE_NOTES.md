# Pixel Lock Screen Evolved v0.0.5

**Fully compatible with the latest Android release: Android 17 QPR1 (September
2026, `CP3A.260905.009`), tested on a Pixel 8 Pro.** QPR1 moved the lock screen
into a Compose scene and dropped a number of older clock APIs. Every feature in
this release runs on that scene, and a new **Compatibility & diagnostics**
report shows each hook resolving on your own build.

## Android 17 QPR1

- The lock screen's Compose scene hosts each clock face through an
  `AndroidViewHolder` whose placement no view matrix carries, so the depth
  effect's subject came out scaled and shifted over the wallpaper. The holder's
  own Compose coordinates are read instead, and the face is re-pinned before
  every draw the scene moves anything in.
- The date row and smartspace card, which the scene lays out above the clock,
  are stacked back under a large module clock.
- Seven removed legacy clock-transition APIs are no longer reported as failed
  hooks: QPR1's scene owns clock-size motion, while older builds keep the
  module's own.
- Compatibility checks select either the Compose or the legacy smartspace path,
  and `ClockRegistry.querySettings` is reported as QPR1's active entry point
  rather than as a fallback.

## Clock styles

- Four styles beside iOS, each with Google's Style, Color and Size tabs:
  **Stacked**, **Numerals**, **Analog** and **Split**.
- **Split** is one heavy line drawn down the screen, stretched until it meets
  the photo's subject and a little past it, so the subject stands in front of
  the ends of the numerals. It is laid out again whenever the photo or its
  scroll changes. With no subject found, or the depth effect off, it reaches
  toward the middle of the screen.
- Every large face writes its own date, so SystemUI's date and weather row is
  never shown twice.

## Clock face width

- The width slider now steps through the same seven cuts as Google's own flex
  clock: `wght` 200 at `wdth` 120 through to `wght` 800 at `wdth` 30, widest
  and lightest first. The numerals grow heavier as they narrow, as stock's do.
- A narrower stop now draws a narrower time. Every width of one size is set at
  the size the widest cut needs, so the same size is the same height at every
  width instead of filling the same block of screen in a different cut.

## Always-on display

- The rows under a small module clock are spaced as SystemUI spaces its own: a
  small face is given the band the lock screen lays its own small clock out in,
  so the date row and the notifications below it keep their distances.
- Module clocks use SystemUI's stock always-on placement, which leaves room for
  the fingerprint sensor.

## Diagnostics and Safe Mode

- **Compatibility & diagnostics** in Wallpaper & style → Lock screen: every
  host class, method and field the module needs, per feature, and what moved
  when one is missing.
- A feature whose required contract is missing never installs its hooks; the
  rest keep working.
- Repeated crashes in a hooked process turn every hook off and post a
  notification, rather than leaving a lock screen or picker that cannot be used.

## Fixes

- The depth effect no longer loses the photo it is cutting the subject from.
  Scaling a photo already the size of the wallpaper surface handed the engine's
  own bitmap straight back, and the engine recycled it long before segmentation
  returned, so the first photo after a restart ended in **No subject found**.
- The depth effect's subject keeps step with the photo it is cut from.
- Module clocks stay in the picker's own section, before the divider.

## Removed

- The **Pixel Minimal** and **Editorial** clock styles. A device set to either
  goes back to the default clock.
- The clock's unlock flight to the status bar, and the overlays that carried
  the status icons through an unlock.
- The bundled Inter, One UI (Manrope) and Nothing (Doto) fonts; the clocks use
  the system's Roboto Flex.

## Requirements

- Pixel on Android 17 QPR1
- Vector (JingMatrix/Vector) v2.2+, or another framework with libxposed API 102
- Scope: System UI (`com.android.systemui`) and Wallpaper & style
  (`com.google.android.apps.wallpaper`); restart both after updating
