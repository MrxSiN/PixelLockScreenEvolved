#!/usr/bin/env bash
# Benchmarks the depth effect's segmentation on the connected device, stage by
# stage, with the app's own compiled classes and the installed APK's model and
# ONNX Runtime libraries. Needs a release build (./gradlew assembleRelease) for
# the compiled classes, the module installed on the device, and root for the
# lock screen photo.
#
#   scripts/bench-segmentation.sh <label> <runs> [threads] [opt] [cpu|xnnpack|nnapi] [memory 1|0|p|0p] [e2e 1|0|c]
#
# Output: .artifacts/bench/segmentation-<label>.txt and -<label>.mask
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
LABEL=$1 RUNS=$2 THREADS=${3:-0} OPT=${4:-all} EP=${5:-cpu} ARENA=${6:-1} E2E=${7:-0}
ADB=${ADB:-/d/Android/platform-tools/adb}
SDK=${ANDROID_HOME:-/c/Users/SiN/AppData/Local/Android/Sdk}
BT=$SDK/build-tools/36.0.0
ANDROID_JAR=$SDK/platforms/android-37.0/android.jar
export JAVA_HOME=${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}
JAVA_BIN=$JAVA_HOME/bin
CACHE=$HOME/.gradle/caches
ORT_JAR=$(find "$CACHE" -path '*onnxruntime-android-1.29.0*' -name classes.jar | head -1)
KOTLIN_JAR=$(find "$CACHE/modules-2" -name 'kotlin-stdlib-2.3.21.jar' | head -1)
APP_CLASSES=${APP_CLASSES:-$ROOT/app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes}
OUT=$ROOT/.artifacts/bench
WORK=$OUT/segmentation-build
DEVICE_DIR=/data/local/tmp/plse-bench
PKG=io.github.mrxsin.pixellockscreenevolved
adbx() { MSYS_NO_PATHCONV=1 "$ADB" "$@"; }

rm -rf "$WORK" && mkdir -p "$WORK/classes"
w() { cygpath -w "$1"; }
"$JAVA_BIN/javac" --release 17 -nowarn -d "$(w "$WORK/classes")" -cp "$(w "$ANDROID_JAR");$(w "$ORT_JAR");$(w "$APP_CLASSES");$(w "$KOTLIN_JAR")" "$(w "$ROOT/scripts/bench/SegmentationBench.java")"
(cd "$APP_CLASSES" && "$JAVA_BIN/jar" cf "$(w "$WORK/app.jar")" my/github/MrxSiN/pixellockscreenevolved/depth)
(cd "$WORK/classes" && "$JAVA_BIN/jar" cf "$(w "$WORK/bench-classes.jar")" .)
"$BT/d8.bat" --release --min-api 37 --lib "$ANDROID_JAR" --output "$WORK/bench.jar" \
    "$WORK/bench-classes.jar" "$WORK/app.jar" "$ORT_JAR" "$KOTLIN_JAR" 2>&1 | grep -v -i warning || true

APK=$(adbx shell pm path "$PKG" | tr -d '\r' | sed 's/^package://')
LIB=$(dirname "$APK")/lib/arm64
adbx shell "mkdir -p $DEVICE_DIR && chmod 700 $DEVICE_DIR"
adbx push -q "$(cygpath -w "$WORK/bench.jar")" "$DEVICE_DIR/bench.jar"
adbx shell "[ -f $DEVICE_DIR/photo ] || { su -c 'f=/data/system/users/0/wallpaper_lock_orig; [ -f \$f ] || f=/data/system/users/0/wallpaper_orig; cat \$f' > $DEVICE_DIR/photo; chmod 600 $DEVICE_DIR/photo; }"
mkdir -p "$OUT"
adbx shell "CLASSPATH=$DEVICE_DIR/bench.jar app_process -Djava.library.path=$LIB / SegmentationBench $APK $DEVICE_DIR/photo $RUNS $DEVICE_DIR/mask $THREADS $OPT $EP $ARENA $E2E" \
    | tr -d '\r' | tee "$OUT/segmentation-$LABEL.txt"
adbx shell "[ -f $DEVICE_DIR/mask ] && cat $DEVICE_DIR/mask && rm $DEVICE_DIR/mask" > "$OUT/segmentation-$LABEL.mask" || true
