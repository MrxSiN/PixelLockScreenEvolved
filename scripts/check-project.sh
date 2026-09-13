#!/usr/bin/env sh
# Static checks run by CI before the build. Each guards an invariant a
# refactor could silently break without failing compilation.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC="$ROOT/app/src/main/kotlin/my/github/MrxSiN/pixellockscreenevolved"
META="$ROOT/app/src/main/resources/META-INF/xposed"

# Modern Xposed module metadata: the framework finds the module through these.
grep -q '^minApiVersion=' "$META/module.prop"
grep -q '^staticScope=true$' "$META/module.prop"
grep -q '^my.github.MrxSiN.pixellockscreenevolved.PixelLockScreenEvolvedModule$' "$META/java_init.list"
grep -q '^com.android.systemui$' "$META/scope.list"
grep -q '^com.google.android.apps.wallpaper$' "$META/scope.list"
[ "$(wc -l < "$META/scope.list")" -eq 2 ]

# The entry point only routes packages to patches.
grep -q 'XposedModule' "$SRC/PixelLockScreenEvolvedModule.kt"
grep -q 'isFirstPackage' "$SRC/PixelLockScreenEvolvedModule.kt"
grep -q 'HostPatch' "$SRC/PixelLockScreenEvolvedModule.kt"

# Clock designs know nothing of SystemUI; only host/ reaches into it.
! grep -rq 'com.android.systemui' "$SRC/clock"
! grep -rq 'libxposed' "$SRC/clock" "$SRC/host"

# Every SystemUI clock plugin class is named in one file.
[ "$(grep -rl 'com.android.systemui.plugins.keyguard.ui.clocks' "$SRC" | wc -l)" -eq 1 ]
grep -q 'registerListeners' "$SRC/host/ClockPluginApi.kt"
grep -q 'availableClocks' "$SRC/host/ClockPluginApi.kt"

# Stored keys: renaming either resets every device that stored it.
grep -q 'id: String = "PIXEL_LOCK_SCREEN_EVOLVED_IOS"' "$SRC/clock/ios/IosClockStyle.kt"
grep -q 'AXIS_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_SIZE"' "$SRC/host/ClockSizePresets.kt"

# R8 rewrites Kotlin class names written as strings to the module's own shrunk
# copy of Kotlin, so a host lookup must never name a kotlin.* class.
! grep -rq '"kotlin\.' "$SRC"

# Pure logic stays free of Android so it can be unit tested.
! grep -qE '^import android' "$SRC/clock/ios/IosTimeText.kt"
! grep -qE '^import android' "$SRC/host/ClockSizePresets.kt"
! grep -qE '^import android' "$SRC/clock/ios/IosClockScale.kt"

# The module has no screen of its own.
! grep -q '<activity' "$ROOT/app/src/main/AndroidManifest.xml"

# Release build: shrunk, signed from the environment, entry class kept by name.
grep -q 'isMinifyEnabled = true' "$ROOT/app/build.gradle.kts"
grep -q 'envKeystorePath' "$ROOT/app/build.gradle.kts"
grep -q 'PixelLockScreenEvolvedModule' "$ROOT/app/proguard-rules.pro"
grep -q 'adaptresourcefilecontents META-INF/xposed/java_init.list' "$ROOT/app/proguard-rules.pro"

# Release notes name the version being built.
VERSION="$(sed -n 's/^val appVersion = "\([^"]*\)"/\1/p' "$ROOT/app/build.gradle.kts")"
grep -q "v$VERSION" "$ROOT/RELEASE_NOTES.md"

echo "Static project checks passed."
