# Pixel Lock Screen Evolved

Xposed module that adds lock screen clock styles to Pixel phones on Android 17.
Styles appear in **Wallpaper & style → Clock** next to Google's own clocks, and
render on the lock screen, in the picker preview and on the always-on display.

## Styles

- **iOS** — the date over a large, centred time pinned under the status bar.
  12-hour time without AM/PM or a leading zero; 24-hour time padded.

  **Clock size** slider (Wallpaper & style → Clock → Style, under the clock
  list): six steps. Each is taller than the last, in the same bold numerals:
  the time grows until it fills the screen's width, then is stretched
  vertically without getting any thinner. With notifications showing, the
  clock is the time alone at the smallest step, with Pixel's date and weather
  centred below.

## Requirements

- Android 17 (SDK 37) Pixel
- Vector (JingMatrix/Vector) or another framework implementing libxposed API 101+
- Scope: `com.android.systemui`, `com.google.android.apps.wallpaper`

## Build and install

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/PixelLockScreenEvolved-v0.2.0.apk
adb shell su -c "/data/adb/lspd/cli modules enable io.github.mrxsin.pixellockscreenevolved"
adb shell su -c "/data/adb/lspd/cli scope set io.github.mrxsin.pixellockscreenevolved com.android.systemui/0 com.google.android.apps.wallpaper/0"
adb shell su -c 'am force-stop com.google.android.apps.wallpaper; kill $(pidof com.android.systemui)'
```

Logs: `adb logcat -s PixelLockScreenEvolved`.

## Adding a style

1. Implement `clock.ClockStyle` (and a `ClockFace`) in its own package under `clock/`.
2. List it in `clock.ClockStyles.all`.
3. For a size slider, set `isResizable = true` and make its faces `ResizableClockFace`.

Nothing in `host/` changes: it adapts any `ClockStyle` to SystemUI's clock
plugin interfaces. See `HOOK_NOTES.md` for the SystemUI internals involved.

Disabling the module falls back to the default clock; nothing is written
except the clock choice the picker itself stores.

## Releases

Signed APKs are built by GitHub Actions; see `PUBLISHING.md`.

## License

GPL-3.0. See `LICENSE` and `NOTICE.md`.
