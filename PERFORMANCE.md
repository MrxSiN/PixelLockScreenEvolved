# Performance

Baseline: `201f32a` (v0.0.5 plus the host-contract work), release build. Candidate: the working tree
of this pass, release build. Both built with AGP 9.3.1, R8 full mode (`proguard-android-optimize.txt`),
and signed with the same key.

Environment: Pixel 8 Pro (`husky`), Android 17 QPR1 `CP3A.261005.005`, Vector v2.2, adb over Wi-Fi,
on battery, the owner's own lock screen photo, wallpaper and settings. The phone was in normal use
between runs.

## Artifact

| | Baseline | Candidate | Change |
|---|---|---|---|
| APK | 104,050,737 B | 104,050,709 B | −28 B |
| classes.dex | 380,900 B | 380,796 B | −104 B (−0.03%) |
| libonnxruntime.so + libonnxruntime4j_jni.so | 32,120,992 + 83,224 B | unchanged | 0 |
| Model asset | 91,495,835 B | unchanged | 0 |

(APKs from the same build tool at versionCode 62 and 63; `.artifacts/probe/clean-*.apk`, not
checked in.)

## Unit tests and static checks

`./gradlew testDebugUnitTest`: 103 tests, 0 failures on baseline and candidate.
`scripts/check-project.sh`: passes on both.

## Lock screen frame callbacks

Command:

```bash
scripts/build-probes.sh 201f32a 50 4
scripts/bench-probes.sh 20 .artifacts/probe/base-52.apk .artifacts/probe/cand-53.apk .artifacts/probe/base-54.apk .artifacts/probe/cand-55.apk .artifacts/probe/base-56.apk .artifacts/probe/cand-57.apk
```

Each APK is installed, SystemUI restarted, then the phone is locked and woken 20 times; lock screen
notifications were hidden for the run (`lock_screen_show_notifications` 0, restored to 1 afterwards)
so the large clock and the depth effect are shown. The probe records each `BeforeEachDraw` callback
and each `SceneFrames` frame in batches of 400. Raw: `docs/perf/frame-probe.txt`.

`BeforeEachDraw` callbacks (the depth layer's placement, the clock pin and the smartspace placement):

| | Batches | p50 per batch (ns, sorted) | Median of batch p50 | Median of batch p95 | Median of batch p99 | Median of batch means | Allocations per call |
|---|---|---|---|---|---|---|---|
| baseline | 8 | 26,001 31,169 31,494 32,267 32,714 40,690 65,836 71,859 | 32,491 | 123,230 | 234,294 | 47,698 | 44.18–48.61 (median 48.22) |
| candidate | 9 | 21,647 23,071 24,008 25,106 27,384 27,710 31,698 34,423 42,684 | 27,384 (−15.7%) | 86,548 (−29.8%) | 179,525 (−23.4%) | 39,274 (−17.7%) | 4.26–4.52 (median 4.36, −91%) |

Allocations: every candidate batch is below every baseline batch. Time: the ranges overlap, so the
time gain is consistent with the allocation drop but is not established at this sample size. The
batches were not order-rotated (each round installed baseline, then candidate, because each install
needs a higher version code).

`SceneFrames` produced one full baseline batch only (p50 63,070 ns, 41.5 allocations per call); the
trust agent dismisses the keyguard within about a second of waking, which leaves few scene frames per
wake. No candidate comparison is claimed for it.

Not measured: jank and frame misses (the keyguard is dismissed too quickly by the trust agent for a
stable `gfxinfo` window), GC counts, SystemUI start and hook installation time (no change was made to
them).

## Segmentation

See `SEGMENTATION_BENCHMARK.md` for the stage breakdown, every variant tried and the raw data. Summary:

| | Baseline | Candidate | Change |
|---|---|---|---|
| Peak RSS, two inferences in a session (n = 5 / 6) | 4,334 MB | 2,264 MB | −47.8% |
| Peak RSS, careful look end to end (n = 1) | 4,446,360 kB | 2,322,772 kB | −47.8% |
| Inference, first run (median, n = 5 / 6) | 15,655 ms | 14,204 ms | not claimed (noise ±30%) |
| Mask output | | byte-identical | |

## Visual parity

Clean release builds of both, recorded with `screenrecord` while waking to the lock screen with the
large iOS face and the depth effect on (`.artifacts/probe/clean-*-2.mp4`, middle frame): clock place
and size, date row and the subject cut out of the digits match; the differing pixels (6.4% of the
clock band) are the minute turning from 7:59 to 8:00, the temperature, and the unlock icon.
