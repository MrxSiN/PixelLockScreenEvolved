# Changelog

## 0.2.0

### Added

- **Clock size** slider for the iOS clock, in Wallpaper & style → Clock →
  Style. Six steps, each taller than the last in the same bold numerals: the
  time grows until it fills the screen's width, then is stretched vertically.
  The font's weight, width and strokes never change, so the largest clock is
  as heavy as the smallest; horizontal strokes are thinned on Roboto Flex's
  axis by as much as the stretch thickens them, and the colon stays round. The
  picker's preview follows the slider as it moves, and the chosen step is
  stored with the clock setting, so it survives reboots and the slider reopens
  on it.
- With notifications showing, the iOS clock is the time alone at the smallest
  size step, with Pixel's date and weather line centred below it, spaced
  evenly between the clock and the notifications.
- The picker's preview no longer draws Pixel's date line over the large iOS
  clock; it matches the lock screen, where the clock writes its own date.
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
