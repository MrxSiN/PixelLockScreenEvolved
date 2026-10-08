# Changelog

## 0.0.6 — 2026-10-09

### Added

- Clock position: a **Position** tab in Wallpaper & style → Clock for the
  module's movable clocks. **Adaptive position** stands the clock where it
  hides least of the wallpaper's subject; off, the clock is dragged in the
  preview itself, snapping to the top, the quarters and the bottom, and kept
  clear of the fingerprint sensor and the smartspace card. Stored as a share
  of the screen in the clock setting.
- Double tap to turn off screen: double tap an empty space on the lock screen
  to turn the screen off. SystemUI already carries Google's gesture with its
  flag off; the module sets that flag from the stock `double_tap_to_sleep`
  setting (default on) and adds no gesture of its own. Off under touch
  exploration, and left alone where SystemUI turns the flag on itself.
  Switch in Wallpaper & style → Lock screen. The screen goes dark as a circle
  closing on the tapped point (SystemUI's light reveal scrim, as Pixel
  Launcher Evolved's home screen double tap does).
- `CP3A.261005.005` recorded and verified; the `CP3A.260905.009` description
  predates the double tap contracts and is retired.
- Host compatibility tests: every host contract is checked in CI against a
  recorded description of each verified Pixel build (class, field and method
  names only, under `app/src/test/resources/hosts/`), starting with
  `CP3A.260905.009`. The hosts write these descriptions themselves on request
  (`scripts/capture-host-fixture.sh`).

### Changed

- The iOS clock is set in the Pixel clock's own font, the one Google's default
  clock loads (`google-sans-flex-clock`, its proportional figures), on one
  line. The "Clock face width" slider moves the same seven cuts as before.
- The iOS time always spans the lock screen's text column, from the edge the
  date row and the smartspace card start at to the one they end at, every
  minute. The size slider sets only its height: from the Pixel's own large
  clock to three times it, the numerals stretched vertically, the colon's dots
  kept round. The face beside notifications is the smallest size. The time may
  run past the scene's clock box, which is narrower and shorter than it, and is
  not clipped there. The rows around it keep one gap: status bar to date,
  date to time, and, beside notifications, time to SystemUI's date row to the
  smartspace card.
- Beside notifications, the lock screen's small-clock band is made as tall as
  the module's small face (SystemUI caps it at `small_clock_height`), so the
  date row, the card and the notifications stack below the time instead of
  under it.
- On the always-on display a tall face is lifted to stand above the
  fingerprint sensor, where SystemUI's own position would have it reach down to
  it, and shrunk to the screen's text margins, where the scene's larger
  always-on scale ran it past the screen's edges.
- The iOS time's characters are spaced by their ink, one gap between every
  pair, so the minutes no longer sit tighter than the hour.
- The iOS clock's size slider has four stops (the largest the old fourth);
  other styles keep six. Each style now says how many it has.
- As the clock changes size on Android 17's composed lock screen, the
  smartspace card glides to its new place instead of jumping, and SystemUI's
  date row fades in and out with the clock; the two faces crossfade as
  Google's do there. Carrying the time across was tried and dropped: the
  scene fades one face out before the other in, so the carried time only slid
  into place after the fade.
- The iOS time no longer resizes part way through a wake: it is fitted to the
  scene's scale only once that scale has held for a quarter second.
- Its minute roll travels 15% of the numerals' height, not 45%, so a tall
  time's old digits no longer rise past it and get cut off flat.
- In Wallpaper & style, the size and width sliders glide the time to each
  step on the expressive spatial spring instead of swapping it.
- Switching between the Size and Position tabs, whose content shares one
  slot, crossfades the rows and glides the sheet's height instead of snapping.
- Clock → Position is the last tab (Style | Color | Size | Position) and holds
  only the Adaptive switch. The Clock position row is gone: with Adaptive off,
  "Drag the clock to reposition it", with a fingertip-and-arrows icon, stands
  beside the preview and the preview clock is dragged.
- The depth effect's segmentation keeps only the newest waiting photo: the run
  under way finishes, and requests it replaces are dropped, so several
  wallpaper changes in a row cost at most two runs.
- Binding the segmentation service needs `STATUS_BAR_SERVICE`, which SystemUI
  holds and no downloaded app can, on top of the existing check of the sending
  uid; messages from anyone else are now dropped unanswered, before their data
  is read.
- The README keeps to screenshots, supported builds, install and recovery;
  implementation notes moved to `docs/`.
- A second inference in one segmentation session, as a careful look or a
  retry makes, peaks at 2.3 GB instead of 4.4 GB: ONNX Runtime's memory
  pattern planning is off. The masks found are byte-identical.
- The lock screen's per-frame work (the depth effect's placement, the large
  clock's pin, the smartspace placement) allocates about 4 objects per
  callback instead of about 48, and the clock faces' digits draw without
  allocating. Measurements are in `PERFORMANCE.md`.

### Fixed

- The Size tab could not be tapped: adding or removing the Position tab left
  a tab view behind, drawn over its neighbours and taking their taps, and a
  tap on Size while Position was open did nothing.
- The clock size slider's label was announced as "Clock face width", and its
  row stood lower in the sheet than its padding below.
- Opening Position from Style or Color first grew the sheet to the size rows'
  height, the Adaptive row at its top, then snapped to its own; its height is
  now recorded before the sheet animates, as the picker's own tabs are.

## 0.0.5 — 2026-09-22

### Added

- Four clock styles beside iOS, each with Google's Style, Color and Size tabs:
  **Stacked** (hour over minute, flush to the start, date below),
  **Numerals** (giant Roboto Flex figures whose minute grows heavier and wider
  as the hour passes), **Analog** (a dial with rounded hands, only the hands in
  outline on the always-on display) and **Split** (the time drawn down the
  screen until it meets the depth effect's subject, and a little past it, so
  the subject stands in front of the ends of the numerals). Every face writes
  its own date where its design puts it, and SystemUI's own date row is hidden
  beside it.
- Custom clocks take their colour from the Color tab's seed and tone, as
  Google's clocks do, on the lock screen, in the preview and dozing.
- **Depth effect page**: whether the wallpaper's subject was found, and
  **Reprocess wallpaper** / **Try again**, which forgets the mask kept for the
  photo and looks again more carefully, without clearing app data.
- **Always-on display page**: **Show lock screen wallpaper** and one
  **Appearance** (Color, Black & white or Dots). The old Dots and Black &
  white switches are read once, so a choice already made carries over.
- Each feature hooks through hooks of its own. One that keeps failing at run
  time, or fails to install, is stopped alone until its host restarts, and the
  compatibility page and its entry say so; every other feature carries on.
- The compatibility page says whether the system build is one the module was
  tried on; on any other, each feature is still checked on its own.
- The module's own motion (the depth effect's subject easing in front of the
  time, and the clock carried between sizes) follows **Remove animations** and
  the animator duration scale.
