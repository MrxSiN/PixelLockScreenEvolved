# Changelog

## 0.2.0

### Added

- **Font** slider for the iOS clock, in Wallpaper & style → Clock → Style:
  Roboto Flex or Inter 4.1, the open font closest to SF Pro, bundled under the
  SIL Open Font License. The preview switches as the slider moves.
- **Clock size** slider for the iOS clock, in Wallpaper & style → Clock → Size,
  which replaces the Large switch for this clock (the large clock always shows
  when notifications allow); Google's clocks keep their switch and show no
  slider. Six steps, each taller than the last in either
  font: the numerals move to a bolder cut, so strokes stay even, and the rest
  of the height comes from stretching them vertically. The colon stays round
  and centred between the digits. The preview follows the slider as it moves,
  and Apply stores the size and the font together with the clock setting, so
  choosing one keeps the other.
- With notifications showing, the iOS clock is the time alone at the smallest
  size step, with Pixel's date and weather line centred below it, spaced
  evenly between the clock and the notifications.
- On unlock, the iOS time flies to the status bar clock: it rises and shrinks,
  curves toward the clock below any camera cutout, turns into the status bar
  clock's own text as it arrives, and stays until SystemUI shows that clock.
  A swipe drags it and takes it back if let go.
- The status bar, date, time and smartspace card are spaced evenly, 40dp from
  the ink of each to the next.
- With no notifications, the smartspace card (weather forecast, events) sits
  below the large iOS clock instead of at the bottom of the screen.
- The picker's preview no longer draws Pixel's date line over the large iOS
  clock; it matches the lock screen, where the clock writes its own date.
- The picker's Style slider is labelled "Font" for this module's clocks and
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
