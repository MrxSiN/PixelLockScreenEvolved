# Hook notes

Everything below was read off a Pixel 8 Pro running Android 17
(`CP2A.260805.005`, SDK 37) by pulling
`/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk` and
`/system_ext/priv-app/WallpaperPickerGoogleRelease/WallpaperPickerGoogleRelease.apk`
and dumping them with `dexdump`. Re-verify these signatures before targeting a
newer build. Every clock plugin lookup lives in `host/ClockPluginApi.kt`; the
picker's sheet and slider lookups are in `host/PickerSliderApi.kt`.

Framework: Vector (JingMatrix/Vector) v2.2, modern libxposed API 102.

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

## Style tab: font slider

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
The module's presets are one per font (`PIXEL_LOCK_SCREEN_EVOLVED_FONT`), and
`current` is built from the stored font so the slider reopens on it.

The slider's label is `layout/floating_sheet_clock_style_content.xml`'s
`id/clock_face_width_label`, fixed to `string/clock_face_width` ("Clock face
width"). It is rebound in
`ClockFloatingSheetBinder$bind$13$1$12$1.emit(Object, Continuation)`, whose
argument is the slider view model and whose `$axisPresetSlider` field is the
`Slider`. The group sits in the R8 lambda held by the view model's
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

The picker preview's rows are shown by
`KeyguardPreviewSmartspaceViewBinder$bind$1$1$1$4.emit(Pair<ClockSizeSetting,
Boolean>, Continuation)`, holding `$largeDateView`, `$smallDateView` and
`$viewModel` (`KeyguardPreviewSmartspaceViewModel.clockViewModel
.keyguardClockViewModel`). It shows a row with a large clock whatever
`hasCustomWeatherDataDisplay` says; the module hides both rows after it runs
while its clock is previewed large.

## Font

Roboto Flex (`/system/fonts/RobotoFlex-Regular.ttf`, family `roboto-flex`) has
the registered axes `wght` 100-1000 and `wdth` 25-151 and the parametric axes
`XTRA` 323-603 (counter width), `XOPQ` 27-175 (vertical stroke), `YOPQ` 25-135
(horizontal stroke) and `YTFI` 560-788 (figure height). Larger sizes move from
`wght 600, wdth 100` to `wght 700, wdth 25`, the font's own designed cuts, so
strokes stay even; the time is set as large as the height and the screen width
allow, and is stretched vertically by at most 1.6 for the rest. Past that a
stretched diagonal (the stem of a 7) reads clearly thinner than a bar. `YOPQ`
is divided by the stretch so horizontal strokes keep their weight.

Android sets `opsz` from the text size when a variation leaves it out, so the
stroke would change with size. The module always sets it.

Inter 4.1 (`assets/fonts/InterVariable.ttf`, from the official rsms/inter
release, SIL OFL 1.1) is the second font: the closest open design to SF Pro,
which cannot be redistributed. It has `opsz` 14-32 and `wght` 100-900 and no
width axis, so its time reaches the screen width at a small size; larger sizes
are stretched vertically by up to 2 while the weight rises from 600 to 800.
`IosClockScale` spreads the six sizes between the smallest and the tallest the
font can reach for the time shown, so every step grows whatever the font. It is loaded from the module's own APK through
`PackageManager.getResourcesForApplication(moduleApplicationInfo).assets`.

R8 rewrites string literals naming `kotlin.*` classes in the release build, so
methods taking a `Continuation` are found by name and parameter count instead.
