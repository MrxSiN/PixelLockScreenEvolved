# Behaviour parity

Oracle: `201f32a`. Every change of this pass keeps the code paths' inputs and outputs and only
changes where intermediate objects live (`FRAME_PATH_AUDIT.md`), plus one ONNX Runtime session
option (`SEGMENTATION_BENCHMARK.md`). Evidence per feature below; "unchanged code" means no file of
that feature was edited.

| Feature (diagnostics name) | Touched by this pass | Evidence |
|---|---|---|
| Clock styles: iOS, Stacked, Numerals, Analog, Split (`CLOCK_STYLES`) | glyph drawing (`DigitRoll`, `NumeralView`), spring animator signature | Unit tests (103/103, incl. `DigitTransitionTest`, `ExpressiveSpringTest`, `IosTimeTextTest`). DEX: draw path allocation-free. Device: iOS face drawn on the lock screen, same place and size as baseline (`PERFORMANCE.md`, visual parity) |
| Minute digit roll | index loops; `animatedFraction` for `ofFloat(0f, 1f)` | `FloatEvaluator` computes `0 + f × (1 − 0)`, which is `f` exactly, so `progress` is bit-identical; same draw order and alphas |
| Clock size and width axes, stored keys | no | unchanged code; `check-project.sh` key checks pass |
| Small/large clock and the size morph (`CLOCK_SIZE_MOTION`) | morph loop over an array instead of a list | same views in the same order; same spring values |
| Large-clock pin on the Compose scene | `ViewBounds` writes into an owned `RectF` | same matrix composition; on device the face sits where baseline puts it |
| AOD behaviour, doze ink, burn-in movement | no (only shared glyph drawing above) | unchanged code |
| Date row and smartspace placement (`SMARTSPACE_PLACEMENT`) | no list, reused arrays in `placeCard`/`offset`; `largeIn` loop | same highest-face rule (first strict minimum in the same iteration order); same offset arithmetic. Device: date row under the large clock as in baseline |
| Depth effect (`DEPTH_EFFECT`) | `DepthLayer` in-place state, `ViewBounds`, `ComposedViewPlacement` | Same comparisons (`Matrix.equals`, `RectF.equals`, data-class equality of crop and scroll, `Bitmap` identity) as the old data class and `Pair`. A singular matrix still inverts to the identity. Device: subject erased from the digits, aligned with the photo, in both builds |
| Depth effect cached and uncached | segmentation session option only | stored mask format unchanged; a fresh careful segmentation gives a byte-identical mask |
| Subject / no-subject outcome | no | unchanged code (`giveUp` path) |
| AOD wallpaper (`AOD_WALLPAPER`) | no | unchanged code |
| Picker clock list, size slider, lock screen options, pages (`PICKER_*`) | page transition reads `animatedFraction` | value computed with the evaluator's own expression, bit-identical |
| Previews of another wallpaper | no | unchanged code |
| Host contract gating and diagnostics | no | on `CP3A.261005.005` both hosts report every required contract resolved, nothing held or faulted (secure `pixel_lock_screen_evolved_snapshot_*`); `HostContractsTest`, `HostCompatibilityTest` pass |
| Safe Mode, crash guard | no | unchanged code; `CrashGuardTest` passes |
| Update / restart | no | installs and SystemUI restarts of 12 builds during measurement; SystemUI came back with the module loaded after every one |
| Stored state | no | `MIGRATION.md` |

Not exercised on the device in this pass: Safe Mode activation, the picker pages' transitions, AOD
entry with this build, configuration/orientation changes, and the swipe (non-bypass) unlock. None of
their code changed apart from the picker transition's value read noted above.

Note: the phone is on `CP3A.261005.005`, which is not yet in `VerifiedBuilds` (the list ends at
`CP3A.260905.009`); adding it is separate from this pass.
