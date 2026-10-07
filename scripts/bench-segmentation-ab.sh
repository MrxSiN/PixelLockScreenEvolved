#!/usr/bin/env bash
# Interleaved comparison of ONNX Runtime memory settings for the segmentation:
# each round runs every setting once, in a rotated order, from the same
# temperature, two inferences per process (the service reuses its session).
#   scripts/bench-segmentation-ab.sh <rounds> <setting>...   settings as in bench-segmentation.sh's memory argument
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ROUNDS=$1; shift
SETTINGS=("$@")
CSV=$ROOT/.artifacts/bench/segmentation-ab.csv
[ -f "$CSV" ] || echo "round,order,setting,session_ms,inference1_ms,inference2_ms,rss_hwm_kb" > "$CSV"
for ((r = 1; r <= ROUNDS; r++)); do
    for ((k = 0; k < ${#SETTINGS[@]}; k++)); do
        S=${SETTINGS[$(((k + r) % ${#SETTINGS[@]}))]}
        "$ROOT/scripts/wait-cool.sh" 48 > /dev/null
        for attempt in 1 2 3; do
            OUT=$("$ROOT/scripts/bench-segmentation.sh" "ab-$S" 2 0 all cpu "$S" 0 2>&1)
            grep -q rss_hwm_kb <<< "$OUT" && break
            echo "attempt $attempt failed: $(tail -3 <<< "$OUT")" >&2
        done
        SESSION=$(grep stage_session_ms <<< "$OUT" | cut -d' ' -f2)
        INF=$(grep stage_inference_ms <<< "$OUT" | cut -d' ' -f2 | paste -sd, -)
        HWM=$(grep rss_hwm_kb <<< "$OUT" | cut -d' ' -f2)
        echo "$r,$k,$S,$SESSION,$INF,$HWM" | tee -a "$CSV"
    done
done