- The module's pages in Wallpaper & style open and close with Android 17
  Settings' shared X axis motion (the page slides in from a quarter of the
  width as the picker slides away, 450ms on the emphasized curve), and back
  closes them the same way. **Depth effect** carries Settings' chevron and
  divider before its switch, marking that it opens a page.
- The new clocks move through Roboto Flex's axes, as Google's clocks do: their
  strokes thin to a light cut on the always-on display and fill out again on
  waking, grow in as the lock screen appears, and thin and fill once as the
  charger is plugged in. Their digits roll at each minute, as the iOS clock's
  do. Analog's hands step on each minute with a little spring, sweep in as the
  lock screen appears, and nudge as the charger is plugged in.

- **Compatibility & diagnostics**, at the end of Wallpaper & style's Lock screen
  list: the module's and Android's versions, SystemUI's and Wallpaper & style's
  versions, the libxposed API and framework, whether each feature is compatible,
  how many required host APIs resolved, which fallbacks were used, which
  optional APIs are absent, and every contract checked, with
  a copyable report. Each host checks the members its features need as it
  starts, and logs the whole report the first time it runs on a new version.
- A feature whose required members are gone is no longer installed. Its switch
  in Wallpaper & style is greyed out and says it is unavailable on that SystemUI
  and to update the module, and the iOS clock is not offered while SystemUI
  cannot draw it.
- **Safe Mode.** Three SystemUI (or Wallpaper & style) crashes within a minute
  hold this module's features in that process off on the next start. SystemUI
  posts a notification, and Wallpaper & style asks as it opens, whether to
  **Restore feature** or **Keep disabled**.
- On the always-on display the large clock first comes in front of the depth
  effect's subject, then moves to Pixel's fingerprint-aware clock region.
  Waking reverses that order: it returns before the subject stands in front again. A
  notification arriving on the always-on display carries it to the small clock.

### Changed

- The Lock screen list's four switches became two entries, **Depth effect**
  (with its switch) and **Always-on display**, each opening a page.

- The iOS clock is set in Roboto Flex alone. Its Style slider is the picker's own
  **Clock face width**, the same seven cuts Google's flex clock steps through,
  the middle one by default.

