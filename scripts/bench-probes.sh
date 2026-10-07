#!/usr/bin/env bash
# Installs each probe APK in turn (scripts/build-probes.sh), restarts SystemUI,
# then locks and wakes the phone <cycles> times so the lock screen draws its wake
# transition, and collects the PLSEProbe summaries and one screenshot per APK.
#   scripts/bench-probes.sh <cycles> <apk>...
# Output: .artifacts/probe/results.txt, .artifacts/probe/<apk>.png
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ADB=${ADB:-/d/Android/platform-tools/adb}
adbx() { MSYS_NO_PATHCONV=1 "$ADB" "$@"; }
CYCLES=$1; shift
OUT=$ROOT/.artifacts/probe
for APK in "$@"; do
    NAME=$(basename "$APK" .apk)
    adbx install -r "$(cygpath -w "$APK")" > /dev/null || { echo "install failed: $NAME"; continue; }
    adbx shell su -c "killall com.android.systemui"
    sleep 20
    adbx logcat -c
    for ((c = 1; c <= CYCLES; c++)); do
        adbx shell input keyevent 26
        sleep 2
        adbx shell input keyevent KEYCODE_WAKEUP
        sleep 2.5
        [ $c -eq 1 ] && adbx exec-out screencap -p > "$OUT/$NAME.png"
        sleep 1.5
    done
    adbx shell input keyevent KEYCODE_WAKEUP
    adbx logcat -d -s PLSEProbe:I | grep " n=" | sed "s/^.*PLSEProbe: /$NAME /" | tee -a "$OUT/results.txt"
done
