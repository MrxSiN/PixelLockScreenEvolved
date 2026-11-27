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

- **Rotation** (slider row under Adaptive position, and a two-finger twist
  of the preview clock): see [Rotation](#rotation) below.

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

## Adaptive

`ClockLayout` scores the four anchors only when one of its inputs changes:
the band, the face's laid-out size, the position, the subject's cut-out, or
the wallpaper's crop or scroll (zoom is left out, as the depth effect leaves
it out, so the wake breath never moves the clock). Each candidate is the
face's laid-out box at that anchor, mapped onto the cut-out through the same
`WallpaperPlacement` the depth effect uses (anchors 0, ¼, ½, ¾, 1), and scored by the share of it the
subject covers (`SubjectInk`, a 128-px sampled grid with a summed-area table:
four reads per half). The top half of the box weighs 0.65 and the bottom 0.35
(`AdaptivePlacement.weigh`): a head rising into the foot of the digits is what
the depth effect is for, the subject over their tops hides how they read. Up to
0.08 covered costs nothing. Each step down costs 0.04; the anchor shown is kept
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
back: the depth layer and the card read the pin's result.

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

## Rotation

The large clock can be turned about its middle, from 90 degrees left to 90
degrees right, stored as axis `PIXEL_LOCK_SCREEN_EVOLVED_ROTATION` (degrees,
clockwise positive) in the same clock setting, beside the position. A setting
without it stands upright, so nothing changes for existing installs; a value
out of range reads as the nearest end, anything not a number as upright
(`ClockPosition.of`). Placed or adaptive, the clock can be turned.

### Controls (Wallpaper & style)

- **Where:** every gesture is taken anywhere in the band of the screen the
  preview stands in, its empty sides included (`PickerGestureZone`, a plain
  transparent view over that band in the activity's content, present only
  while the Position tab is open), not just on the clock: the preview is drawn
  at about half size and a turned clock takes touches only inside its turned
  outline, so twisting on the clock itself was hard (owner's report). Both
  fingers land in the one view, so their pointers are never split.
- **Two-finger twist** (`PickerClockDrag`): the face
  turns with the fingers, measured on screen (the clock's own coordinates
  turn with it; measured there a twist came out at about half its angle).
  Within 4 degrees of a detent (0, 15, 30, 45, 90 either way) it settles on it
  with the system's segment tick; past 90 it stretches a quarter as far and
  springs back. While turning, the preview shows a line through the clock's
  middle at its angle over a faint level line, and a badge with the angle,
  drawn at screen size although the preview is drawn at about half. A second
  finger landing mid-drag ends the drag where it is and starts the turn.
- **Double tap** on the preview clock stands it upright, with a confirm tick.
- **Rotation slider** (`PickerPositionRows`), built from the Style tab's own
  slider row as the size slider is, with the angle written after its label and
  its 181 stop marks hidden (`BaseSlider.tickVisibilityMode`); it moves with a
  twist and a twist moves with it.
- **TalkBack / switch access**: "Turn clock left / right" step from detent to
  detent, and the clock's state reads "Upright" or "Turned 30 degrees right".
- The hint beside the preview reads "Drag the clock to move it, or twist it
  with two fingers to turn it" (placed) or "Twist the clock with two fingers
  to turn it" (adaptive), with a twisting glyph for the second.

The preview's views split a gesture's pointers between their children and
decide, as the first finger lands, which pointers each child is sent, so a
second finger never reached a clock that took its own touches (measured: one
pointer, for gestures injected through `InputManager` too). The gesture zone
takes both fingers itself, so nothing needs changing in the picker's views.

### Placement

| Concern | How |
|---|---|
| Turning | `View.setRotation` on the face's own view about its middle, applied by the pin before every draw (`ClockFacePlacement.turn`), so every reader of the face's drawn matrix follows it: the depth effect's eraser (`DepthLayer` maps the subject through the face's full matrix), the card under the clock (`ComposedSmartspacePlacement` reads its drawn bounds) |
| The band | the face is placed by the box it covers turned, `w·|sin θ| + h·|cos θ|` tall (`ClockRotation.extentHeight`); the share places that box's middle, which turning never moves, between the band's ends (`ClockPosition.topAt(…, extent)`), so a turned clock's corners stay under the status bar and above the floor. An upright face is placed exactly as before |
| The pin | reads the scene's scale as the length of the face's turned x axis (`hypot(MSCALE_X, MSKEW_Y)`) and aligns the top of the face upright: its drawn middle less half its height |
| Adaptive | anchors are scored by the turned box |
| Faces that fill the screen down | are given the upright height whose turned box fits their room (`ClockRotation.uprightRoom`), never less than 35% of it; nearly on its side, the width decides and the room is kept |
| Motion | a turn chosen glides on the same spatial spring as a move, from wherever the face stands; a turn and a move share one animation |
| Always-on display | the turn is handed back with the placement, as the doze fraction runs (`AodClockMotion.towardAodPosition`), so the always-on display shows the clock upright where SystemUI puts it; waking, the clock turns into its tilt as it rises into place |
| AOD fit | the always-on fit that shrinks a wide face now scales about the face's middle (rotation and scale share one pivot); the pin keeps the always-on top it had when that fit was taken about the face's top |
| Small clock | never turned |

