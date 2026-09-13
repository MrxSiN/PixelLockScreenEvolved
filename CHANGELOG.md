# Changelog

## 0.2.0

### Added

- **Clock size** slider for the iOS clock, in Wallpaper & style → Clock →
  Style. Seven steps run from the classic iOS clock, which is the smallest, to
  the largest iOS lock screen clock: numerals that grow taller and, once they
  would run past the screen's edges, narrow on Roboto Flex's width axis instead
  of shrinking. The picker's preview follows the slider as it moves, and the
  chosen step is stored with the clock setting, so it survives reboots and the
  slider reopens on it.
- The picker's slider is labelled "Clock size" for this module's clocks and
  keeps Google's "Clock face width" for Google's own.
- GitHub Actions workflow that runs the project checks and unit tests, builds a
  signed release APK, and attaches it to a GitHub Release for `v*` tags.

### Changed

- The iOS time is set in Roboto Flex. The smallest size looks as before; the
  date now sits closer to the time, as on iOS, because the font's unused line
  space above and below the numerals is trimmed.

## 0.1.0

### Added

- **iOS** lock screen clock style, listed in Wallpaper & style → Clock beside
  Google's clocks. The large face is the date over a large, centred time pinned
  under the status bar, with SystemUI's own date line hidden so the date is not
  shown twice; the small face is the time alone, centred. Follows the
  wallpaper's light or dark text colour, dims in always-on display and moves for
  burn-in protection.
