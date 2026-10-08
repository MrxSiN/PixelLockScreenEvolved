# Hook notes

Everything below was read off a Pixel 8 Pro running Android 17
(`CP2A.260805.005`, SDK 37) by pulling
`/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk` and
`/system_ext/priv-app/WallpaperPickerGoogleRelease/WallpaperPickerGoogleRelease.apk`
and dumping them with `dexdump`. Re-verify these signatures before targeting a
newer build. Every clock plugin lookup lives in `host/ClockPluginApi.kt`; the
picker's sheet and slider lookups are in `host/PickerSliderApi.kt`.

Framework: Vector (JingMatrix/Vector) v2.2, modern libxposed API 102.

## What Android 17 QPR1 changed

Read off the same phone on `CP3A.260905.009` (September 2026). The sections
below still describe `CP2A`; where the two disagree, this section wins.

The lock screen moved to a Compose element system, which is how a face is drawn:

- `ClockFaceLayout` now extends
  `com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider`,
  and SystemUI places a face through its `getElements()` rather than through
  `getViews()` and `applyConstraints()`, neither of which it calls any more.
  Returning an empty element list leaves the clock created, themed and ticking
  but never drawn, which is exactly what a missing clock looks like.
- A clock element is a `MovableLockscreenElement`, which also inherits
  `getContext()` and `getSource()` from `BaseLockscreenElement`; `getSource()`
  answers `ElementSource.DYNAMIC`, as Google's own faces do. SystemUI checks
  the interface before choosing `LockscreenScope.MovableElement`; using plain
  `LockscreenElement` bypasses the clock region's placement. Its key comes from
  `LockscreenElementKeys$Clock.getLarge()` or `.getSmall()`.
- Its composable, `LockscreenElement(LockscreenScope, Composer, int)`, need not
  be compiled as one. SystemUI carries
  `DefaultClockFaceLayout$Companion.clockView(View, Modifier, Composer)`, a
  public static composable that hosts a plain view, so the module builds the
  modifier Google builds — `SizeKt.wrapContentSize$default`, then the scope's
  `burnInAware` and `nonAuthUI` — and hands its own view to that. Everything is
  reached by reflection, and the module compiles against no Compose at all.
  `host/LockscreenElementApi.kt` holds those lookups.
- The large face's inner view is translated to the legacy top inset after
  Compose lays it out. The translation stays on the inner view, so the movable
  element remains under SystemUI's transition and burn-in modifiers.
- The scene draws the face's `AndroidViewHolder` inside graphics layers (the
  large clock sits at a scale of 0.9 on the lock screen, and moves further as
  it wakes), which no view matrix carries, and changing them invalidates no
  view. `View.transformMatrixToGlobal` is therefore wrong for anything in the
  holder; the true placement is
  `AndroidViewHolder.layoutNode` -> `LayoutNode.nodes` ->
  `NodeChain.innerCoordinator` -> `NodeCoordinator.localToWindow-MK-Hz9U(J)J`
  (a packed `Offset`). `host/ComposedViewPlacement.kt` reads it, and
  `ViewBounds.toWindow` uses it.
- The notification shade's `scrim_behind`, `scrim_in_front` and
  `light_reveal_scrim` still exist but sit under a hidden
  `NotificationShadeWindowView`. The scene draws its own `LightRevealScrim`,
  without an id, and dims the lock screen photo itself by
  `KEYGUARD_SCRIM_ALPHA` (0.2, measured against the photo on screen), with no
  `ScrimView` to read. That scrim is `LockscreenBehindScrim`: black at
  `ColorUtils.compositeAlpha(AodDimRepository.wallpaperDimAmount, 51)`, and
  `DozeServiceHost.setAodWallpaperDimmingScrim` kept that amount at 0 here.
- `ClockRegistry.registerListeners` is gone from SystemUI's shrunk copy, which
  now reaches its clocks through `querySettings()`; Wallpaper & style keeps
  `registerListeners`. `ClockPluginApi.registryReady` takes whichever exists,
  and the registry calls it again on every clock change, so the injector only
  adds the styles it is missing.
- `ClockProvider.createClock` takes a third `ClockAssets` argument, and
  `initialize` takes `(Executor, ClockMessageBuffers, ClockProviderEventListener)`.
  Both are absorbed by `InterfaceProxy`, which dispatches on method name.
- `KeyguardPreviewRenderer.updateClockAppearance` is a suspending method of the
  renderer, reached through the synthetic `access$updateClockAppearance` before.
  It is called on the renderer, and its first argument is the `ClockController`.
- `ClockSizeTransition` and the whole
  `keyguard/ui/view/layout/sections/transitions` package are gone, as is
  `KeyguardSmartspaceViewBinder`. `SmartspaceSection` lost `applyConstraints`
  and `dateView`, keeping `keyguardSmartspaceViewModel`, whose members R8 has
  inlined away entirely. The old custom clock-size morph has no target left;
  the Compose scene moves its `MovableLockscreenElement` itself. The live date
  row placement also has no legacy target, and the lock screen shows SystemUI's
  date row beside the clock's own until that half is ported. The preview half
  (`KeyguardPreviewSmartspaceViewBinder$bind$1$1$1$4`) is intact and is kept, so
  the lookups the live half needs are optional.

In Wallpaper & style:

- `CustomizationPickerFragment.initCustomizationOptionEntries` is a method of
  the fragment, not the synthetic accessor it was, so the fragment arrives as
  the call's target. The root and the screen are picked out of the arguments by
  type rather than by position.
- `ClockFloatingSheetBinder.bind` is folded into its one caller. What it set up
  now lives in `ClockFloatingSheetBinder$bind$13$1`, whose captured fields hold
  the sheet's `$view`, the `$optionsViewModel` and every slider; hooking its
  `invokeSuspend` stands in for the old `bind`, and runs more than once per
  sheet because it is a coroutine resuming.
- Material's `BaseSlider` keeps `defaultThumbDrawables`, a list with one thumb
  per value, where it kept a single `defaultThumbDrawable`.
- The picker's Kotlin is shrunk far enough that `kotlin.Pair` has no `getFirst`;
  the field behind it is still there.
