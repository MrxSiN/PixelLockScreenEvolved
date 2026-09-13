# Changelog

## 0.2.0

### Added

- **Clock size** slider for the iOS clock, in Wallpaper & style → Clock →
  Style. Seven steps run from the classic iOS clock, which is the smallest, to
  the largest iOS lock screen clock: the numerals grow taller, lighter and
  narrower until they are thin, straight-sided figures of even stroke with a
  round colon. Roboto Flex's parametric axes narrow the counters and thin the
  strokes, and the numerals are then stretched vertically, measured against the
  font at runtime so every size lands on the iOS proportions. A time too wide
  for the screen is drawn smaller rather than squeezed. The picker's preview
  follows the slider as it moves, and the chosen step is stored with the clock
  setting, so it survives reboots and the slider reopens on it.
- With notifications showing, the iOS clock is the time alone at the second
  size step, with Pixel's date and weather line below it and the notifications
  below that.
- The picker's slider is labelled "Clock size" for this module's clocks and
  keeps Google's "Clock face width" for Google's own.
- GitHub Actions workflow that runs the project checks and unit tests, builds a
  signed release APK, and attaches it to a GitHub Release for `v*` tags.

### Changed

- The iOS time is set in Roboto Flex and drawn by the clock itself, so the
  view is exactly as tall as the numerals and the date sits right above them.
  The smallest size looks as before.

## 0.1.0

### Added

- **iOS** lock screen clock style, listed in Wallpaper & style → Clock beside
  Google's clocks. The large face is the date over a large, centred time pinned
  under the status bar, with SystemUI's own date line hidden so the date is not
  shown twice; the small face is the time alone, centred. Follows the
  wallpaper's light or dark text colour, dims in always-on display and moves for
  burn-in protection.