- **Split**'s thumbnail shows the time split by a cut-out figure, so the style
  reads apart from iOS in the clock list rather than as a second plain line.
  Its small face, shown beside notifications, stands taller than a Pixel small
  clock's figures: dropping to the usual height read as the clock shrinking
  away from a large face that fills the screen.
- **Split** is now one heavy line drawn down the screen rather than an hour and
  a minute stepped across it. It reads where the depth effect's subject stands
  and stretches until it meets it, and a little past it, so the subject stands
  in front of the ends of the numerals. With no subject found, or the effect
  off, it reaches toward the middle of the screen.

### Fixed

- Every module clock now stays in the picker's section beside iOS, before the
  divider, instead of fixed-width clocks being mixed among Google's clocks.
- Module clocks now use SystemUI's stock always-on placement, which leaves room
  for the fingerprint sensor, instead of forcing the time to screen centre.
- At the largest clock size, every **Clock face width** now has the same numeral
  height; the widest face no longer becomes shorter than the narrowest face.
- **Clock face width** now changes how wide the iOS clock is drawn, as it does
  on Google's clocks. Every width of one size is set at the size the widest
  width needs, so a narrower width draws a narrower time instead of the same
  block of the screen in a different cut.
- The width slider now steps through the same seven cuts as Google's own flex
  clock, read from its `FlexClockController.BASE_PRESETS`: `wght` 200 at `wdth`
  120 through to `wght` 800 at `wdth` 30, widest and lightest first. The
  numerals grow heavier as they narrow, as stock's do, instead of holding one
  weight across a wider range of widths than the font is drawn for.
- The depth effect no longer loses the photo it is cutting the subject from.
  Scaling a photo already the size of the wallpaper surface hands the engine's
  own bitmap straight back rather than copying it, and the engine recycled it
  long before segmentation returned, so the first photo after a restart ended
  in **No subject found** instead of a cut-out.
- The rows under a small module clock are spaced as SystemUI spaces its own.
  A module face is only as tall as its numerals, so the date row came up to
  meet it while the notifications below stayed put, leaving the clock, the date
  and the notifications unevenly gapped on the always-on display; a small face
  is now given the band SystemUI lays its own small clock out in.
- Android 17 QPR1 no longer reports seven removed legacy clock-transition APIs
  as failed hooks. Its Compose scene owns clock-size motion, while older builds
  keep the module's legacy motion hooks.
- Compatibility checks now select either the Compose or legacy smartspace path,
  so QPR1 no longer counts two removed optional constraint APIs against its total.
- SystemUI's QPR1 `ClockRegistry.querySettings` entry point is reported as its
  active API instead of as a fallback for the removed `registerListeners`.

### Removed

- The **Pixel Minimal** and **Editorial** clock styles. A device set to either
  goes back to the default clock.
- The Inter, One UI (Manrope) and Nothing (Doto) fonts.
- The clock's unlock flight to the status bar clock, and the overlays that held
  the status icons and brought in the notification icons through an unlock.

## 0.0.4

### Changed

- The always-on wallpaper's dots are lit nearly as brightly as the clock beside
  them, and the brightness they are given is spent on their width instead of on
  their grey: the always-on display holds greys unsteadily, and dimmed dots
  flickered. A dot narrower than a pixel now lights nothing at all. About 4% of
  the screen's pixels are lit where 11% were, for the same light and the same
  power, and the photo's black is black again.

- **Black & white** sits above **Dots** in Wallpaper & style's Lock screen list.

### Removed

- The **Always-on brightness** slider. The always-on wallpaper keeps a quarter
  of the photo's light, which is what the slider started at.

### Fixed

- The depth effect's subject no longer lights up as a block in the time's
  bounds while the display wakes or goes to sleep. The light reveal scrim was
  drawn over the subject through the wrong transform, so it landed off the
  subject entirely and darkened nothing; the subject kept the photo's full
  brightness while the photo itself was still blacked out.

- The always-on wallpaper is drawn over the subject as well as behind the lock
  screen, so it no longer stops at the subject's edges: no box around the time
  on the always-on display, and no seam across the screen as it wakes.

- Nothing is drawn in front of the time on the always-on display. The subject
  is gone by the time the display has dozed, where it used to sit over the
  clock and break its outline.

- The subject goes as soon as an unlock is committed. It is the module's own
  copy of the photo, drawn inside the keyguard, so a launcher that blurs the
  wallpaper on unlock left it sharp, as a clear block in the time's bounds
  while everything around it blurred.

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
