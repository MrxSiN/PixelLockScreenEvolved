# Double tap to turn off screen

Double tap an empty space on the lock screen to turn the screen off.

## Who owns what

Google's gesture already ships in SystemUI with its flag off; the module turns
the flag on and owns nothing else. Signatures are in
[HOOK_NOTES.md](../HOOK_NOTES.md#double-tap-to-turn-off-screen).

| Part | Owner |
|---|---|
| Touch observation, double-tap timing and slop | SystemUI: `LockscreenTouchHandling`, Compose `detectTapGestures` (platform `ViewConfiguration` values) |
| Empty-space test | SystemUI: Compose hit testing. The detector sits on a `Box` around the lock screen's elements, so a child that consumes a tap (notification, media, smartspace, shortcut, UDFPS, indication) keeps it |
| Accidental-touch rejection | SystemUI: `FalsingManager.isFalseDoubleTap()` before `onDoubleClick` |
| Sleep | SystemUI: `KeyguardTouchHandlingInteractor.onDoubleClick()` (`onDoubleTap(Float, Float)` from `CP4A.261105.040.A1`, found by its `goToSleep` call), then `PowerManager.goToSleep(uptimeMillis)`; system policy runs the normal screen-off and AOD transitions |
| On/off | Stock secure `double_tap_to_sleep` (system default 1). Google's Settings switch exists but is marked unsupported; the module's row in Wallpaper & style → Lock screen writes the same key |
| Flag | Module: `host/KeyguardDoubleTapToSleep.kt` sets `isDoubleTapHandlingEnabled` just before each `onDoubleClick` |
| Screen-off shape | Module: `host/SleepReveal.kt` answers each step of `LiftReveal`, `PowerButtonReveal` and `CircleReveal` (whichever SystemUI picked from how the screen last woke) on SystemUI's `LightRevealScrim` with a circle closing on the tap, as `CircleReveal` (whose constructor R8 removed) draws the AOD tap wake. Ported from Pixel Launcher Evolved's `SleepRevealScrim` |

The module adds no recognizer, overlay, region snapshot or sleep call. So
double registration can't happen. If Google turns the flag on, there is still
only one detector, and it reads the same key.

## Lifecycle

- **Install:** one `before` hook on `onDoubleClick`, with its fields and
  methods resolved once. Contracts: `HostContracts` under
  `DOUBLE_TAP_TO_SLEEP`. If any is missing, the feature alone stays off.
- **Each double tap that passes falsing:** first the module checks whether
  SystemUI drives the flag itself. If the flag already reads true before the
  module has ever written it, it can only be SystemUI's own; the module then
  leaves it alone and logs once. Otherwise the module records ownership in the
  process's system properties (these survive a hot reload but not a SystemUI
  restart) and writes `setting on && !touch exploration`.
- **Touch exploration:** the flag is false, so TalkBack's double tap keeps
  activating the focused item, and the stock `PulsingGestureListener` path
  runs as without the module. Switch Access is unaffected: it does not deliver
  raw double taps to the lock screen.
- **AOD:** untouched. The gesture lives on the awake lock screen's scene only.
- **Module off, Safe Mode, or a tripped feature:** the flag stays Google's
  constant false, which is stock behaviour. The setting is never written except
  from the switch.
- **Screen-off circle:** `LockscreenTouchHandling`'s tap callbacks (one
  synthetic lambda for tap, long press and double tap) receive the Compose
  `Offset`. The point is held only while a callback runs, and the double tap
  calls `onDoubleClick` from inside it, so the point standing then is the
  double tap's. An earlier version picked the case by `$r8$classId` and
  misread the switch (0 is the double tap), so it drew around the last single
  tap. After `onDoubleClick`, if the flag was
  true (so the screen is going off), that point is handed to `SleepReveal`.
  `SleepReveal` draws the lift's steps around it until the amount reaches 0,
  or for up to 2 s, then forgets it, so power-button and timeout screen-offs
  keep their own effect. These contracts are optional: without them, or if a draw
  fails, the screen-off is SystemUI's lift and the gesture still works.
- **SystemUI's own double-tap reveal:** from `CP4A.261105.040.A1` the double
  tap's screen off is SystemUI's `DoubleTapReveal`, a `CircleReveal` around
  the tap that also darkens the circle's centre (`LightRevealScrim.startColorAlpha`),
  opaque once the screen is dark. The lift, power button and AOD tap reveals
  never reset it, so the next ordinary wake left the scrim black behind the
  whole lock screen. After each of those three effects' steps, `SleepReveal`
  sets it back to 0; `DoubleTapReveal` writes it after its circle's step, so
  its own screen off is untouched.
- **Renamed tap callbacks:** the lambda's name is numbered by R8, so it has a
  DexKit locator: the class under `LockscreenTouchHandlingKt` with field `f$0`
  whose `invoke` calls `Float.intBitsToFloat` (it unpacks the tapped `Offset`).
- **Tap again to wake:** right after a double-tap sleep, the AOD tap handler
  (`PulsingGestureListener.onSingleTapUp`) is told by
  `BrightLineFalsingManager.isFalseTap` that the first tap is accidental.
  ShadeLog shows `isNotFalseTap: false`; the next tap about 0.5 s later
  passes. For 2 s after a double-tap sleep, `WakeAfterDoubleTapSleep` answers
  that one call "not accidental", only while `onSingleTapUp` runs. The
  proximity check (pocket) stays.
- **Haptics:** none. Google's path plays none for this gesture.

`config_supportDoubleTapSleep` is not used as the stock signal: a Pixel
overlay sets it true at run time on `CP3A.261005.005`, where the flag is still
off and Settings reports the feature unsupported.

## Cost

The circle runs only while a point stands, about one screen-off long. Each
frame boxes three to five floats into `Method.invoke`. Every other reveal
returns after a single field compare.

Ordinary touches run no module code: the only hook is `onDoubleClick`, which
SystemUI calls once per recognised double tap. Per call, the module does two
field reads, a `System.getProperty`, one `Settings.Secure` read, one
`AccessibilityManager` read and two reflective invokes, which box only the
cached `Boolean`s. Nothing runs per frame, so no frame-path measurement
applies; touch-dispatch CPU was not measured.

## Tested on CP3A.261005.005 (Pixel 8 Pro, commit 8292fb8 + this change)

| Case | Result |
|---|---|
| Stock state read off the build (SystemUI/Settings/launcher/framework dex, settings, config) | Gesture present, flag folded to false, setting `1` by default |
| Valid double tap on empty space (between the notification stack and UDFPS) | Pass: flag false, then owned and written true, then Google's "going to sleep due to double tap", then `PowerManagerService: Going to sleep due to application (uid 10229)`, then `Dozing...` (AOD) |
| Hot reload (bumped versionCode) | Hook re-installed under the same id |
| SystemUI restart | All six contracts `RESOLVED`; nothing held or faulted |
| Unit tests incl. `HostCompatibilityTest` against the recorded `CP3A.261005.005` | Pass |

Not reproduced by automation, so to be checked by hand: single tap, slow
double tap, drag, and taps on notifications, media, smartspace, shortcuts and
UDFPS; TalkBack on; multi-touch; battery saver. `cmd input` taps look
synthetic to `FalsingManager`, which rejects most of them ("Triggering False
Event"), and its belief rises with each attempt. Of about a dozen automated
attempts, one passed falsing, and it slept as expected. Every other attempt
failed falsing or landed during the wake transition, so those runs say nothing
about exclusions.