- The page is `picker_motion_layout`, a `MotionLayout` whose swipe transition
  collapses `preview_header` as the option list scrolls. How far is set by
  `CustomizationPickerFragment.updateHeaderHeightConstraints(MotionLayout,
  WallpaperPickerEntry, int labelHeight, int optionListHeight,
  PackThemeSuggestedChip, int bottomInset)`: the collapsed header is the page's
  height less the option list's, floored at a dimension, so the whole list just
  fits. This module's rows made the header hit that floor (tiny thumbnails);
  `host/PickerPreviewCollapse.kt` passes the list's height without them, so it
  collapses as far as on a stock picker and the added rows scroll under it.

## Where clocks come from

Both SystemUI and Wallpaper & style build their own
`com.android.systemui.shared.clocks.ClockRegistry`:

- SystemUI: inside the Dagger `SwitchingProvider.get4`; the constructor is
  inlined there by R8, so it cannot be hooked.
- Wallpaper & style: `WallpaperPickerGoogleAppModule_Companion_ProvideClockRegistryFactory.get`.

Each calls `registerListeners()` straight after construction, which launches
`querySettings()` (reads `Settings.Secure.lock_screen_custom_clock_face`). The
module hooks `registerListeners` and adds entries just before it runs.

```
ClockRegistry
  ConcurrentHashMap availableClocks     // clockId -> ClockRegistry$ClockInfo
  Context context
  void registerListeners()
ClockRegistry$ClockInfo(ClockMetadata, ClockProvider, PluginLifecycleManager)
```

`PluginLifecycleManager` may be null: `verifyLoadedProviders` and the plugin
listener skip entries without one. `createClock(Context, String)` looks the id
up in `availableClocks` and calls `provider.createClock(context, settings)`.
The picker lists `getClocks(Z)` (metadata of every entry, minus deprecated ones)
and names each through `provider.getClockPickerConfig(settings)`.

## Plugin interfaces (`com.android.systemui.plugins.keyguard.ui.clocks`)

```
ClockProvider        createClock(Context, ClockSettings) ClockController
                     getClockPickerConfig(ClockSettings) ClockPickerConfig
                     getClocks() List<ClockMetadata>
                     initialize(ClockMessageBuffers)
ClockController      getSmallClock/getLargeClock() ClockFaceController
                     getEvents() ClockEvents, getConfig() ClockConfig
                     getEventListeners() ClockEventListeners
                     initialize(boolean isDarkTheme, float doze, float fold)
                     dump(PrintWriter)
ClockFaceController  getView() View, getLayout() ClockFaceLayout,
                     getConfig() ClockFaceConfig, getEvents() ClockFaceEvents,
                     getAnimations() ClockAnimations, get/setTheme(ThemeConfig)
ClockFaceLayout      getViews() List<View>, applyConstraints(ConstraintSet),
                     applyPreviewConstraints(ClockPreviewConfig, ConstraintSet),
                     applyExternalDisplayPresentationConstraints(ConstraintSet),
                     applyAodBurnIn(AodClockBurnInModel), getElements() List
ClockEvents          onTimeZoneChanged, onTimeFormatChanged, onLocaleChanged,
                     onAlarmDataChanged, onWeatherDataChanged, onZenDataChanged
ClockFaceEvents      onTimeTick, onThemeChanged, onFontSettingChanged,
                     onSecondaryDisplayChanged, onTargetRegionChanged
ClockAnimations      doze(F), enter, charge, fold(F), onFidgetTap(FF),
                     onFontAxesChanged, onPickerCarouselSwiping(F), onPositionAnimated
```

Data classes built by constructor:

```
ClockMetadata(String clockId, boolean isDeprecated, String replacementTarget)
ClockConfig(String id, String name, String description,
            boolean useAlternateSmartspaceAODTransition, boolean useCustomClockScene)
ClockFaceConfig(ClockTickRate, boolean hasCustomWeatherDataDisplay,
                boolean hasCustomPositionUpdatedAnimation, boolean useCustomClockScene)
ClockPickerConfig(String id, String name, String description, Drawable thumbnail)
ThemeConfig(boolean isDarkTheme, Integer seedColor)
            getDefaultColor(Context), getAodColor(Context)
ClockEventListeners()
```

`DefaultClockFaceLayout` has no constructor left after R8, so the module
implements `ClockFaceLayout` itself.

## Placement

`KeyguardClockViewBinder.addClockViews` adds every `getViews()` view to the
keyguard `ConstraintLayout`. `ClockSection.applyConstraints` places them by id
(`ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE` / `_SMALL`; the face view must carry
that id) and then calls the face layout's `applyConstraints`, so a face can
override what SystemUI set. The small clock's height is SystemUI's
`small_clock_height` dimension.

`hasCustomWeatherDataDisplay` is read per face by
`KeyguardClockViewModel.hasCustomWeatherDataDisplay`; when true SystemUI hides
its own date and weather line on the lock screen. The picker preview does not
consult it, so the preview still shows that line.

## Style tab: clock face width slider

The picker has no free-form axis slider for clocks. It draws a stepped slider
for any clock whose `ClockPickerConfig` carries an `AxisPresetConfig`:

```
ClockPickerConfig(String id, String name, String description, Drawable thumbnail,
                  boolean isReactiveToTone, List<ClockFontAxis> axes,
                  AxisPresetConfig presetConfig)
AxisPresetConfig(List<AxisPresetConfig$Group> groups, AxisPresetConfig$IndexedStyle current)
                 findStyle(ClockAxisStyle) IndexedStyle
AxisPresetConfig$IndexedStyle(int groupIndex, int presetIndex, ClockAxisStyle style)
AxisPresetConfig$Group(List<ClockAxisStyle> presets, Drawable icon)
ClockAxisStyle(Map<String, Float>), get(String) Float, put(String, float)
ClockSettings.getAxes() ClockAxisStyle
```

