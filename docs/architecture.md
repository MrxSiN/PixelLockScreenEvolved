# Architecture

How Pixel Lock Screen Evolved reaches into SystemUI and Wallpaper & style, how
it keeps working when Google moves things, and where each part lives. The
Android 17 QPR1 lock screen has [a page of its own](android-17-qpr1.md), as
do [the depth effect](depth-effect.md) and [clock position](clock-position.md). `HOOK_NOTES.md` records every host
signature the module relies on and how it was verified on a device.

## Clocks from the host's own registry

System UI and Wallpaper & style each build their own `ClockRegistry`. Just
before each registry reads which clock is chosen, the module adds its styles to
it, presented as an ordinary `ClockProvider`:

```
ClockStyle ──▶ host/ adapters ──▶ ClockProvider ──▶ SystemUI's ClockRegistry ──▶ lock screen, preview, AOD
                                                └──▶ Wallpaper & style's ClockRegistry ──▶ Clock list, sliders
```

From there each process treats them as its own: SystemUI lays them out, dims
them for the always-on display, moves them for burn-in and previews them, and
Wallpaper & style lists them and stores the choice. The width and size are axis
values in the clock setting the picker already writes, so choosing one keeps
the other and nothing else is stored.

## Surviving host updates

SystemUI and Wallpaper & style are private, shrunk code that changes every
monthly build. The module never assumes a member is there.

| Part | Where | What it does |
|---|---|---|
| Contracts | `diagnostics/HostContracts.kt` | Every host class, method and field each feature looks up, per host, with the alternatives it falls back to and whether the feature can do without it. |
| Renamed symbols | `diagnostics/HostContracts.kt` (`locators`), `core/DexLocator.kt`, `diagnostics/HostSymbols.kt` | Names a build tends to change (numbered Kotlin lambdas, methods Google rewrites) carry a fingerprint: the nested class holding given fields (or exactly those fields), optionally with a method calling a given method, or the method calling a given framework method. R8 merges lambdas of one shape, so the call or the exact fields are what tell them apart. Every host class lookup goes through `hostClass`. When the name misses, DexKit finds it in the host APK once per host build and caches it; the contract check counts it as a fallback and every lookup (`hostClass`, `hostMethod`) uses it. Zero or several matches leave the feature off. |
| Contract check | `diagnostics/ContractAnalyzer.kt` | Looks each contract up before any patch installs, walking the class and its superclasses as the features do. It reads the host through `HostTypes`, so the same check runs on a phone and on a desk. |
| Feature gate | `diagnostics/FeatureGate.kt` | A feature missing a required member never installs its hooks. |
| Feature faults | `diagnostics/FeatureFaults.kt`, `hook/FeatureHooks.kt` | A feature whose hooks keep throwing is stopped alone; the rest carry on. |
| Snapshot | `diagnostics/HostStartupReport.kt` | Publishes what each start found to a secure setting, for the **Compatibility & diagnostics** page. |
| Safe Mode | `safemode/` | Three crashes of a host within a minute hold every feature of that host off on its next start. |

### Host compatibility tests

The unit tests check the module's own logic, but most breakage comes from the
host. So every contract is also checked against each Pixel build the module
was verified on, in CI:

- `app/src/test/resources/hosts/<Build.ID>/<package>.txt` describes one host
  on one build: each class a contract names, the classes above it, and the
  names of the fields and methods (with parameter types) each declares, or
  that the build does not have the class. Names only; no host code is kept.
- `HostCompatibilityTest` reads each description (`HostDescriptor`) and runs
  the same `HostContracts.forRuntime` and `ContractAnalyzer` the phone runs. A
  contract a refactor renames, retypes or points at the wrong class fails
  there, before it ships. A recorded build must also be listed in
  `VerifiedBuilds`.
- `HostContractsTest` holds each contract to the source that looks it up, so
  the list cannot drift from the code either.

The descriptions are written by the hosts themselves, through the class loader
the check reads (`HostDescriptorCapture`), so they are the host exactly as the
module sees it. To record a new build on a rooted phone with the module
installed:

```bash
scripts/capture-host-fixture.sh
```

It sets `debug.pixel_lock_screen_evolved.capture`, restarts SystemUI, opens
Wallpaper & style, and pulls what each wrote into the build's directory. When
the phone is in use, a hot reload does the same without restarting anything:
set the property, install a build with a new `versionCode`, and start the
picker's process (for example with a `content query` on its
`com.google.android.apps.wallpaper.recents` provider). A contract added later
that names a class a description lacks fails with a message naming the build to
capture again.

`CP2A.260805.005` was verified before descriptions were recorded, and is gone
from the test phone, so it has none.

## Package layout

```
PixelLockScreenEvolvedModule   Xposed entry: hands each process the patches it needs
clock/                         clock styles and faces; knows nothing of SystemUI
  parts/                       what every face shares: numerals, date line, digit roll, thumbnails
  styles/                      one file per design
host/                          adapters to SystemUI and Wallpaper & style, one patch per concern
hook/                          the hooking contract, over the libxposed API
diagnostics/                   host contracts, their check, per-feature gating and the compatibility snapshot
safemode/                      crash-loop detection and the Safe Mode switch
depth/                         the app side of the depth effect: subject segmentation
motion/                        springs and the system's own animation timing
core/                          logging, DexKit lookups of renamed host symbols
```

Every SystemUI clock plugin class is named in `host/ClockPluginApi.kt` alone,
and each other part of SystemUI in one `…Api` class, so a SystemUI update that
moves something is absorbed in one file.

### Adding a clock style

Implement `clock.ClockStyle` and a `ClockFace` in `clock/styles/` and list the
style in `clock.clockStyles()`. For a size slider, set `isResizable` and make
its faces `ResizableClockFace`; for the width slider, set `isWidthAdjustable`
and make its faces `AdjustableWidthClockFace`; to lay a face out around the
photo's subject, make it `SubjectAwareClockFace` and set `isMovable` false. Nothing in `host/` changes.

## Building

```bash
./gradlew clean testDebugUnitTest assembleRelease
```

Vector hot reloads only when the installed `versionCode` differs from the code
already loaded in a host process. For iterative device builds, pass a new,
monotonically increasing value each time:

```bash
./gradlew assembleRelease -PmoduleVersionCode=8
```

`META-INF/xposed/module.prop` opts in with `autoHotReload=true`; the module hands
over each live host and rebuilds its current clock without restarting it.

A local release build is unsigned unless `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_ALIAS`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD`
are set. `scripts/check-project.sh` runs the static invariants that CI enforces,
and `PUBLISHING.md` describes the signed releases GitHub Actions builds.

Release builds are shrunk with R8; the module entry class is kept by name,
because the framework resolves it from `META-INF/xposed/java_init.list` rather
than from any call site.
