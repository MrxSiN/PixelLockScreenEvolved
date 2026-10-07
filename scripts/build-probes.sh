#!/usr/bin/env bash
# Builds instrumentation (Probe) release APKs of a baseline commit and of the
# working tree, one per version code so each can be installed over the last.
#   scripts/build-probes.sh <baseline-commit> <first-version-code> <count-per-variant>
# Output: .artifacts/probe/<variant>-<versionCode>.apk; variants alternate base, cand.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE=$1 CODE=$2 COUNT=$3
OUT=$ROOT/.artifacts/probe
mkdir -p "$OUT"
for v in base cand; do
    WT=$ROOT/.artifacts/wt-$v
    [ -d "$WT" ] || git -C "$ROOT" worktree add -q --detach "$WT" "$BASE"
    git -C "$WT" checkout -q --detach "$BASE" && git -C "$WT" reset -q --hard && git -C "$WT" clean -qfd
    if [ $v = cand ]; then
        cp -r "$ROOT/app/src" "$WT/app/"
    fi
    cp "$ROOT/local.properties" "$WT/"
    python "$ROOT/scripts/instrumentation/apply-probe.py" "$WT"
done
for ((i = 0; i < COUNT; i++)); do
    for v in base cand; do
        (cd "$ROOT/.artifacts/wt-$v" && ./gradlew -q --console=plain assembleRelease -PmoduleVersionCode=$CODE 2>&1 | grep -v '^w:' || true)
        cp "$ROOT/.artifacts/wt-$v/app/build/outputs/apk/release/PixelLockScreenEvolved-v0.0.5.apk" "$OUT/$v-$CODE.apk"
        echo "$OUT/$v-$CODE.apk"
        CODE=$((CODE + 1))
    done
done