`ClockPickerViewModel.axisPresetsSliderViewModel` builds a
`ClockAxisPresetSliderViewModel(valueTo = presets.size - 1, onSliderStopTrackingTouch)`
for the current group. While the slider moves, the picker calls
`ClockAnimations.onFontAxesChanged(preset)` on both faces of its preview clock.
The module's presets are one per width step (`PIXEL_LOCK_SCREEN_EVOLVED_WIDTH`,
kept apart from `wdth`, which SystemUI reads to place its date line), and
`current` is built from the stored width so the slider reopens on it.

The slider's label is `layout/floating_sheet_clock_style_content.xml`'s
`id/clock_face_width_label`, fixed to `string/clock_face_width` ("Clock face
width"). The module keeps that label. The slider is rebound in
`ClockFloatingSheetBinder$bind$13$1$12$1.emit(Object, Continuation)`, whose
argument is the slider view model. The group sits in the R8 lambda held by the view model's
`onSliderStopTrackingTouch`, found by field type. Only this binder class name
is R8-generated; if it moves, the slider still works under Google's label.

## Size tab: size slider

`ClockFloatingSheetBinder.bind(view, ...)` (4 parameters) binds every tab of the
clock sheet once. The Size tab, `id/clock_floating_sheet_size_content`, is a
ConstraintLayout holding a title, a description and a switch, centred
vertically. The module inflates `floating_sheet_clock_style_content`, takes its
`clock_face_width_container` row (label and `Slider`), gives the views new ids
and adds the row below `clock_style_clock_size_description`.

Material is shrunk in the picker, so the slider is configured through
`BaseSlider` fields: `valueFrom`, `valueTo`, `stepSize`, `dirtyConfig = true`,
then `setValuesInternal(ArrayList)`. Listeners go into `changeListeners` as a
proxy of `BaseOnChangeListener.onValueChange(BaseSlider, float, boolean)`.
Colours are copied from the Style tab's slider before each draw:
`trackColorActive/Inactive`, `tickColorActive/Inactive`, the four track and
tick `Paint`s, and `defaultThumbDrawable.drawableState.fillColor`
(`MaterialShapeDrawable.setFillColor`, only when it differs, since setting it
redraws).

Tabs are shown by toggling visibility inside `floating_sheet_content_container`,
whose height is animated to the tab's value in the static
`ClockFloatingSheetBinder._clockFloatingSheetHeights`, a `StateFlowImpl` of
`ClockFloatingSheetHeightsViewModel(Integer clockStyleContentHeight,
Integer clockColorContentHeight, Integer clockSizeContentHeight,
Integer axisPresetSliderHeight)`, measured once from each content's wrapped
height (the size content has 60px top and bottom padding and wraps its height).
`host/PickerSizeTab.kt` swaps the rows and records the content's measured
height there with `updateState(null, new)` whenever it changes. The added row
is pinned to the content's top. For this module's clocks the slider sizes the
clock, so the title, description and `clock_style_clock_size_switch` are
`GONE` (the switch left checked) and only the row shows; for Google's clocks
the row is `GONE` and the tab is as the picker made it.

Moving the slider resizes every preview clock the picker has built. The picker
writes settings only through `ClockRegistry.applySettings(ClockSettings)`, with
the axes of the Style slider's preset. The module hooks it before it runs and
puts `PIXEL_LOCK_SCREEN_EVOLVED_FONT`, `PIXEL_LOCK_SCREEN_EVOLVED_SIZE` and
`wdth = 120` into the argument's axes, keeping the stored value for whatever
this write did not choose:

```
{"key":"PIXEL_LOCK_SCREEN_EVOLVED_FONT","value":1},{"key":"wdth","value":120},
{"key":"PIXEL_LOCK_SCREEN_EVOLVED_SIZE","value":4}
```

Apply is enabled while `ClockPickerViewModel.onApply` is non-null: the clock,
colour, size switch or axis preset differs from what is stored. The preset is
compared in `ClockPickerViewModel$isClockAxisStyleEdited$1.invokeSuspend`,
whose `L$0` is the chosen `AxisPresetConfig$IndexedStyle` (null until one is
chosen) and `L$1` the stored one, by `style` equality. The chosen preset lives
in the view model's `overridingClockPresetIndexedStyle` (`StateFlowImpl`,
`getValue`/`setValue`); the view model is
`ThemePickerCustomizationOptionsViewModel.clockPickerViewModel`, the second
argument of `ClockFloatingSheetBinder.bind`. `host/PickerSizeEdit.kt` sets that
flow to the preset on show with the size added to its axes whenever the size
slider moves, which makes the picker compare again, and for this module's
presets replaces the comparison's result with "font index or size differs from
the stored setting". The picker also passes the new preset to the preview
clocks' `onFontAxesChanged`, which already reads the size axis.

## Date line beside or below the small clock

`KeyguardClockViewModel.shouldDateWeatherBeBelowSmallClock` is true when the
stored clock setting's axes hold `wdth >= 110` (Google's wide Flex clocks);
otherwise it asks `isFontAndDisplaySizeBreaking` (screen width and font scale).
When false, `SmartspaceSection` puts `date_smartspace_view` beside the small
clock, which for a centred, full-width clock is off the right edge of the
screen. Every setting the module stores for its clocks therefore also holds
`wdth = 120`. The
`hasCustomWeatherDataDisplay` face flag only hides that line beside the large
clock; SystemUI always shows it with the small clock, so the small face leaves
out its own date.

`DefaultKeyguardBlueprint` applies `ClockSection` before `SmartspaceSection`, so
a clock face cannot move that row from its own `applyConstraints`. The module
hooks `SmartspaceSection.applyConstraints(ConstraintSet)` and, while one of its
clocks is showing small (`keyguardClockViewModel.currentClock` /
`isLargeClockVisible`), connects `dateView` below the small clock and centres
it. Measured on the device: SystemUI leaves 60px (about 23dp) between the row
and `nssl_placeholder`.

With `hasCustomWeatherDataDisplay` and the large clock showing,
`SmartspaceSection` clears the date row's top, hangs it above
`bc_smartspace_view` (the card: forecasts, events) and gives the card no top
at all, leaving it for the clock face; with nothing placing it, the card ends
up at the bottom of the screen. The module then clears the card's top and
bottom and connects its top to `LOCKSCREEN_CLOCK_VIEW_LARGE`'s bottom, with
`dimen/smartspace_padding_vertical`, the gap SystemUI leaves below a large
clock for its own row.

