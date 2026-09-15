# Changelog

## 0.0.2

### Added

- A notification while the depth effect is being prepared for a new lock
  screen photo, so the few seconds before the subject shows in front of the
  clock are not mistaken for the effect not working. Its icon shows in the
  status bar and it sits with the notifications, without sound, vibration or a
  pop-up, and cannot be swiped away until the subject is found. It then reads
  "Depth effect ready" and goes by itself after five seconds, or says the
  effect is unavailable, in which case the photo is tried again the next time
  the lock screen shows it. A photo whose subject was already found shows no
  notification. It is the "Depth effect" channel in System UI's notification
  settings.

### Changed

- On the always-on display the iOS time is drawn in outline, a thin line
  around each digit's outer edge, instead of solid, so its wide strokes do not
  keep the same pixels lit. It fades between solid and outline as the display
  dozes and wakes.

- The depth effect finds the subject with BiRefNet_lite instead of U²-Net lite.
  It looks at the photo at 1024 by 1024 instead of 320 by 320, so the whole
  subject is kept, hair and thin edges included; U²-Net lite could keep only
  part of it. The APK grows by about 87 MB, and finding the subject of a new
  photo takes about 12 seconds on a Pixel 8 Pro, using up to 2.3 GB of memory
  while it runs. The model is this project's own ONNX export
  (`scripts/export_subject_model.py`), which runs three times faster in a third
  of the memory of the published export. Masks the old model found are deleted.

### Fixed

- The status icons no longer jump sideways when the status bar takes them over
  after an unlock: the lock screen spaces its status icons further from the
  battery, so the picture carried through the unlock now lands the icons and
  the battery each on their own place.
- The status icons no longer blink out for a frame as an unlock begins.

## 0.0.1

First release on GitHub.

### Added

- **iOS** lock screen clock style, listed in Wallpaper & style → Clock beside
  Google's clocks. The large face is the date over a large, centred time, set
  in Roboto Flex and pinned under the status bar, with SystemUI's own date line
  hidden so the date is not shown twice; the small face is the time alone,
  centred. Follows the wallpaper's light or dark text colour, dims in always-on
  display and moves for burn-in protection.
- **Font** slider for the iOS clock, in Wallpaper & style → Clock → Style:
  Roboto Flex or Inter 4.1, the open font closest to SF Pro, bundled under the
  SIL Open Font License. The preview switches as the slider moves.
- **Clock size** slider for the iOS clock, in Wallpaper & style → Clock → Size,
  which replaces the Large switch for this clock (the large clock always shows
  when notifications allow); Google's clocks keep their switch and show no
  slider. Six steps, each taller than the last in either font: the numerals move to a bolder cut, so strokes stay even, and the rest
  of the height comes from stretching them vertically. The colon stays round
  and centred between the digits. The preview follows the slider as it moves,
  and Apply stores the size and the font together with the clock setting, so
  choosing one keeps the other. Moving the slider alone enables Apply, which
  greys out again once the size and font are back to what is stored.
- With notifications showing, the iOS clock is the time alone at the smallest
  size step, with Pixel's date and weather line centred below it, spaced
  evenly between the clock and the notifications.
- On unlock, the iOS time flies to the status bar clock: it rises and shrinks,
  curves toward the clock below any camera cutout, turns into the status bar
  clock's own text as it arrives, and stays until SystemUI shows that clock.
  A swipe drags it and takes it back if let go.
- As the last notification goes or the first arrives, the iOS time moves and
  resizes in one piece between the small and large clock, instead of blinking
  out and back in; the date row below the small clock fades rather than
  vanishing, and the smartspace card glides instead of jumping.
- With the depth effect on, the time's flight to the status bar starts behind
  the photo's subject, as the lock screen showed it, and comes out from behind
  it on the way.
- The status icons and battery on the right of the lock screen status bar stay
  put through an unlock instead of fading out and back in with the status bar.
- After an unlock, the notification icons and chips beside the status bar
  clock grow in one after another on Material 3 Expressive springs instead of
  reappearing.
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
- **Depth effect**, switched on at the bottom of Wallpaper & style's Lock
  screen list: the subject of a still lock screen photo is found on the device
  (U²-Net lite on ONNX Runtime, once per photo) and drawn in front of the large
  clock's time, placed exactly where the window manager puts the wallpaper and
  following its scroll, zoom and dimming.
