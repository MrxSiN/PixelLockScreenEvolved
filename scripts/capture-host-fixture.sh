#!/usr/bin/env sh
# Records the host build of the connected phone for the host compatibility
# tests (HostCompatibilityTest): which of the classes the contracts name it has,
# and what they extend and declare. Names only; no host code is copied.
#
# Needs a rooted phone with this module installed and enabled in both scopes,
# and adb on PATH (or ADB=/path/to/adb). SystemUI restarts, and Wallpaper &
# style opens, so run it while the phone is not in use.
#
#   scripts/capture-host-fixture.sh
#
# Writes app/src/test/resources/hosts/<Build.ID>/<package>.txt. Add a new
# build to VerifiedBuilds once its features are checked on the phone.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ADB=${ADB:-adb}
PROPERTY=debug.pixel_lock_screen_evolved.capture
FILE=pixel_lock_screen_evolved_host_descriptor.txt
SYSTEMUI=com.android.systemui
PICKER=com.google.android.apps.wallpaper

shell() { "$ADB" shell "$@" | tr -d '\r'; }

BUILD=$(shell getprop ro.build.id)
OUT="$ROOT/app/src/test/resources/hosts/$BUILD"
mkdir -p "$OUT"

shell setprop "$PROPERTY" 1
trap 'shell setprop "$PROPERTY" 0' EXIT

for PACKAGE in $SYSTEMUI $PICKER; do
    shell su -c "rm -f /data/user_de/0/$PACKAGE/$FILE"
done

# Each host describes itself as it starts.
shell su -c "killall $SYSTEMUI" || true
shell su -c "killall $PICKER" || true
shell am start -a android.intent.action.SET_WALLPAPER >/dev/null

for PACKAGE in $SYSTEMUI $PICKER; do
    TRIES=0
    until [ "$(shell su -c "test -s /data/user_de/0/$PACKAGE/$FILE && echo yes")" = yes ] || [ $TRIES -ge 30 ]; do
        TRIES=$((TRIES + 1))
        sleep 1
    done
    shell su -c "cat /data/user_de/0/$PACKAGE/$FILE" > "$OUT/$PACKAGE.txt"
    [ -s "$OUT/$PACKAGE.txt" ] || { echo "$PACKAGE wrote no description; is the module enabled for it?" >&2; exit 1; }
    echo "Recorded $PACKAGE on $BUILD: $(grep -c '^class ' "$OUT/$PACKAGE.txt") classes"
done
shell input keyevent KEYCODE_HOME