The picker preview's rows are shown by
`KeyguardPreviewSmartspaceViewBinder$bind$1$1$1$4.emit(Pair<ClockSizeSetting,
Boolean>, Continuation)`, holding `$largeDateView`, `$smallDateView` and
`$viewModel` (`KeyguardPreviewSmartspaceViewModel.clockViewModel
.keyguardClockViewModel`). It shows a row with a large clock whatever
`hasCustomWeatherDataDisplay` says; the module hides both rows after it runs
while its clock is previewed large.

## Changing clock size on the lock screen

When `isLargeClockVisible` changes, `KeyguardBlueprintViewModel` runs an
`IntraBlueprintTransition` of type `ClockSize`: a together-ordered set of
`ClockSizeTransition$ClockFaceOutTransition` (133ms, linear),
`$ClockFaceInTransition` (167ms after a 133ms delay) and
`$SmartspaceMoveTransition` (967ms to the large clock, 467ms to the small one,
emphasized), all `VisibilityBoundsTransition`s that animate alpha, visibility and
`setLeftTopRightBottom`. Each face transition's `addTargets()` adds the face
views and reads `viewModel` (`KeyguardClockViewModel`); `SmartspaceMoveTransition`
targets `date_smartspace_view` (when the date sits below a clock),
`bc_smartspace_view`, `aod_notification_icon_container` and one more id. The
transition logs every target to the `KeyguardBlueprintLog` buffer in
`dumpsys activity service com.android.systemui`.

A moment before it, `KeyguardSmartspaceViewBinder$bind$1$1$2$1.emit` (and
`$bind$1$1$4$4.emit`), both holding `$keyguardRootView`, set
`date_smartspace_view` `GONE` directly. A layout pass can run in between, so
the transition captured the row already gone and the card already jumped up.

`host/KeyguardClockSizeMotion.kt` hooks both emits and puts the row back while
this module's small clock is still shown, so the transition fades it; it also
hooks `ClockFaceTransition.addTargets` and excludes this module's face views
(`excludeTarget(view, true)`), and `host/ClockSizeMorph.kt` moves the new face's
time from the old one's place instead. Matching the current clock uses the
`ClockController` proxy each `ClockControllerAdapter` hands SystemUI.

The move runs on `ExpressiveSpring.DEFAULT_SPATIAL` (damping 0.8, stiffness
380) and the fade on `DEFAULT_EFFECTS` (1, 1600). With Material's emphasized
decelerate over 500ms, a screen recording showed 69% of the resize in the
first 25ms and the rest crawling; the spring starts from rest and settles in
about 440ms. `ClockFaceAdapter.arrival` carries the fade to the depth layer.

## Compatibility checks and Safe Mode

`diagnostics/HostContracts.kt` lists every class and member each feature looks
up, per host; `HostContractsTest` fails the build when a name there is no longer
in the sources. Each host checks the list with `Class.forName(name, false,
classLoader)` and a walk of declared members up the class hierarchy before any
patch installs (`ReflectedHostTypes`), and `FeatureGate` leaves out a feature
missing a required member. `HostCompatibilityTest` runs the same check against
the recorded builds under `app/src/test/resources/hosts/`, which the hosts write
when `debug.pixel_lock_screen_evolved.capture` is 1
(`scripts/capture-host-fixture.sh`). On `CP3A.260905.009` SystemUI selects `ClockRegistry.querySettings` in
place of `registerListeners`, and the Compose scene owns clock
size motion without a module hook.

Once the host's `Application` is created (`Instrumentation.callApplicationOnCreate`)
it writes a JSON snapshot to the secure setting
`pixel_lock_screen_evolved_snapshot_<host>`, which Wallpaper & style reads for
the compatibility page and its switches. A snapshot counts only if its boot time
matches the current boot within a minute.

Safe Mode records each uncaught crash in
`<deviceProtectedDataDir>/pixel_lock_screen_evolved_safe_mode` (readable before
a context exists and before first unlock). Three within 60 seconds hold every
feature of that host off on the next start. An answer is written to
`pixel_lock_screen_evolved_safe_mode_<host>` as `RESTORE:<time>` or
`KEEP_DISABLED:<time>`; the host observes it, carries it out and clears it, and
restoring kills the process so it starts again with the features installed.

## Double tap to turn off screen

Read off `CP3A.261005.005`; design and test record in
[docs/double-tap-to-sleep.md](docs/double-tap-to-sleep.md).

- `keyguard.ui.composable.LockscreenTouchHandlingKt.LockscreenTouchHandling`:
  a `Box` around the lock screen's elements whose `pointerInput` runs
  `detectTapGestures`; its `onDoubleTap` asks `FalsingManager.isFalseDoubleTap()`
  and calls `KeyguardTouchHandlingInteractor.onDoubleClick()`.
  `ConnectedDisplayConstraintLayoutKeyguardPresentation` calls it too.
- `KeyguardTouchHandlingInteractor.onDoubleClick()` reads field
  `isDoubleTapHandlingEnabled` (`ReadonlyStateFlow`; `$$delegate_0` is a
  `StateFlowImpl`, which keeps `setValue(Object)`): true logs "going to sleep
  due to double tap" and calls `PowerManager.goToSleep(uptimeMillis)`; false
  calls `PulsingGestureListener.onDoubleTapEvent()`. The constructor builds the
  flow as `flowOf(false).stateIn(...)`. `KeyguardTouchHandlingViewModel`
  hydrates it into `isDoubleTapHandlingEnabled$delegate`, whose getter nothing
  calls. The interactor keeps its `context` field.
- The tap callbacks are
  `LockscreenTouchHandlingKt$LockscreenTouchHandling$3$1$$ExternalSyntheticLambda0`
  (`invoke(Object)`, field `$r8$classId`, read off the packed-switch: 0 double
  tap, which ignores its `Offset` and calls `onDoubleClick`; 1 long press; the
  default (2) tap, which calls `onClick(x, y)`). `Offset.packedValue`
  packs x in the high 32 bits and y in the low 32, as float bits.
