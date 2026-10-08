# Clock position

Where a large clock of this module's stands on the lock screen while awake.
Wallpaper & style → Lock screen → Clock → **Position** tab, the last one
(Style | Color | Size | Position):

- **Adaptive position** (the sheet's own switch row), its only row: on, the
  clock stands at the anchor that hides least of the wallpaper's subject, and
  the preview clock cannot be dragged.
- Off, the clock is placed by **dragging the real preview clock**, and
  "Drag the clock to reposition it", after a fingertip with an arrow above
  and below, stands beside the preview
  (`PickerDragHint`: in the column left of it where that is at least 96 dp
  wide, otherwise centred above it; never over the wallpaper, the clock or a
  control, and it takes no touches). The clock follows the finger, snaps to the
  top, the quarters and the bottom with the system's segment tick and a guide
  line, stretches a little past the ends, and settles on the spatial spring.
  TalkBack and switch access get Move clock up / down actions and hear where
  it landed. There is no separate position row or list: the preview is the
  editor.

Choices preview live and are stored on **Apply**, like the size; Back
discards them.

Verified on a Pixel 8 Pro, `CP3A.261005.005`.

## Native owners

Nothing is drawn by the module that SystemUI did not ask for, and no
SystemUI layout is replaced.

| Concern | Owner |
|---|---|
| Clock face lifecycle, large/small swap, fades, burn-in, AOD position | SystemUI's Compose lock screen scene, unchanged |
| Awake position of the large face | `ClockFacePlacement` translates the face's inner view, the same pin that already held it under the status bar; SystemUI's movable element and its graphics layers stay in charge of everything else |
| AOD | `AodClockMotion` releases the pin as the doze fraction rises, so the always-on display keeps SystemUI's fingerprint-aware position whatever is chosen |
| Small clock | never moved |
| Smartspace card | `ComposedSmartspacePlacement` already keeps it under the clock's drawn bottom, so it follows; the room it needs is kept free below the clock (`LockScreenFloor`) |
| Date row | already hidden beside the large faces, which write their own date |
| Depth effect | `DepthLayer` already reads the face's drawn place before each frame and erases from there, so it follows in the same frame; its readability rule (time in front when more than half would be hidden) still applies |
| Clock size change | `ClockSizeMorph` measures the large face on its first pre-draw, after the pin has run, so the morph lands on the chosen anchor |
| Persistence | the clock setting the picker already writes: axis `PIXEL_LOCK_SCREEN_EVOLVED_POSITION` beside the width and size axes |

## Position model

Stored in the clock setting's axes: `PIXEL_LOCK_SCREEN_EVOLVED_POSITION`
(`1` adaptive, `0` placed) and `PIXEL_LOCK_SCREEN_EVOLVED_POSITION_Y`, the share
of the band's free height the clock is placed at, from 0 (top) to 1 (bottom),
kept while adaptive so turning Adaptive off returns the clock to where it was
dragged. A setting without them reads as the top, so existing installs and
older builds see no change. Nothing is stored in pixels; the share is recomputed
from live geometry on every dependency change, so resolution switches
(1008/1344 px wide on this phone) keep the same composition.

The band:

- **top**: under the status bar (`LockScreenInsets.largeClockTop`), as before;
- **bottom** (`LockScreenFloor`): the under-display fingerprint sensor's top,
  read from the framework (`FingerprintManager.getSensorPropertiesInternal`,
  in natural pixels at the largest resolution, scaled to the one in use), less
  the lock screen's row gap, less the smartspace card and its gap when a card
  is shown. On this phone: 2,146 px sensor top, floor 2,073 px (reported as
  693‰). Without an under-display sensor, or with the screen turned, the floor
  is 78% of the window (marked `ponytail:` in the source).

Split is not movable (`ClockStyle.isMovable`): its time runs from under the
status bar down to the subject, and is laid out around it
(`SubjectAwareClockFace`); moving it would move what it measures against. The
position rows are hidden for it.

## Adaptive

`ClockLayout` scores the four anchors only when one of its inputs changes:
the band, the face's laid-out size, the position, the subject's cut-out, or
the wallpaper's crop or scroll (zoom is left out, as the depth effect leaves
it out, so the wake breath never moves the clock). Each candidate is the
face's laid-out box at that anchor, mapped onto the cut-out through the same
`WallpaperPlacement` the depth effect uses (anchors 0, ¼, ½, ¾, 1), and scored by the share of it the
subject covers (`SubjectInk`, a 128-px sampled grid with a summed-area table:
four reads per candidate). Each step down costs 0.04; the anchor shown is kept
unless another is better by 0.1 (`AdaptivePlacement`). With no subject, or one
that fills the screen, that leaves the clock at the top, where the depth
effect's readability rule then decides whether the time stands in front.

The cut-out is only ever the depth effect's own, already found
(`KeyguardDepthEffect.addSubjectListener`); nothing is segmented for the
clock. With the depth effect off there is no subject, and Adaptive stands at
the top.

### Dependency order

One way, so nothing iterates to a fixed point:

```
photo subject (depth effect) ─┐
wallpaper crop/scroll ────────┤
band (status bar, sensor, card height) ─┼─▶ anchor ─▶ face pin ─▶ DepthLayer erase, card placement
face laid-out size, position ─┘
```

The face's *laid-out* size and the card's laid-out height are used, not their
drawn ones, because the scene scales both while waking; the drawn values would
change the inputs every frame of a wake. Nothing downstream of the pin feeds
back: the depth layer and the card read the pin's result, and Split, which
would close the loop, never moves.

### Picker preview

