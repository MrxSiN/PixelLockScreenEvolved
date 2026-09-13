# Hook notes

Everything below was read off a Pixel 8 Pro running Android 17
(`CP2A.260805.005`, SDK 37) by pulling
`/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk` and
`/system_ext/priv-app/WallpaperPickerGoogleRelease/WallpaperPickerGoogleRelease.apk`
and dumping them with `dexdump`. Re-verify these signatures before targeting a
newer build. Every clock plugin lookup lives in `host/ClockPluginApi.kt`; the
picker's slider binding is in `host/PresetSliderApi.kt` and `host/PickerSizeLabel.kt`.

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

## Size slider

The picker has no free-form axis slider for clocks. It draws a stepped slider
for any clock whose `ClockPickerConfig` carries an `AxisPresetConfig`:

```
ClockPickerConfig(String id, String name, String description, Drawable thumbnail,
                  boolean isReactiveToTone, List<ClockFontAxis> axes,
                  AxisPresetConfig presetConfig)
AxisPresetConfig(List<AxisPresetConfig$Group> groups, AxisPresetConfig$IndexedStyle current)
                 findStyle(ClockAxisStyle) IndexedStyle
AxisPresetConfig$Group(List<ClockAxisStyle> presets, Drawable icon)
ClockAxisStyle(Map<String, Float>), get(String) Float
ClockSettings.getAxes() ClockAxisStyle
```

`ClockPickerViewModel.axisPresetsSliderViewModel` builds a
`ClockAxisPresetSliderViewModel(valueTo = presets.size - 1, onSliderStopTrackingTouch)`
for the current group; tapping the selected clock again cycles groups. While the
slider moves, `ThemePickerCustomizationOptionsBinder` calls
`ClockAnimations.onFontAxesChanged(preset)` on both faces of the picker's own
preview clock. Apply writes the preset into the setting's `axes`
(`{"key":"PIXEL_LOCK_SCREEN_EVOLVED_SIZE","value":6},{"key":"wdth","value":120}`), and SystemUI builds the
clock again with those settings. `DefaultClockProvider.getClockPickerConfig`
sets `current` with `findStyle(settings axes)`, which is what places the slider
on the stored step when the picker reopens.

The slider's label is `layout/floating_sheet_clock_style_content.xml`'s
`id/clock_face_width_label`, fixed to `string/clock_face_width` ("Clock face
width"). It is rebound in
`ClockFloatingSheetBinder$bind$13$1$12$1.emit(Object, Continuation)`, whose
argument is the slider view model and whose `$axisPresetSlider` field is the
`Slider`. The group sits in the R8 lambda held by the view model's
`onSliderStopTrackingTouch`, found by field type. Only this binder class name
is R8-generated; if it moves, the slider still works under Google's label.

## Date line beside or below the small clock

`KeyguardClockViewModel.shouldDateWeatherBeBelowSmallClock` is true when the
stored clock setting's axes hold `wdth >= 110` (Google's wide Flex clocks);
otherwise it asks `isFontAndDisplaySizeBreaking` (screen width and font scale).
When false, `SmartspaceSection` puts `date_smartspace_view` beside the small
clock, which for a centred, full-width clock is off the right edge of the
screen. Every size preset therefore also stores `wdth = 120`. The
`hasCustomWeatherDataDisplay` face flag only hides that line beside the large
clock; SystemUI always shows it with the small clock, so the small face leaves
out its own date.

## Font

Roboto Flex (`/system/fonts/RobotoFlex-Regular.ttf`, family `roboto-flex`) has
the registered axes `wght` 100-1000 and `wdth` 25-151 and the parametric axes
`XTRA` 323-603 (counter width), `XOPQ` 27-175 (vertical stroke), `YOPQ` 25-135
(horizontal stroke) and `YTFI` 560-788 (figure height). The large sizes narrow
the counters and thin the strokes on those, then stretch the numerals
vertically; `YOPQ` is set below `XOPQ` so horizontal strokes match vertical
ones after the stretch.