### Verified on device (Pixel 8 Pro, `CP4A.261105.040.A1`, 2026-11-26)

- Picker: Rotation slider to 32°, the preview clock and its date turned and
  stayed in the band, Apply enabled; a double tap stood it upright, the slider
  followed to 0° and Apply turned off again.
- Picker: two-finger twists injected through `InputManager` (`app_process`
  helper, scratchpad only): 37° landed at 37°; −44° settled on −45°; a twist
  from −45° by 52° landed at 7°; the guide and badge read 15°, 22°, 30° as it
  turned and held on 15° passing it. Raw `sendevent` multi-touch did not work
  on this touchscreen (Android saw one pointer), so it is not a usable test
  route.
- Apply stored `PIXEL_LOCK_SCREEN_EVOLVED_ROTATION 30`. Lock screen (iOS face,
  notifications hidden): the large clock turned 30° with its date, starting
  below the status bar; always-on display upright; waking, the clock turned
  into its tilt as it rose. The small clock beside notifications stayed
  upright. The owner's setting was restored afterwards.
- Not verified on device: the depth effect's eraser over a turned clock with
  the subject crossing the digits (follows from the matrix path, untested),
  a filling face (Hyper) turned, and a real finger's twist (only injected).

## Adaptive size

Clock → Size → **Adaptive size** (a switch row above Clock size, built from the
sheet's own Large clock row, `PickerSwitchRow`), stored as axis
`PIXEL_LOCK_SCREEN_EVOLVED_SIZE_ADAPTIVE` (1 on). Every resizable style of the
module's takes part: the large face is grown down to the wallpaper's subject.

| Step | How |
|---|---|
| The edge | `SubjectInk.topEdge`: the first row of the depth effect's cut-out grid, from the band's top down, in which the subject fills at least 8% of the face's columns, mapped through the same `WallpaperPlacement` the depth effect uses (`ClockLayout.findEdge`, worked out only when the anchor's inputs change) |
| The height | `AdaptiveSize.height`: the edge lands 18% of the face's height above its bottom (`OVERLAP`), so the subject stands in front of the foot of the time. Solved exactly for the position's share, as the face's top rises with its height at any share but 0, so the answer does not depend on the face's present height and fitting never feeds back on itself; an adaptive position counts as the top. Bounded by the band |
| The size | `ClockFaceAdapter.refit`: the largest size of the style's own range whose measured height is no more than that, by halving the range nine times against the face measured unbounded at each size tried (`ResizableClockFace.setSizeAtOnce`: the iOS time glides to a new size on a spring while shown, and measured through that glide the search read the old size every time and landed far too large); then the face is put back where it stood and glides once to the size found. Only on a change of 6 px or more, after the draw, awake and at the scene's resting scale (the height is in window pixels over the scale the scene draws the clock at) |
| Figures around the subject (iOS) | a face that can (`ContouredClockFace`, the iOS face) runs each figure on down from the fitted size to the subject in its own columns: SystemUI measures the subject's top edge in 24 slices across the screen (`SubjectProfile`, a slice's edge where the subject fills a quarter of it), the adapter maps them into the time view as it is drawn (`ViewBounds`, so the scene's scale and the pin are taken in) and hands a `Contour`; each figure is stretched alone until its foot stands the overlap past the highest edge in its columns, never shorter than the size makes it, tops level, the colon as the size draws it, and the view as tall as the longest figure. A slice with no subject reaches the band's floor. Measured without the contour, so sizing and contour never feed each other; none while turned, before a fit, or with Adaptive size off; mapped only at the scene's resting scale |
| Picker redraw | the preview reads SystemUI's reports only as it draws, so a new report redraws it (`ClockLayoutReport.redrawOnChange`); before, a preview with nothing else moving kept the old edge (and the old adaptive anchor) |
| Falling back | no subject, the depth effect off, or Adaptive size off: the size step (the Clock size slider, dimmed while Adaptive size is on). The always-on display keeps the size the face had awake |
| Picker preview | the picker cannot read the subject; SystemUI reports the edge as a share of the screen (`pixel_lock_screen_evolved_clock_subject_edge`, permille, 0 for none) and the profile (`pixel_lock_screen_evolved_clock_subject_profile`, 24 comma-separated permille, 0 for a slice without it), `ClockLayoutReport`, and the preview fits and contours its own face to them. The edge is measured over the face SystemUI shows, so a style much narrower or wider than that previews approximately |

Verified on device (Pixel 8 Pro, `CP4A.261105.040.A1`, 2026-11-26): with
Adaptive size on and the large clock shown, SystemUI found the edge of the
owner's wallpaper at 174‰ (the hair rising at the right, inside the clock's
columns) and kept the smallest size, as the rule says for a subject that high.
Picker preview (same day, iOS, Adaptive size on): with the reported edge set
by hand to 24%, 30% and 38% of the screen, the clock grew to each, the foot of
the digits about 18% of the clock's height below the edge every time; at 45%
and above it stood at the style's largest size. The Size tab shows the switch
above a dimmed Clock size slider.

