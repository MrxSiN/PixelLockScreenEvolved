# Segmentation benchmark

Device: Pixel 8 Pro (Tensor G3: 1× X3 2.91 GHz, 4× A715 2.37 GHz, 4× A510 1.70 GHz), Android 17
QPR1 `CP3A.261005.005`, on battery. Model `birefnet_lite.onnx` (91,495,835 B, fp16 weights cast to
fp32), ONNX Runtime 1.29.0 for Android, CPU execution provider. Input: the device's own lock screen
photo, scaled as SystemUI scales it before sending (`DepthCutout.forSegmentation`): 683×1024, then
stretched to 1024×1024 for the model.

Harness: `scripts/bench-segmentation.sh` runs `scripts/bench/SegmentationBench.java` with
`app_process`. It loads the module's own compiled `depth` classes (pre-R8 release output; run
interpreted/JIT, as the module's app process runs them before any profile is compiled), the
installed APK's ONNX Runtime libraries and model, and the photo. `scripts/bench-segmentation-ab.sh`
interleaves settings in rotated order, waiting before each run until the big cores are below 48 °C
(`scripts/wait-cool.sh`), with two inferences per process, because the service keeps one session for
every inference of a binding.

## Stages (baseline, one run, `docs/perf/segmentation-stages.txt`)

| Stage | ms |
|---|---|
| Model/session initialisation | 1,848–2,021 |
| Resize to 1024×1024 (`createScaledBitmap`) | 4.9 |
| Preprocess (`SubjectInput.of`: pixels to normalised CHW floats) | 34.2 |
| Inference (`session.run`) | 9,224.5 |
| Output copy (tensor to `FloatArray`) | 3.4 |
| Postprocess (`SubjectInput.alphaOf`: sigmoid and edge easing) | 12.0 |
| Mask (`ALPHA_8` bitmap and upscale to 683×1024) | 129.6 first run, 14.2 warm |
| End to end through `SubjectSegmenter.maskOf` | 8,322 |
| Peak RSS (VmHWM) | 2,300,636 kB |

Inference is about 98% of the time; everything around it is under 2% even before warm-up. Binder
transfer, cache read and the SystemUI cut-out are outside the module's app process and were not
timed here.

## Alternatives tried

| Variant | Result | Kept |
|---|---|---|
| `intraOpNumThreads` 9 / 5 / 4 / 1 against the default (0, chosen by ONNX Runtime) | 14.5 and 12.0 / 12.7 and 13.0 / 15.7 and 15.9 / 33.7 and 33.7 s; back to back without cooling, so only indicative; none was faster than the default runs above or below | no |
| XNNPACK execution provider, 4 threads | 14.2 s, peak 2.91 GB | no |
| NNAPI execution provider | session 8.6 s, then killed by the low-memory killer (exit 137) during inference | no |
| CPU arena off (`setCPUArenaAllocator(false)`) | peak 2.32 GB, but inference median 19.0 s (+21%) | no |
| Memory pattern off (`setMemoryPatternOptimization(false)`) | peak 2.26 GB, inference no slower | **yes** |

## Memory settings, interleaved (`docs/perf/segmentation-ab.csv`, raw)

Rows also include one exploratory run per setting from before the loop was made to retry; all are
listed. Medians, with min–max:

| Setting | n | Session init | Inference, 1st run | Inference, 2nd run | Peak RSS |
|---|---|---|---|---|---|
| default | 5 | 4,579 ms | 15,655 (11,651–22,755) ms | 16,484 (13,766–18,916) ms | 4,334 MB (4,331–4,336) |
| arena off | 5 | 3,906 ms | 19,019 (11,632–20,017) ms | 16,088 (13,099–17,281) ms | 2,322 MB (2,321–2,359) |
| pattern off | 6 | 3,753 ms | 14,204 (8,060–15,769) ms | 12,885 (10,350–15,986) ms | 2,264 MB (2,248–2,279) |

Inference times vary ±30% between runs of one setting (thermal state and other load on a phone in
use), so no speed difference between "default" and "pattern off" is claimed. The memory difference
is not noise: every default run peaked at 4.33 GB and every pattern-off run at 2.25–2.28 GB.

Why: with memory pattern planning, the second run of a session allocates the block it planned from
the first run on top of the arena that run grew. The second run happens on a careful look (the
photo and its mirror image in one `maskOf`, the picker's "find the subject again") and on retries.

## End to end, careful look (`docs/perf/segmentation-careful.txt`)

`SubjectSegmenter.maskOf(photo, careful = true)`, the module's own class, baseline classes vs
candidate classes, one run each:

| | Init | Mask | Peak RSS |
|---|---|---|---|
| baseline | 4,929 ms | 33,393 ms | 4,446,360 kB |
| candidate | 3,874 ms | 29,290 ms | 2,322,772 kB (−47.8%) |

The time difference is a single sample and is not claimed.

## Output quality

The candidate's careful mask is byte-identical to the baseline's (699,435 bytes, 0 differing). The
memory planner changes where intermediate tensors live, not what is computed.

## Not done, and why

- Rewriting preprocessing/postprocessing natively (C, XAX or SIMD): together they take about 46 ms
  of 8–15 s, so even an infinitely fast kernel would change the total by under 0.6%, which is inside
  the run-to-run noise. A JNI boundary would not pay for itself measurably.
- A quantised or smaller-input model: the project's notes record that smaller inputs break the
  mask; a quantised export would need a new quality study. Not attempted.
- Returning the 1024×1024 mask instead of scaling it to the photo: removes one resample (14 ms
  warm) but changes mask pixels and the stored mask size, so it is not behaviour-preserving.