- Screen-off reveal: `LightRevealScrimRepository` picks the effect from the
  last wake: `PowerButtonReveal` after a power button wake (a circle from the
  button), `CircleReveal` after an AOD tap (around that old point), else
  `LiftReveal`. Each has `setRevealAmountOnScrim(float, LightRevealScrim)`;
  `LightRevealScrim.setRevealGradientBounds(4 floats)` and
  `setRevealGradientEndColorAlpha(float)`. `statusbar.CircleReveal` keeps its
  fields and `setRevealAmountOnScrim`, but has no constructor.
- AOD tap to wake: `shade.PulsingGestureListener.onSingleTapUp(float, float)`
  needs `isDozing`, `singleTapEnabled`, not docked, `!isProximityNear()` and
  `!isFalseTap(int)`, then sets `_lastDozeTapToWakePosition` and calls
  `wakeUpIfDozing(..., "PULSING_SINGLE_TAP")`. It logs each condition to the
  `ShadeLog` buffer (`dumpsys activity service
  com.android.systemui/.SystemUIService logbuffer ShadeLog`).
  `classifier.BrightLineFalsingManager.isFalseTap(int)` is behind
  `FalsingManagerProxy`.
- Settings: `com.google.android.settings.gestures
  .DoubleTapScreenToSleepPreferenceController` (key
  `gesture_double_tap_screen_to_sleep`) over secure `double_tap_to_sleep`
  (`pkg:android default:1`); `getAvailabilityStatus` returns 3
  (`UNSUPPORTED_ON_DEVICE`). `config_supportDoubleTapSleep` is false in
  `framework-res.apk` but true at run time (a Pixel overlay), so it says nothing
  about the flag. Nothing in SystemUI, Pixel Launcher, `services.jar` or
  `framework.jar` reads the setting.

## Lock screen spacing

With the large clock the rows are 40dp apart from ink to ink. Measured on a
Pixel 8 Pro: the status bar is 151px tall and its icons end 19dp above its
bottom; a date line's capitals start 16dp below its top; the smartspace card's
first line starts 38dp below its top. `LockScreenInsets` subtracts these.

## Lock screen previews of another wallpaper

Wallpaper & style asks SystemUI for lock screen previews through
`KeyguardRemotePreviewManager.preview(Bundle)`. The request's keys are read in
`KeyguardPreviewRepository.<init>`: `host_token`, `width`, `height`,
`highlight_quick_affordances`, `display_id`, `hide_clock`, `wallpaper_colors`
and `clock_id`. The picker draws the wallpaper itself behind the preview, so
`wallpaper_colors` is all SystemUI hears of it: null in the lock screen tab's
preview of the wallpaper already set, and the photo's `WallpaperColors` in the
preview shown while a new photo is looked at before it is set.

```
KeyguardPreviewRenderer
  Context context
  KeyguardPreviewViewModel previewViewModel
  static Object access$updateClockAppearance(KeyguardPreviewRenderer, ClockController, Resources, Continuation)
KeyguardPreviewViewModel
  KeyguardPreviewInteractor interactor
KeyguardPreviewInteractor
  KeyguardPreviewRepository repository
KeyguardPreviewRepository
  WallpaperColors wallpaperColors
```

`access$updateClockAppearance` runs for each preview's clock as it is set up,
holding the renderer and the clock's `ClockController` proxy.
`host/KeyguardPreviewWallpapers.kt` hooks it, and a preview whose colours are
set and differ from `WallpaperManager.getWallpaperColors(FLAG_LOCK)` (or
`FLAG_SYSTEM`) gets no depth layer: before, the current photo's subject showed
over the new photo's time.

## Depth effect model

The subject is found by BiRefNet_lite (fixed 1x3x1024x1024 input, ImageNet
mean and deviation, logits out) on ONNX Runtime 1.29 in the module's own app.
Measured on a Pixel 8 Pro with a 575 by 1024 photo, the phone warm from the
runs before:

| Model | Graph optimisation | Time | Peak RSS |
|---|---|---|---|
| BiRefNet_lite, own export (shipped) | all | 11-17 s | 2.25 GB |
| BiRefNet_lite, onnx-community fp16 | basic | 32-39 s | 5.9-6.5 GB |
| BiRefNet_lite, onnx-community fp32 | all | 25 s | 6.3 GB |
| RMBG-1.4 fp32 | all | 10-12 s | 0.94 GB |
| RMBG-1.4 int8 (dynamic) | all | 4-9 s | 0.96 GB |

onnx-community's export writes each deformable convolution (torchvision's
`deform_conv2d`, in the decoder's `ASPPDeformable`) as `GatherND` and
`ScatterND` sampling at full resolution; every run of it made the low memory
killer end background apps. ONNX has had `DeformConv` since opset 19, and ONNX
Runtime's CPU provider implements it (1.29 included), so
`scripts/export_subject_model.py` exports the op directly. On a desktop CPU the
two exports took 5.0 s and 2.3 GB against 13.0 s and 7.7 GB, and the own export
matched PyTorch exactly where onnx-community's differed by 0.015 on average.

The own export stores weights in half precision with a `Cast` to float before
each, which ONNX Runtime folds as the session loads: 91 MB, under GitHub's
file limit, differing from full precision by 0.00005. Computing in float16
instead is worse on ARM: the fp16 export fails with extended optimisation
(ONNX Runtime fuses GELUs into `com.microsoft.Gelu`, which has no float16 CPU
kernel), and at basic optimisation its mask differed by 5/255 and had a hole
at an earring. Dynamic int8 quantisation of `MatMul` made the file larger and
the mask worse (0.025). Running the model below 1024 does not work: it was
trained at 1024, and at 768 it took in the floor behind the subject and at 512
lost the hair and horns. RMBG-1.4 kept much of the photo's lit floor as subject
and left a haze beside thin shapes.

## Depth effect through the light reveal

Waking from the always-on display, `LightRevealScrim` (id `light_reveal_scrim`
in the notification shade window) reveals the photo from a point outward as
`revealAmount` goes from 0 to 1, in step with the clock's doze fraction going
from 1 to 0. Its `onDraw`:

