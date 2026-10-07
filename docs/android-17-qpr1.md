# Android 17 QPR1

Android 17 QPR1 (`CP3A.260905.009`, September 2026) moved the Pixel lock screen
into a Compose scene and removed several of the clock APIs the module used on
Android 17 (`CP2A.260805.005`). Every feature runs on the new scene. This page
explains the shape of the port; `HOOK_NOTES.md` ("What Android 17 QPR1
changed") lists each signature.

## Clocks are Compose elements

`ClockFaceLayout` now extends `LockscreenElementProvider`, and SystemUI draws a
face through `getElements()`; it no longer calls `getViews()` or
`applyConstraints()`. A face that returns no elements is created, themed and
ticking, but never drawn. When a module clock is missing and a stock clock
draws fine on the same build, this is the first thing to check.

The module compiles against no Compose at all. SystemUI carries
`DefaultClockFaceLayout$Companion.clockView(View, Modifier, Composer)`, a public
composable that hosts a plain view, so the module builds the same modifier
Google builds and hands it its own view, all by reflection. A
`java.lang.reflect.Proxy` receives the element's composable call and forwards
it (`host/LockscreenElementApi.kt`).

## Placement through graphics layers

The scene hosts each face in an `AndroidViewHolder` and places the holder with
graphics layers that no view matrix carries: the large clock sits at a scale of
0.9 and moves further as the phone wakes. Anything drawn to line up with the
screen, such as the depth effect's subject over the wallpaper, came out scaled
and shifted. The holder's own Compose coordinates are read instead
(`host/ComposedViewPlacement.kt`), and the face is re-pinned before every draw
the scene moves anything in, so the clock never lands a frame late. The date
row and smartspace card, which the scene lays out above the clock, are stacked
back under it the same way.

## What changed with it

- `ClockRegistry.registerListeners` is gone from SystemUI, which reaches its
  clocks through `querySettings()`. `HostContracts.forRuntime` checks whichever
  the running SystemUI uses.
- `ClockSizeTransition` is gone, and the scene animates movable clock elements
  itself, so clock size motion is reported as host managed rather than broken.
- The scene fades the clock element by element, and draws its own scrims.
  The depth effect erases the subject's shape from the face drawn as one layer
  rather than drawing a copy over it, so digits cannot ghost through.
- R8 inlined many synthetic accessors into their callers, in SystemUI and
  in Wallpaper & style. When a lookup dies on a new build, check that first.

All of these are recorded for `CP3A.260905.009` under
`app/src/test/resources/hosts/`, and checked in CI ([architecture](architecture.md#host-compatibility-tests)).
