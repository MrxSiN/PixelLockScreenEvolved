<div align="center">

<img src="docs/icon.svg" width="120" alt="Pixel Lock Screen Evolved">

# Pixel Lock Screen Evolved

**The lock screen clock the Android 17 Pixel does not ship — picked where you already choose one.**

Wallpaper & style → **Clock** → the iOS clock, beside Google's own.

<br>

[![Release](https://img.shields.io/github/v/release/MrxSiN/PixelLockScreenEvolved?include_prereleases&color=5B3DF5&label=release&style=for-the-badge)](https://github.com/MrxSiN/PixelLockScreenEvolved/releases)
[![Downloads](https://img.shields.io/github/downloads/MrxSiN/PixelLockScreenEvolved/total?color=3DDC84&logo=android&logoColor=fff&style=for-the-badge)](https://github.com/MrxSiN/PixelLockScreenEvolved/releases)
[![Android](https://img.shields.io/badge/Android-17-3DDC84?logo=android&logoColor=fff&style=for-the-badge)](https://developer.android.com)
[![Licence](https://img.shields.io/github/license/MrxSiN/PixelLockScreenEvolved?color=5B3DF5&style=for-the-badge)](LICENSE)

</div>

---

## Why it works this way

Most lock screen modules draw a clock over SystemUI's and hide the real one.
This one does not. Its clocks are added to SystemUI's own clock registry, the
same place Google's clocks come from, so SystemUI lays them out, dims them for
the always-on display, moves them for burn-in and previews them itself — and
Wallpaper & style lists them, previews them and stores the choice as it does
for any clock.

|  | |
|---|---|
| 🎯 **Native** | Chosen, previewed and applied in Wallpaper & style. No settings app, no second preview. |
| 🌗 **Everywhere a clock is** | The lock screen, the picker preview and the always-on display, following the wallpaper's colours. |
| 🔙 **Leaves nothing behind** | Nothing is written but the clock choice the picker itself stores. Disabling the module falls back to the default clock. |
| 🧩 **One contract** | A clock style knows nothing of SystemUI; `host/` adapts any `ClockStyle` to SystemUI's clock plugin interfaces. |
| 🌊 **Motion, not jumps** | Size changes, unlocking and the status bar move on springs, following SystemUI's own timing. |

---

## Features

<details open>
<summary><b>🕘 iOS clock</b></summary>
<br>

The date over a large, centred time pinned under the status bar. 12-hour time
without AM/PM or a leading zero; 24-hour time padded. The status bar, date,
time and smartspace card are spaced evenly, and with no notifications the
smartspace card sits below the clock.

| Control | Where | What it does |
|---|---|---|
| **Font** | Clock → Style | Roboto Flex, or Inter — the open font closest to Apple's SF Pro, bundled. The preview switches as the slider moves. |
| **Clock size** | Clock → Size | Six steps, each taller than the last, in place of the Large switch. The numerals move to a bolder cut as they grow, so strokes stay even, and fill the screen's width at the largest. Moving the slider alone enables Apply. |

With notifications showing, the clock is the time alone at the smallest step,
with Pixel's date and weather centred below it. As the last notification goes
or the first arrives, the time moves and resizes in one piece between the two,
the date row fades, and the smartspace card glides.

</details>

<details>
<summary><b>🪄 Depth effect</b></summary>
<br>

The subject of a photo wallpaper in front of the large clock, as on iOS: the
time goes behind the subject, and nothing else on the lock screen does.

| Switch | Where | What it does |
|---|---|---|
| **Depth effect** | Wallpaper & style → Lock screen, at the bottom | Finds the subject of the lock screen photo on the device and draws it over the time, placed exactly where the window manager puts the wallpaper and following its scroll, zoom and dimming. |

The subject is found once per photo, by the module's own app, with U²-Net lite
on ONNX Runtime; nothing leaves the device. Live wallpapers are drawn by their
own apps and have no depth effect.

</details>

<details>
<summary><b>🔓 Unlock</b></summary>
<br>

| Motion | What it does |
|---|---|
| **Time to status bar** | The time rises, shrinks and curves toward the status bar clock, below any camera cutout, and turns into that clock's own text as it arrives. A swipe drags it and takes it back if let go. With the depth effect on, it starts behind the subject and comes out from behind it. It flies with the smartspace card, so a lock screen without one keeps it still. |
| **Status icons stay put** | The icons and battery on the right of the lock screen status bar are the ones the status bar shows after it, so they stay where they are instead of fading out and back in. |
| **Notification icons and chips** | Grow in beside the status bar clock one after another on Material 3 Expressive springs, instead of simply reappearing. |

</details>

---

## Requirements

| | |
|---|---|
| **Android** | 17 (API 37) on a Pixel |
| **Apps** | System UI (`com.android.systemui`) and Wallpaper & style (`com.google.android.apps.wallpaper`) |
| **Framework** | [Vector](https://github.com/JingMatrix/Vector) v2.2+, or any framework implementing libxposed API 101+ |
| **Root** | Only what the framework itself needs |

Built against the modern [libxposed API](https://github.com/libxposed/api)
(`io.github.libxposed:api`), not the legacy `de.robv.android.xposed` bridge.

## Install

```
1. Install the APK from Releases
2. Enable Pixel Lock Screen Evolved in your Xposed manager
3. Restart System UI and force-stop Wallpaper & style once
4. Wallpaper & style → Clock → choose the iOS clock → Apply
5. Optional: Wallpaper & style → Lock screen → Depth effect
```

The module declares a **static scope**, so there is nothing to pick. It has no
icon in the app drawer and no screen of its own: every control is in Wallpaper
& style.

---

## How it works

System UI and Wallpaper & style each build their own `ClockRegistry`. Just
before each registry reads which clock is chosen, the module adds its styles to
it, presented as an ordinary `ClockProvider`:

```
ClockStyle ──▶ host/ adapters ──▶ ClockProvider ──▶ SystemUI's ClockRegistry ──▶ lock screen, preview, AOD
                                                └──▶ Wallpaper & style's ClockRegistry ──▶ Clock list, sliders
```

From there each process treats them as its own. The font and size are axis
values in the clock setting the picker already writes, so choosing one keeps
the other and nothing else is stored.

<details>
<summary><b>The depth effect, in detail</b></summary>
<br>

SystemUI draws a still lock screen photo itself. Each time it draws one it has
not drawn before, the photo is taken as drawn, stretched to the wallpaper
surface, and sent to the module's app, which finds the subject's mask with
U²-Net lite and answers only SystemUI. A mask already found is read back from
disk. The subject cut out of the photo becomes a layer beside the large clock
face, placed as `WallpaperController` places the wallpaper — crop, scroll and
zoom — clipped to the time, and faded with SystemUI's scrims so it goes and
comes back with the photo on the way into and out of the always-on display.

</details>

<details>
<summary><b>Unlock motion, in detail</b></summary>
<br>

The keyguard window fades within a few frames of an unlock starting, far sooner
than a move can be followed, and SystemUI only brings the status bar back some
400ms later. So the time and the status icons are carried as pictures in a
window of the module's own, above everything, and handed over to the status
bar's own views once SystemUI shows them. The status bar's own views are never
hidden or faded by the module: such changes reached the screen late and made
the clock blink.

</details>

`HOOK_NOTES.md` records the SystemUI and Wallpaper & style internals each part
relies on, and how they were verified on a device.

---

## Build

```bash
./gradlew clean assembleRelease
```

A local release build is unsigned unless `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_ALIAS`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD`
are set. `scripts/check-project.sh` runs the static invariants that CI enforces,
and `PUBLISHING.md` describes the signed releases GitHub Actions builds.

Release builds are shrunk with R8; the module entry class is kept by name,
because the framework resolves it from `META-INF/xposed/java_init.list` rather
than from any call site.

## Design

```
PixelLockScreenEvolvedModule   Xposed entry: hands each process the patches it needs
clock/                         clock styles and faces; knows nothing of SystemUI
host/                          adapters to SystemUI and Wallpaper & style, one patch per concern
hook/                          the hooking contract, over the libxposed API
depth/                         the app side of the depth effect: subject segmentation
core/                          logging
```

Adding a style means implementing `clock.ClockStyle` and a `ClockFace` in their
own package under `clock/` and listing the style in `clock.ClockStyles.all`. For
a size slider, set `isResizable` and make its faces `ResizableClockFace`; for a
font slider, list `fontNames` and make its faces `FontChoosingClockFace`.
Nothing in `host/` changes.

Every SystemUI clock plugin class is named in `host/ClockPluginApi.kt` alone,
and each other part of SystemUI in one `…Api` class, so a SystemUI update that
moves something is absorbed in one file.

---

<div align="center">

**GNU General Public License v3.0** · see [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md)

</div>