```
if (revealGradientWidth <= 0 || revealGradientHeight <= 0 || revealAmount == 0) {
    if (revealAmount < 1) canvas.drawColor(revealGradientEndColor)
    return
}
if (startColorAlpha > 0) canvas.drawColor(getColorWithAlpha(startColorAlpha, revealGradientEndColor))
shaderGradientMatrix.setScale(revealGradientWidth, revealGradientHeight, 0, 0)
shaderGradientMatrix.postTranslate(revealGradientCenter.x, revealGradientCenter.y)
gradientPaint.shader.setLocalMatrix(shaderGradientMatrix)
canvas.drawRect(0, 0, width, height, gradientPaint)
```

`gradientPaint` is a two-stop `RadialGradient` ending in white, `SRC_OVER`,
under a `MULTIPLY` filter of `revealGradientEndColor` (black).

The depth layer used to fade by `1 - revealAmount`, so the time showed through
the subject until the reveal finished. `KeyguardScrims.darkenAsRevealed` now
repeats that `onDraw` over the subject in the scrim's coordinates with
`SRC_ATOP`. It only darkens the subject when the layer view is in its own
hardware layer, set only while the reveal runs (the layer is as big as the
photo). Drawn straight on the window, `SRC_ATOP` darkened the time behind the
subject too; inside a `Canvas.saveLayer` around the subject it darkened
nothing on screen, whether the scrim's `RenderNode` was drawn or its paint.

Since Android 17 QPR1 none of that is needed: the subject is no longer a copy
drawn over the time but its shape erased (`DST_OUT`) from the face, drawn as
one hardware layer while it erases (`host/DepthLayer.kt`). The photo itself
then shows through, so the reveal scrim, the lock screen's dimming and the
always-on wallpaper cover it as they cover the photo, and `KeyguardScrims` was
removed. The copy had a worse flaw on the composed lock screen: the scene fades
the clock per drawn element rather than as a group, so a half-faded copy let
the digits show through as the display woke or a screen saver ended.

## Depth effect notification

The module's app has no activity, so it cannot be granted `POST_NOTIFICATIONS`;
SystemUI posts the notification instead (`host/DepthProgressNotice.kt`), in a
channel of its own that appears under System UI's notification settings. Its
text is read from the module's resources through
`PackageManager.getResourcesForApplication`, and its small icon is
`Icon.createWithResource(modulePackage, R.drawable.ic_notification_depth)`.
The channel is `IMPORTANCE_DEFAULT` with no sound or vibration, so the icon
shows in the status bar without a heads-up; `IMPORTANCE_LOW` would count as
silent, which Pixel hides from the status bar by default.

## Wallpaper on the always-on display

SystemUI has its own dimmed wallpaper for the always-on display.
`WallpaperRepositoryImpl.wallpaperSupportsAmbientMode` is true when the secure
settings `doze_always_on` and `doze_always_on_wallpaper_enabled` are both 1 and
the device supports it: SystemUI's integer `config_dozeSupportsAodWallpaperOverride`
(0x7f0b0027: 0 no, 1 yes, -1 on a Pixel 8 Pro, meaning ask the framework), then
the framework's bool `config_dozeSupportsAodWallpaper` (0x01110163, false on a
Pixel 8 Pro). `DozeScreenBrightness`, `KeyguardViewMediator`,
`LightRevealScrimRepositoryImpl`, `LightRevealScrimInteractor`,
`NotificationShadeDepthController` and `WindowRootViewModel` follow that flow.
Neither `framework.jar` nor `services.jar` reads the framework bool, so only
SystemUI decides.

Setting `doze_always_on_wallpaper_enabled` to 1 alone left the always-on display
black. Hooking `Resources.getBoolean(int)` to answer true for
`config_dozeSupportsAodWallpaper` made SystemUI keep the wallpaper, blurred and
dimmed, but gave no say over its brightness, colour or burn-in, so the module
does not use it.

