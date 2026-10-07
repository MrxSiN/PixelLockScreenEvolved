#!/usr/bin/env bash
# Waits until the device's big-core temperature drops below $1 °C (default 45), so benchmark runs start from the same thermal state.
ADB=${ADB:-/d/Android/platform-tools/adb}
LIMIT=${1:-45}
while :; do
    T=$("$ADB" shell dumpsys thermalservice | sed -n '/Current temperatures from HAL/,/Current cooling/p' | grep -m1 'mName=BIG' | sed 's/.*mValue=\([0-9.]*\).*/\1/')
    awk "BEGIN{exit !($T < $LIMIT)}" && { echo "cool: $T"; break; }
    sleep 15
done
