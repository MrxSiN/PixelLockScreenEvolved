# Runtime architecture

## Decision

Not a rewrite. Measurements on the device decided where work went, and they pointed at the existing
code rather than at a new language or compiler:

- **Host-facing code** (SystemUI, Wallpaper & style, views, Compose-hosted views, the clock plugin
  objects, libxposed, framework resources, callbacks, animations, drawing): stays
  **Kotlin/Java → R8 (full mode) → DEX → ART**. The measured cost there was per-frame allocation
  and repeated reflective setup, which was algorithmic and fixed in place (`FRAME_PATH_AUDIT.md`).
  Almost all of this code makes reflective calls into SystemUI's own classes, so it belongs in ART:
  moved to native code, every one would be a JNI call back into ART.
- **Compute** (subject segmentation): stays **ONNX Runtime 1.29.0, CPU execution provider**, in the
  module's own app process, behind one coarse boundary per photo
  (`SubjectMaskService`: photo in by Binder, `ALPHA_8` mask out). Inference is about 98% of a
  segmentation; the only change kept is a session option that halves the peak memory of a second
  inference (`SEGMENTATION_BENCHMARK.md`).

## Partition

| Region | Representation | Process | Boundary crossings per operation |
|---|---|---|---|
| Hooks, clock faces, depth layer, picker pages, diagnostics, Safe Mode | Kotlin + one Java helper (`core/Invoke.java`), R8 → DEX | SystemUI, Wallpaper & style | none native |
| Segmentation pre/postprocessing | Kotlin, R8 → DEX | module app | none native |
| Segmentation inference | ONNX Runtime (prebuilt arm64 `libonnxruntime.so`) | module app | one `session.run` per inference |

## XAX

The owner asked for the parts that would gain from it to be rewritten with XAX
(`D:\GitHub\XAX`, research prototype). It was assessed against the measurements, and nothing
qualified:

- Its Android targets are direct DEX/APK emission and an arm64 JNI shared-object emitter. Its
  libxposed-on-Vector runtime path is recorded in its own state file as UNEXECUTED (ADR-178), and its
  AArch64 lowerer has scalar float arithmetic but no NEON vectorisation or `exp`.
- The host-facing code is reflection into R8-renamed SystemUI and Compose internals; its cost was
  allocations, which XAX-emitted DEX would not remove any better than the fixes made here, and
  porting it would put hooks that took many sessions to stabilise (the QPR1 Compose port) behind an
  unexecuted runtime path.
- The only self-contained compute it could take is pre/postprocessing: about 46 ms of an 8–15 s
  segmentation (under 0.6%, inside the run-to-run noise of inference). Inference itself is ONNX
  Runtime's, which XAX cannot replace.

So no XAX component ships. Reopen this if a profile shows a self-contained kernel of real weight on a
SystemUI frame path.

## Hot-path rules now in place

- Geometry answers (`ViewBounds`) are written into caller-owned `Matrix`/`RectF`; the shared
  scratch is main-thread only.
- Compose coordinates are read through cached reflective members, cached holder-class answers and
  prebuilt argument arrays handed to `Method.invoke` without a copy.
- State compared before each frame is kept in place and compared field by field.
- Frame-rate animator listeners read `animatedFraction` (or a primitive `fun interface`), never a
  boxed `animatedValue`.
- Glyph drawing calls `DozeInk` directly instead of through a boxing lambda.

## Tooling (reproducible, checked in)

| Script | Purpose |
|---|---|
| `scripts/bench-segmentation.sh`, `scripts/bench/SegmentationBench.java` | stage and end-to-end segmentation timings on the device |
| `scripts/bench-segmentation-ab.sh`, `scripts/wait-cool.sh` | interleaved, temperature-gated session-option comparison |
| `scripts/build-probes.sh`, `scripts/instrumentation/` | instrumentation builds of a baseline commit and the working tree |
| `scripts/bench-probes.sh` | lock/wake cycles collecting per-callback time and allocation summaries |
| `scripts/dex-audit.py` | allocation, boxing, reflection and locking sites per source method in the final DEX |
