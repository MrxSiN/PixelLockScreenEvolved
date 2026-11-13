# Pixel Lock Screen Evolved

Vector / libxposed API 102 module that patches SystemUI and Wallpaper & style
(clock styles, clock position, depth effect, AOD wallpaper, double tap to sleep).
`HOOK_NOTES.md` records every host member the module touches and why;
`docs/architecture.md` describes the layout.

## Principles

Apply on every change:

- **SOLID.** One reason to change per class (SRP); extend through new types,
  not edits to working ones (OCP); subtypes substitutable (LSP); small
  interfaces (ISP); depend on abstractions such as `Hooks`, `HostTypes`,
  `Logger`, never on a concrete host class (DIP).
- **DRY.** One representation per fact. Host names live in one place per
  lookup; contracts in `HostContracts`.
- **KISS, YAGNI.** No speculative code, no abstraction with one user.
- **Law of Demeter.** Talk to immediate collaborators; host internals stay
  inside the `*Api` class that reaches them.
- **Separation of concerns.** `diagnostics` (pure, desk-testable), `hook`
  (framework glue), `host` (host patches), `clock`, `depth`, `safemode`, `core`.
- **Boy Scout rule.** Leave touched code cleaner than found.

## No hardcoding

- Host symbols that R8 or Kotlin can renumber or rename (`$bind$N$1` lambdas,
  `ExternalSyntheticLambdaN`, private methods Google rewrites) must be
  identified dynamically: give them a `Locator` in `HostContracts.locators` and
  look them up through `hostClass` / `hostMethod` (`host/HostFields.kt`), which
  read `HostSymbols`. `DexLocator` resolves a miss with DexKit once per host
  build and caches it.
- Never guess: a locator matching zero or several candidates leaves the
  feature off.
- No device-specific values (paths, sizes, ids) in code; read them from the
  host or the framework at run time.

## Language for new code

The owner wants new or replaced code written in Brainfuck (compiled by BBC)
and XAX (with the XAX-MCP server),
using their latest versions, wherever the target supports it.

What the toolchains can produce decides where that applies:

- BBC compiles `.bf` to standalone Windows x86-64 or Android arm64
  executables that only read stdin and write stdout. Use it for standalone
  tools and compute that fits that shape (for example a script under
  `scripts/`).
- XAX builds whole DEX/APK, JNI `.so` and libxposed module artifacts. It
  cannot emit a class into this Gradle/Kotlin APK, so it fits a new
  self-contained artifact, not a patch inside the existing module.
- Code that runs inside SystemUI or Wallpaper & style (reflection into host
  classes, libxposed callbacks, DexKit, Android views) stays Kotlin until the
  module as a whole moves to an XAX build. Say so explicitly whenever a change
  falls back to Kotlin for that reason. An earlier XAX assessment (October
  2026, commit `8292fb8`) found no part of the module that would gain from it.

## Device and build

- adb: pass `-s <serial>` when more than
  one device is attached.
- Build with `JAVA_HOME` set to Android Studio's bundled JBR;
  release signing reads `ANDROID_KEYSTORE_*` from the Windows user
  environment. Bump `-PmoduleVersionCode` above the installed one so Vector
  hot reloads.
- `./gradlew :app:testDebugUnitTest` runs the desk checks, including every
  contract against each recorded build under `app/src/test/resources/hosts/`.
  Record a new build without restarting the phone: set
  `debug.pixel_lock_screen_evolved.capture` to 1, install a build with a
  bumped versionCode, start the picker
  (`content query --uri content://com.google.android.apps.wallpaper.recents/list`),
  pull `pixel_lock_screen_evolved_host_descriptor.txt` from each host's
  `/data/user_de/0/<package>/`, set the property back to 0, and add the build
  to `VerifiedBuilds`. `scripts/capture-host-fixture.sh` does the same with
  restarts.
- The compatibility report per host is in secure setting
  `pixel_lock_screen_evolved_snapshot_<systemui|picker>`; the module logs
  under tag `PixelLockScreenEvolved`.

<!-- ecosystem-policy v1 (2026-11-13): identical in every MrxSiN first-party repository. Change it everywhere in one coordinated set of PRs, never in one repository alone. -->
## Ecosystem rules (v1)

Shared by MrxSiN's first-party repositories. Any other rule in this file that is stricter or more specific to this repository wins.

- **Who owns what.** XAX owns the semantic graph, verifier, compiler, ABIs, backends, targets and artifacts. BBC owns the Brainfuck frontend, BFX and Brainfuck-specific optimization. XAX-MCP owns MCP transport, tool schemas, sandbox and permissions. Dependencies point one way: consumers -> XAX-MCP / BBC -> XAX.
- **New production logic** is XAX, or Brainfuck + XAX, wherever the toolchains reach the target; this repository's own language rules say where that is. Existing code in other languages is tracked migration debt, not permission to add more. Declarative config, assets, fixtures and licenses are not code to convert.
- **Upstream first.** When a toolchain lacks something this repository needs: reproduce it on the toolchain's latest `main`, search its issues and PRs, then fix a small bounded gap with a PR in the owning repository, or open (or link) an issue for a large one and mark only the affected work blocked-upstream. No consumer-side shims, private toolchain forks or local toolchain patches.
- **Pins.** Build against exact toolchain commits, recorded in one machine-readable place. Check the toolchains' latest commits before nontrivial work; move a pin by reviewed PR once checks pass, and write down why when an older pin stays.
- **No hardcoding** of secrets, personal or machine paths, toolchain locations, obfuscated host names or version-specific offsets. Machine-local paths belong in your own `~/.claude/CLAUDE.md` or `CLAUDE.local.md`, never in the repository. Specification constants (opcodes, ABI layouts, protocol markers, security limits) are fine when named and documented.
- **Design.** SOLID, DRY, KISS, YAGNI, Law of Demeter, Boy Scout Rule and Separation of Concerns, applied with judgment. They never override correctness, safety, verified state or evidence quality.
- **Evidence.** Report only what actually ran: EXECUTED, MEASURED, NOT RUN or BLOCKED, with the reason. Emulator, Wine or qemu runs are not device runs. Never rewrite old evidence; add new.
- **Git.** Work on a branch from the default branch and land it by pull request; the owner reviews and merges. No force-push to the default branch, no history rewrites. No AI attribution in commits, PRs or comments; commits use the repository's git identity. PR titles say what changed in plain words.
- **Security.** Report vulnerabilities privately, never in a public issue. Never print or commit tokens.
<!-- /ecosystem-policy v1 -->
