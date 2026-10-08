# Pixel Lock Screen Evolved v0.0.6

**Fully compatible with Android 17 QPR1 (October 2026, `CP3A.261005.005`),
tested on a Pixel 8 Pro.**

## iOS clock

- Set in the Pixel clock's own font, Google Sans Flex Clock (the first clock in
  the picker), with its proportional figures, on one line.
- The time always spans the lock screen's text column, edge to edge with the
  date and smartspace. The size slider sets only its height: four steps, from
  Google's own large clock to 2.2 times it. Every pair of figures stands the
  same gap apart.
- Beside notifications, the small face is the stock large clock's height, and
  SystemUI's date row, smartspace and notifications are moved below it with
  even spacing.
- On the always-on display the clock is fitted inside the screen margins and
  kept clear of the fingerprint sensor.

## Clock position

- New **Position** tab (Wallpaper & style → Clock), the last one: **Adaptive
  position** keeps the clock clear of the wallpaper's subject; off, drag the
  clock in the preview, as a hint beside it says.

## Double tap to turn off screen

- Double tap an empty space on the lock screen to turn the screen off:
  Google's own gesture, which SystemUI carries switched off. Switch in
  Wallpaper & style → Lock screen.

## Motion

- The size and width sliders glide the preview to each step; Size ↔ Position
  crossfades and glides the sheet.
- No resize part way through a wake; the minute roll no longer runs off the
  top of a tall time; the smartspace card glides and the date row fades as the
  clock changes size.

## Fixes

- The Size tab could not be tapped after the Position tab came or went.

See `CHANGELOG.md` for the full list.