Instead `host/KeyguardAodWallpaper.kt` keeps a copy of the lock screen photo
(half the screen's size, `Bitmap.Config.HARDWARE`, about 5MB) and draws after
`LightRevealScrim.onDraw`. The reveal scrim sits above the real wallpaper and
below the composed lock-screen elements, so the clock, smartspace and unlock
affordances stay above the custom wallpaper. Adding a child beside the Compose
root no longer works on Android 17 QPR1: Compose draws its opaque AOD surface
over that child, while `AndroidViewsHandler` only draws registered interop
views. The drawing's alpha is the clock's doze fraction. It uses one AGSL
`RuntimeShader`: the photo, through a `BitmapShader` placed as
`WallpaperPlacement` places the wallpaper, multiplied by the brightness, made
grey, or sampled at each dot cell's middle (5dp) to light a disc whose radius
is 0.36 of the cell times
`smoothstep(0.3, 0.95, lightness)`, times the depth cut-out's alpha there when
the depth effect has one (`KeyguardDepthEffect.addSubjectListener`, copied to
the GPU at the same half size). Every minute the photo drifts on a 3dp circle
and the dot grid moves 7px across and 4px down within its cell, both prime to
its 15px, so every offset comes round; between two minutes 6.4% of lit dot
pixels stayed lit. On a Pixel 8 Pro, with brightness 25%, dots lit 11.7% of
pixels (mean level 8 of 255), and 8.4% with the subject alone (6 of 255); a
3.5dp cell and `smoothstep(0.18, 0.85)` had lit 42.6% (27 of 255). Colour and
grey light every pixel.

The photo is drawn once as SystemUI starts, often before the style setting is
read, and not again until it changes, so its copy is always kept. While
charging, turning the screen off with the power key starts the clock
screensaver, not the always-on display; `KEYCODE_SLEEP` dozes.

Over the always-on wallpaper the depth layer is not darkened by the light reveal
scrim, whose black it hides, and only fades with the doze. BiRefNet_lite leaves
alpha of a few percent across a wide area around the subject, which showed as a
box around the time wherever what lay beneath differed from the photo, so
`DepthCutout.cutOut` drops mask alpha up to 26 of 255 and stretches the rest.

The lock screen photo and its placement come from `host/LockWallpaperFeed.kt`,
which hooks `ImageWallpaper$CanvasEngine.drawFrameOnCanvas`, `onOffsetsChanged`
and `WallpaperService$Engine.onZoomChanged` once for the depth effect and the
always-on wallpaper. The options are rows of `host/PickerLockScreenOptions.kt`,
switches over `pixel_lock_screen_evolved_aod_wallpaper`, `..._aod_dots` and
`..._aod_black_and_white`, and a `SeekBar` over
`pixel_lock_screen_evolved_aod_wallpaper_brightness` (percent, 5 to 60, 25
until set), written once let go. A row that only matters while another option
is on is faded to 0.38 and disabled until it is. The picker process holds the
module's code until it is killed, so it has to be restarted with SystemUI after
installing a new build.

## Minute roll

`IosTimeView` lays the time out as a path per character. When `setText` changes
the text while the view is shown, `IosDigitTransition` lines the old and new
texts up from their right ends, and over 600ms (`PathInterpolator(0.2, 0, 0, 1)`)
each changed digit rises by 45% of the numerals' height and fades by 55% of the
way, while the new digit comes up from below, fading in from 15%. The face's
layout clips the time to its box, so the digits roll out of and into it. On the always-on display
the roll ran at about 30 frames a second. The depth layer holds its answer on
whether the subject may stand in front of the time while `ClockFace.isChanging`.

## Always-on display outline

`ClockAnimations.doze(fraction)` is passed on to faces implementing
`DozingClockFace`. The iOS time lays itself out as one `Path`
(`Paint.getTextPath` for the digits, stretched by a `Matrix`, plus the colon's
circles) and fades from filling it to an outline as the fraction rises. Roboto
Flex and Inter build glyphs from overlapping contours (the 4's bar crosses its
stem), so a plain stroke drew lines inside the digits. `Path.op(UNION)` with an
empty path returned the path unchanged. The outline is instead stroked at twice
its width in a layer and the filled path erased from it (`DST_OUT`), leaving
the outer half of the stroke.

## Font

The iOS clock is set in the font of SystemUI's default clock, the first in the
picker's list: `DefaultClockProvider` loads it as
`Typeface.create("google-sans-flex-clock", NORMAL)`, a `new-named-family` in
`/product/etc/fonts_customization.xml` (`GoogleSansFlexClock-Regular.ttf`, then
`GoogleSansClock-Regular.ttf`). `GSFAxes` declares `wght` 1-1000 (400),
`wdth` 25-151 (100), `slnt` -10-0, `ROND` 0-100, `GRAD` 0-100, `opsz` 6-144
and `ITAL`. `FlexClockController.BASE_PRESETS` are the seven width stops (with
`ROND` 0 and `slnt` 0), and `FontTextStyleImpl` sets the feature settings
`pnum`: without it the font draws its tabular figures, a different design (the
6 and 9 lose their curl). The iOS time is never stretched: a size is a text
size: the smallest draws numerals as tall as the stock large clock (393 px
drawn on the Pixel 8 Pro; the lock screen scene draws both clocks at about 0.88
of their laid-out size), and none draws the widest time of the chosen stop and
hour format past 96% of the screen's width. Measured drawn heights against
the stock 393 px: narrowest stop 99% to 113%, `wdth` 55 99% to 110%, `wdth`
80 (capped) 82% to 97%. With proportional figures the
widest 12-hour time fits at the stock height only from `wght` 600 / `wdth` 80
narrower; wider stops cap below it.

The other module clocks use Roboto Flex (`/system/fonts/RobotoFlex-Regular.ttf`, family `roboto-flex`), which has
the registered axes `wght` 100-1000 and `wdth` 25-151 and the parametric axes
`XTRA` 323-603 (counter width), `XOPQ` 27-175 (vertical stroke), `YOPQ` 25-135
(horizontal stroke) and `YTFI` 560-788 (figure height). Larger sizes move from
`wght 600` to `wght 700`, so strokes stay even, and the chosen width sets `wdth`
from 50 to 150; the time is set as large as the height and the screen width
allow, and is stretched vertically by at most 1.6 for the rest. Past that a
stretched diagonal (the stem of a 7) reads clearly thinner than a bar. `YOPQ`
is divided by the stretch so horizontal strokes keep their weight.

Android sets `opsz` from the text size when a variation leaves it out, so the
stroke would change with size. The module always sets it.

`Paint.setFontVariationSettings` (and `TextView`'s) returns early when given
the settings it last applied, even after `setTypeface` replaced the derived
typeface they were applied to, so the time view clears them before setting
them again. Otherwise every relayout after the first draws the font's default
instance.

R8 rewrites string literals naming `kotlin.*` classes in the release build, so
methods taking a `Continuation` are found by name and parameter count instead.

## Clock position (`CP3A.261005.005`)

No new hooks in SystemUI. The large face's existing pin
(`ClockFacePlacement.pinComposedTop`) takes its awake top from
`ClockLayout`; `docs/clock-position.md` describes the model.

- The fingerprint sensor is read from the framework, not SystemUI:
  `FingerprintManager.getSensorPropertiesInternal()` (hidden, readable by
  SystemUI) → `FingerprintSensorPropertiesInternal.isAnyUdfpsType()` /
  `getLocation()` → `SensorLocationInternal.sensorLocationY` /
  `sensorRadius`, in natural pixels at the largest display mode. The SDK 37
  stubs no longer carry `FingerprintManager`, so it is fetched by the service
  name `"fingerprint"`. On the Pixel 8 Pro `AuthController` dumps
  `udfpsBounds=Rect(557, 2146 - 787, 2376)` at 1344×2992, matching
  `y − radius`.
- In Wallpaper & style, `ClockFloatingSheetBinder$bind$13$1` also captures
  `$viewModel` (the `ClockPickerViewModel`), `$colorUpdateViewModel` and
  `$lifecycleOwner`; the last two colour the position switch through
  `SwitchColorBinder.bind`. Which clock the sheet previews is
  `ClockPickerViewModel.overridingClock` (a `StateFlowImpl` of
  `ClockMetadataModel`, null until a clock is tapped) → `clockId`.
- `ClockPickerViewModel$isClockAxisStyleEdited$1` compares the chosen
  `IndexedStyle`'s style with the stored one, null-safe; for a clock without
  `AxisPresetConfig`, the stored side is null, so choosing any preset there
  enables Apply, and the picker carries its axes to the preview
  (`onFontAxesChanged`) and to `applySettings`.
- The width preset slider's rebinding (`ClockFloatingSheetBinder$bind$13$1$12$1.emit`)
  is no longer hooked: it only fires for clocks with width presets.
- The Size tab's switch row (`layout/floating_sheet_clock_size_content`:
  `clock_style_clock_size_title`, `_description`, `_switch`, constrained to
  each other) is inflated again for the position switch, with new ids and its
  constraint fields re-pointed.
- Position tab: `ClockFloatingSheetBinder$bind$13$1` captures `$tabAdapter`
  (`FloatingToolbarTabAdapter`, `submitList(List)`) and `$viewModel`.
  `FloatingToolbarTabViewModel(Icon, String text, boolean isSelected,
  Function0 onClick)`; `Icon$Loaded(Drawable, Text$Loaded)`. The selected tab
  is `ClockPickerViewModel._selectedTab`; `ClockFloatingSheetBinder$bind$13$1$2$4`
  shows `$clockStyleContent`, `$clockColorContent` or `$clockSizeContent` by it
  and animates the sheet to the matching recorded height. Tab clicks are
  `ClockPickerViewModel$tabs$1$1.invoke`, an R8-merged lambda keyed by
  `$r8$classId` (2 Size, 3 Color, 4 Style here; 0 and 1 are unrelated). A
  drawable in the module's own resources cannot be loaded in the picker
  (package visibility: `Icon.loadDrawable` answers null), so the tab icon is
  drawn in code.
- The adapter's diff (`AsyncListDiffer$1$1`) matches tabs by `text` and
  compares `text`, `isSelected` and `icon.equals`, never `onClick`; a tab view
  keeps the click of the list that last bound it. The Size tab's view therefore
  still holds the picker's own click while Position is open, from whichever
  earlier list bound it; it selects Size, which is selected already, so
  nothing is emitted. The module remembers every click the picker's lists
  carried and closes Position when one runs with Size still selected. (Before,
  only the newest list's clicks counted: a tap on Size from Position did
  nothing.)
- The tab list's animator (`TabItemAnimator`, a `DefaultItemAnimator`) only
  animates a selection (payloads 3024/1114 from `getChangePayload`). Adding or
  removing the Position tab through it left the removed tab's view in the
  `RecyclerView`, drawn over its neighbours and taking their taps. R8 inlined
  `RecyclerView.getAdapter/getItemAnimator/setItemAnimator`; the fields
  `mAdapter` and `mItemAnimator` and `hasPendingAdapterUpdates()` remain, and
  `DefaultItemAnimator.endAnimations()`. Once a list with another count is laid
  out (`getItemCount` matches, no pending updates), `endAnimations` is called
  before its first draw, which drops the removed view at once.
- The drag hint is a plain `TextView` added to `android:id/content`, placed
  from `lock_preview_card`'s drawn bounds before each draw. The sheet is bound
  before it joins the window (`rootView` is then the sheet itself), so both it
  and the tab list are looked up once the sheet is attached.
- The Size slot's height (`ClockFloatingSheetHeightsViewModel.clockSizeContentHeight`)
  is read when the picker animates the sheet to the slot. Recorded at the next
  layout after the Position rows replaced the size rows, the sheet first grew
  to the old height and then snapped; it is recorded as the rows change.
- A Size tab view can keep the module's Size click after Position closes (the
  diff ignores clicks); that click selects Size itself then.

