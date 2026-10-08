# Frame path audit

Every callback that runs per frame, per pre-draw, per draw or per animation update, with what it
allocated, reflected or crossed before (`201f32a`) and after this pass. DEX evidence: `dexdump -d`
of the release `classes.dex` read through the R8 mapping with `scripts/dex-audit.py`. Device
evidence: instrumentation builds (`scripts/build-probes.sh`, `scripts/instrumentation/Probe.java`)
counting allocations with `Debug.getThreadAllocCount` around each callback; see `PERFORMANCE.md`.

No frame path makes a JNI call of the module's own: the only native code is ONNX Runtime, in the
module's app process, never in SystemUI or the picker. No frame path logs or builds strings.

## Lock screen, every pre-draw and every animation frame while the face is shown

| Path | Before | After |
|---|---|---|
| `ComposedViewPlacement.toWindow` (reached by every row below) | `generateSequence` over parents and over each parent's class hierarchy (sequence, iterator and lambda objects, a class-name compare per superclass per frame); 2 `FloatArray`; per point a boxed `Long` argument and an `Object[]`; 4 `Matrix` | Parents walked by loop; holder-class answer cached per class; arrays and matrices reused; argument arrays built once with their boxed constant `Long`s, handed to `Method.invoke` uncopied (`core/Invoke.java`). Left: the 3 `Long`s reflection boxes its answers in |
| `ViewBounds.toWindow/fromWindow/inWindow/inAncestor` | a new `Matrix`/`RectF` per call, 2–4 per call | write into caller-owned objects; scratch matrices inside |
| `DepthLayer.place` (pre-draw) | `inAncestor` (above), a `Measured` data class and a `Pair` (`placementKey`) per frame | in place: one owned `RectF`; `Measured` compared field by field and copied only when taken |
| `DepthLayer` scene-frame check | `toWindow` per frame | into an owned `Matrix`, compared with `Matrix.equals` as before |
| `DepthLayer.Eraser.draw` / `drawSubject` (draw) | `toWindow`, 3 `Matrix` | owned matrices; left: 2 `WallpaperPlacement.Size` and the `Placed` result per draw (only on frames that actually redraw) |
| `ClockFacePlacement.pinComposedTop` (pre-draw and holder layout) | `inWindow` `RectF` + `toWindow` | owned `RectF` |
| `ClockFacePlacement.pinComposedTop` doze fraction | `() -> Float` provider: a boxed `Float` per pre-draw | `fun interface DozeFraction` returning a primitive |
| `ClockLayout.awakeTop`, `LockScreenFloor.of`, `ClockLayoutReport.putFloor` (clock position, every pin) | — (new) | primitive compares of the band, face size, position, cut-out, crop and scroll; no allocation, reflection or logging on a frame whose inputs are unchanged (DEX audit). On a change only: 2 `WallpaperPlacement.Size` and a `Placed` per adaptive score, a spring animator per move, one secure-setting write off the main thread per reported change |
| `ComposedSmartspacePlacement.placeCard/offset` (pre-draw and every scene frame) | `ModuleClockFaces.largeIn` built a filtered `ArrayList` and an `IntArray(2)` per candidate; `offset` built a `FloatArray(9)` and a `FloatArray(2)`; boxed `Float?` bottom | one loop under the lock with a shared `IntArray`; reused arrays; no boxing. Left: the `WeakHashMap` entry iterator |

Measured on the device during lock screen wake transitions (large clock, depth effect on), allocations
per `BeforeEachDraw` callback: **44.2–48.6 before, 4.26–4.52 after** (8 and 9 batches of 400 calls).
The remainder is the three boxed `Long`s of each Compose coordinate read; an `asType`-adapted
`MethodHandle.invokeExact` would avoid them, but was measured 3.4× slower than `Method.invoke` on ART
in the sibling project (PixelLauncherEvolved ledger, entry 9), so it was not tried again.

## Clock faces, every draw and every animation frame

| Path | Before | After |
|---|---|---|
| `DigitRoll.draw` (every face draw) | `forEach` iterators over glyphs and places; a bound method reference per place; the ink lambda boxed each glyph's alpha (`Float.valueOf`, a new object each time) | index loops; `DozeInk.draw` called directly: no allocation (DEX audit: none) |
| `DigitRoll` roll animator | `animatedValue as Float` (a boxed `Float` per frame) | `animatedFraction`, the same value for `ofFloat(0f, 1f)` |
| `NumeralView.glyphs` (every frame the strokes thin or fill) | two `List<Float>` of boxed advances, a `Matrix` | `FloatArray`s, a reused `Matrix`. Left: one `Path` and `Glyph` per character, which the roll keeps referring to, and the variation settings string |
| `ExpressiveSpring.animator` updates (size morph, analog hands) | `(Float) -> Unit` boxed the seconds every frame | `fun interface Update` with a primitive parameter |
| `ClockSizeMorph` frames | `forEach` over the views around the time | array loop |
| `SharedAxisTransition` (picker pages) | `animatedValue as Float` per frame | the evaluator's own expression on `animatedFraction` |

## Not changed, with the reason

- `IosTimeView.onDraw`/`NumeralView.onDraw` measure the text each draw (`Paint.measureText`, a
  native call served from Minikin's layout cache, no allocation). Caching it needs invalidation on
  every paint and text change; not worth the risk without a measured cost.
- `DozeInk` opens one `saveLayerAlpha` per glyph while the outline fades in or shows. One layer for
  the line would change compositing where glyph outlines meet.
- `NumeralView.play` boxes the pulse's animated value once per frame next to a full glyph rebuild
  (`getTextPath` per character), which dominates.
- `TimeCoverage.of` allocates a small bitmap and buffer, but only when the time or the subject has
  held still after moving, not per frame.
