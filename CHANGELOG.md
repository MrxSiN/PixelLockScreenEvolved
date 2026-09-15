# Changelog

## 0.0.3

### Added

- **Wallpaper on always-on display** in Wallpaper & style's Lock screen list,
  below Depth effect, with a **Dots** switch, a **Black & white** switch and an
  **Always-on brightness** slider from 5% to 60%. The always-on display then
  keeps the lock screen photo, placed exactly where it sits on the lock screen,
  dimmed, instead of turning black, as on iOS. Switches that only matter while
  another is on are greyed out until it is. Dots draw the photo as sparse white
  dots on black, bigger where it is lighter; where the depth effect has found
  the photo's subject, only the subject is dotted. On a Pixel 8 Pro dots lit
  about 12% of the screen's pixels, and 8% with the subject alone, where the
  dimmed photo lights them all. To protect the screen from burn-in, the photo
  drifts a few pixels and the dots' grid steps through its cell every minute,
  so no pixel stays lit; from one minute to the next, only about 6% of the lit
  dot pixels stay lit. It fades in as the display dozes and out as it wakes, while
  the photo is revealed beneath it. It needs the always-on display turned on,
  and uses more battery than a black one.

- The iOS time rolls to each new minute, on the lock screen and the always-on
  display: each digit that changes rises and fades while its new one comes up
  from below, and the digits that stay glide to their new places when the
  time's width changes, as from 9:59 to 10:00.

### Changed

- With the depth effect on, the time stays in front of the subject when the
  subject would hide more than half of it, as on iOS, so it can still be read.
  The share is measured on the time's own digits once the lock screen holds
  still, and again as the minute, size or font changes.

- The time moves between the small and large clock on a Material 3 Expressive
  spring that sets off from rest, instead of an easing that covered most of the
  way in the first few frames and crawled for the rest. The date and the depth
  effect's subject fade in around it, where the subject used to appear at once.

- The time flies to the status bar clock on unlock even when the lock screen
  shows no smartspace card, and on a fast swipe that has already faded the lock
  screen, starting as faint as the time was and coming up to full as it flies.
  It used to stay still in both cases.

- The flight to the status bar clock is smoother. One spring carries it from
  the swipe to the landing, firming up gently when the unlock is committed
  instead of switching to a timed path; the time shrinks by a steady ratio
  instead of seeming to collapse at the end; it arrives still gently moving
  instead of creeping the last pixels and snapping; and a frame SystemUI is too
  busy to draw no longer makes it leap.

- The flight is got ready while the lock screen is up: its window is added and
  its pictures are drawn once the display is awake, and drawn again only when
  the minute, the clock or the depth effect's subject changes. Starting it on
  unlock takes about half a millisecond instead of about 20, and the time is on
  its way two or three frames later instead of waiting some 46ms for a new
  window. The window that holds the status icons through an unlock is kept
  ready the same way. It is all let go while the display dozes.

### Fixed

- The depth effect's subject no longer brings a faint box around the time into
  view as the lock screen wakes. The subject mask's faint haze around the
  subject is dropped.

- Previewing a new photo in Wallpaper & style no longer shows the current lock
  screen photo's subject over the preview's time. The lock screen tab's preview
  of the photo already set still shows its depth effect.

- Waking from the always-on display, the time no longer shows through the
  depth effect's subject, looking brighter and in front of it, before sinking
  behind it. The subject used to fade in by one amount as the photo was
  revealed; it is now darkened pixel for pixel as SystemUI's light reveal
  darkens the photo beneath it, so it stays solid in front of the time and
  appears with the photo around it.

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