## Clock width on the composed lock screen (`CP3A.261005.005`)

The large clock element measures the face with `AT_MOST 1248` on a 1344 px
screen (48 px each side), inside `FrameLayout` → `ViewFactoryHolder` →
`AndroidViewsHandler` (none clip), and the scene draws it at about 0.88 of its
laid-out size at rest, so a face that only fills its box ends about 123 px from
each edge. The iOS face measures its column unbounded, in width and height (`IosClockFace.WideFace`)
and centres it back over the holder, which puts it at its start; it cannot rely
on overflowing its own bounds, because the depth effect draws the face as one
hardware layer, which ends there. The scale is the face's drawn height over its
laid-out height ([ClockFacePlacement]), divided by the window root's own drawn
scale (the picker's preview draws the whole lock screen scaled down), handed to
the face only once two frames in a row agree, so a wake's zoom never resizes the
time. The small face gets the scale too (it is never pinned). The scene
caps the small clock at `small_clock_height`:
`ClockRegionElementProvider$SmallClockRegionElement` gives the clock element
`heightIn(min = ClockEventController.smallClockBounds.height, max =
dimensionResource(R.dimen.small_clock_height))` inside a column with the
date/weather row and the cards, so a taller face overflowed upward. The
`dimensionResource(int, Composer, int)` read of that one id
(`com.android.systemui.customization.clocks.R$dimen.small_clock_height`)
answers the module small face's measured height instead (`KeyguardSmallClockBand`);
it runs during composition only. The scene then lowers the whole band by
what it grows, so room added under the small face is lifted back by the same
translation. AOD: the released pin can leave a tall face reaching the
sensor; it is lifted to `LockScreenFloor` while dozing, and scaled about its
top to the screen's text margins (the scene's always-on scale is larger than
its awake one). Clock size on the composed scene: the old
`ClockSizeTransition` hook is gone, so `KeyguardClockSizeMotion` is gated off.
The scene fades the outgoing clock element out (about 6 frames blank for
Google's clock, 9 for this module's) and only then the incoming one in; it
composes the incoming element while its view is detached. Running
`ClockSizeMorph` from there was tried: the carried time only slid into place
after the fade, which read worse than the scene's plain crossfade, so it was
dropped. The small band's height is read from the small face on screen
(`isInNotificationShade`), or measured unbounded while it is still detached,
so the room is right on the first frame.