The preview runs in Wallpaper & style's process, which can read neither the
photo's subject nor the fingerprint sensor. SystemUI's lock screen therefore
reports two values when they change, off the main thread
(`ClockLayoutReport`): the adaptive anchor (kept chosen whatever position is
set, so the preview shows it before Adaptive is applied) and the floor as a
share of the screen. The preview's face is translated from the top its
constraints give it, before every draw, to the same share of its own parent.

## Motion

- A change of position while the lock screen is up, awake and animating (an
  adaptive anchor moving because a new subject arrived, or a position applied)
  glides on `ExpressiveSpring.DEFAULT_SPATIAL` (the expressive scheme's default
  spatial spring, as `ClockSizeMorph` uses), from wherever the face stands.
- No glide while dozing, with animations off (`SystemMotion.isReduced`), with
  the lock screen hidden, or on the first placement of a face.
- The preview glides on the same spring as the slider steps.

## Contracts and failure

| Feature | Host | Required | Optional |
|---|---|---|---|
| Clock position | SystemUI | the clock plugin contracts (`ClockSettings.getAxes`, `ClockAxisStyle.get`, …) | `FingerprintManager.getSensorPropertiesInternal`, `FingerprintSensorPropertiesInternal.isAnyUdfpsType/getLocation`, `SensorLocationInternal.sensorLocationY/sensorRadius` |
| Clock position controls | Wallpaper & style | the size slider's sheet, slider and edit contracts | `ClockPickerViewModel.overridingClock`, `ClockMetadataModel.clockId` (which clock the Size tab is for), the sheet binding's `$colorUpdateViewModel` and `$lifecycleOwner` (switch colours) |

- Without the fingerprint contracts the floor falls back to 78% of the
  window; positions still work.
- A position that throws on a frame turns positions off for that face and logs
  once; the clock stands at the top and SystemUI carries on.
- Without the previewed-clock contracts the Size tab follows the stored clock.
- Safe Mode holds both features off like any other; the stored axis is left in
  place and ignored, so turning the module off leaves the clock setting valid.

## Also fixed on the way

- The Size tab decided "is this one of the module's clocks" from the width
  preset slider's rebinding, which only iOS has, so the size slider never
  showed for Stacked, Numerals, Analog or Split once another clock had been
  tapped; it now asks the picker which clock is previewed.
- Moving the size slider on a clock without width presets never enabled
  Apply. Such clocks are now given a preset that carries the chosen axes
  alone (`ClockPluginApi.axesOnlyPreset`), and none once the choice is moved
  back.
- The composed pin read the doze fraction through a `() -> Float`, boxing a
  `Float` every pre-draw; it is now a primitive `fun interface`.

## Position tab

The sheet's tabs are a fixed `ClockPickerViewModel.Tab` enum (Style, Color,
Size), each with one content view and one recorded height. The Position tab
rides the Size slot (`PickerPositionTab`):

- the list handed to `FloatingToolbarTabAdapter.submitList` is rewritten on
  its way in: a Position tab (an `Icon$Loaded` drawn in code, as the picker
  cannot load this module's resources) after Size, the same two objects (open,
  closed) for as long as the sheet lives;
- choosing it selects Size in the picker (`_selectedTab`), so the sheet
  animates as it does between its own tabs, and the Size slot shows the
  position rows instead of the size rows (`PickerSizeTab`), its height
  recorded as for the size rows;
- the adapter's diff keeps the picker's own click on the Size tab while
  Position is open, from whichever list bound it, so the picker's tab click
  (`ClockPickerViewModel$tabs$1$1`, an R8-merged lambda) closes Position when
  the lambda invoked is one any picker list carried and Size is still
  selected;
- the tab list's animator only knows selections, and a tab added or removed
  through it was left drawn over its neighbours, taking their taps (the Size
  tab that could not be tapped); when the number of tabs changes, its
  animations are ended once the new list is laid out;
- the tab is listed only while the previewed clock can be moved.

## Not done

- **Left/right alignment and custom X offsets.** Every style is centred by
  design; no style has a valid off-centre layout yet.
- **Readability (contrast) in the adaptive score.** No wallpaper readability
  metric exists in the module to reuse.

## Tests

- `ClockPositionTest`: stored values, share geometry, adaptive choice with and
  without a subject, full-screen subject, hysteresis, anchor names.
- `SubjectInkTest`: coverage of boxes inside, across and off the cut-out.
- `HostCompatibilityTest` / `HostContractsTest`: the new contracts against the
  recorded `CP3A.261005.005` descriptions, re-captured for this change.
- Device, lock screen (iOS face, depth effect on): Top, Middle and Lower move
  the real clock with its date and the subject's cut-out; the always-on
  display returns to SystemUI's position; Adaptive stays at Top for a portrait
  filling the lower three quarters (every lower anchor covers more of it); the
  small clock beside a notification is unchanged.
- Device, picker (2026-10-08, after the Position page was simplified): the
  tabs read Style | Color | Size | Position; 13 taps across every pair of tabs
  each selected the tab tapped and showed its content (Size from Position
  included); switching between movable, unmovable and Google clocks left no
  stray tab view; the hint shows only with Adaptive off and the Position tab
  open; a drag, Apply, and the lock screen matched the preview
  (`POSITION 0, POSITION_Y 0.614`).
- Device, picker (2026-10-07): the Position tab switches to and from
  Style and Size, greys Clock position while Adaptive is on; dragging the
  preview clock snaps to Bottom and places freely (66%, 54% down); Apply
  stored `POSITION 0, POSITION_Y 0.5446`, and the lock screen showed the clock
  at the same place as the preview.
