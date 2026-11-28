# Pixel Lock Screen Evolved v0.0.9

**Fully compatible with Android 17 QPR1 (November 2026, `CP4A.261105.040.A1`),
tested on a Pixel 8 Pro.**

## New clocks

- Hyper, Headline, Poster, One, Horizon, Dot and Cinema replace Stacked,
  Numerals, Analog and Split. A phone that chose a removed style goes back to
  the default clock.
- The iOS clock is set in AXS66Clock, bundled with the module.

## Depth effect

- The subject now stands in front of the small clock beside notifications, and
  in front of Google's own clocks.
- **Depth map**: something as near as the subject and touching it, such as a
  railing, stands in front too. The subject's outline stays as found where
  the model is sure of it.
- **Edit subject**: paint over the photo to add to the subject or erase from
  it. Strokes snap to the photo's own edges.
- **Subjects in front**, **Clock and subject** and **Tilt motion** on the
  Depth effect page.
- Finding a subject no longer makes the phone stutter, and choosing the depth
  map for a photo already looked at takes about a second.

## Smoother

- Waking from the always-on display, every clock moves smoothly at the full
  frame rate. It used to move in steps.
- The subject goes in front of the time and comes away from it with the photo
  itself as the screen wakes and sleeps, so the time is never cut over black
  and never jumps in front.
- Changing clock size, the time slides behind the subject.

## Always-on display

- **Dot size**: seven sizes for the Dots look, with a live preview of your own
  wallpaper.
- The dots grow from the photo and shrink back into it, and no longer flash
  over the lock screen as the screen turns off.

See `CHANGELOG.md` for the full list.