Figures around the subject (same day, v82): on the owner's wallpaper, whose
hair falls from about 16% of the screen's height at the right to 60% at the
left (reported profile `0,0,0,0,596,449,…,174,164,164`), the lock screen
showed the 9 run down to the hair at the left while the 1 and the 5 stayed
short under the rising hair at the right; in the preview, a hand-set diagonal
profile gave the same.

Also fixed: the Size and Position tabs, which share the Size slot's recorded
height, were found squashed (owner's report) with the bare Large clock row's
height in the picker's sheet heights. `PickerSizeTab.record` skipped writing a
height it had written before without asking whether the picker still held it;
the picker measures its tabs again as it binds the sheet anew. It now compares
with the picker's value on every layout, so any overwrite is repaired. The
exact sequence that overwrote it was not reproduced.

Not verified on device: a wallpaper with room above the subject on the lock
screen itself (the owner's wallpaper leaves none), the Size tab switch's Apply
path, and the other styles' fits (the search is the same for every resizable
face; only iOS animates its size, and that is handled).

## Not done

- **Left/right alignment and custom X offsets.** Every style is centred by
  design; no style has a valid off-centre layout yet.
- **Readability (contrast) in the adaptive score.** No wallpaper readability
  metric exists in the module to reuse.

## Tests

- `ClockPositionTest`: stored values, share geometry, adaptive choice with and
  without a subject, full-screen subject, hysteresis, anchor names.
- `AdaptiveSizeTest`: the subject's edge in a column band (crown, shoulders,
  a search started below the crown, a share too large, none), the height at
  the top and at a lower share, no edge and an edge above or below the band,
  and the highest contour edge under a figure.
- `ClockRotationTest`: stored turn read back and clamped, the turned box,
  upright placement unchanged, the turned box kept in the band and the share
  round trip, the room a filling face gives up, detents and how a turn is
  said and written.
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
