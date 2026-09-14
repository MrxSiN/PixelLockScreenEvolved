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

## Unlock flight to the status bar clock

`KeyguardUnlockAnimationController` is told about every unlock:
`onKeyguardDismissAmountChanged()` while a swipe drags the keyguard,
`onKeyguardGoingAwayChanged()` when it starts going away, and
`notifyStartSurfaceBehindRemoteAnimation(AnimatedSurface[], AnimatedSurface[],
AnimatedSurface[], long, boolean)` when the app behind appears. Its
`keyguardStateController` (`KeyguardStateControllerImpl`) holds `mShowing`,
`mKeyguardGoingAway`, `mFlingingToDismissKeyguard` and `mDismissAmount`.

The keyguard window's views fade to alpha 0 within about 130ms of an unlock
starting, and the smartspace card's flight to the home screen is drawn by the
launcher (`ILauncherUnlockAnimationController`), so nothing in the keyguard
window can be seen travelling. The module adds its own window
(`TYPE_SECURE_SYSTEM_OVERLAY`, 2015, not touchable) with a picture of the
time, hides the time, and moves the picture on `Choreographer` frames: by the
keyguard's fade while dragged, then over 400ms once going away or flung.

The status bar clock is `com.android.systemui.statusbar.policy.Clock`, a
`TextView`, tracked through `onAttachedToWindow`/`onDetachedFromWindow`; the
one outside the notification shade window is the status bar's. SystemUI fades
it in over about 300ms, some 400ms after the keyguard has gone. The picture
turns into a drawing of that clock's text with its own `Paint`, waits at full
opacity until the clock's alpha (with its parents') reaches 1, and is then
removed; fading the picture out meanwhile dims the two together.

The path is a cubic Bézier curve from the time to the status bar clock, held
below any `DisplayCutout` bounding rect between them (on a Pixel 8 Pro,
x 616-726 down to y 151).

Hooking the same method twice from this module replaces the first hook, so the
unlock controller's methods are hooked once, in `host/UnlockFrameLoop.kt`, and
every motion that follows an unlock (the clock flight, the status bar) adds
itself to it.

With the depth effect on, the flight's first picture of the time has the
subject's cut-out erased from it (`DepthLayer.eraseSubjectFrom`, the layer's
bitmap drawn through `transformMatrixToGlobal` of the layer and the inverse of
the time's, `DST_OUT`), and a whole picture fills in beneath it over the first
35% of the flight.

## Status bar through an unlock

`com.android.systemui.statusbar.phone.KeyguardStatusBarView` (hooked at
`onFinishInflate`) holds the lock screen's right side in
`mSystemIconsContainer`. The status bar window is a view hierarchy:
`status_bar_contents` > `status_bar_start_side_container` (`clock`,
`notification_icon_area` > `notificationIcons`, a `NotificationIconContainer`)
and `status_bar_end_side_container` > `status_bar_end_side_content` (`system_icons` > `statusIcons`, and the battery).
`cmd window dump-visible-window-views` writes it out.

`host/StatusIconsHandover.kt` holds a picture of the lock screen's icons in its
own window and eases it onto the status bar's `status_bar_end_side_content`, tinted toward the
status bar clock's text colour. The battery's empty part is translucent, so a
picture over its own icons draws a brighter battery. The overlays only hide the
lock screen's views once the overlay window has drawn
(`OverlayWindow.onFirstFrame`), and hold the status bar's views at
`transitionAlpha` 0 while SystemUI fades them in; `status_bar_end_side_content`
is what is held. (Holding `system_icons` alone appeared to leave the battery doubling under the
picture.) Windows draw independently, and the status bar can present a frame or more
after the overlay: swapping a picture for its view in one frame, or crossfading
them in about 60ms, left the status bar clock missing for up to 8 frames. The
flight therefore never hides or fades the status bar clock: the landed picture
stays solid over it while SystemUI fades it in, and goes once it is fully
shown, as the first version did. Hiding the clock with `transitionAlpha` during
the flight and showing it again on landing blinked even with the picture held
300ms longer, the clock invalidated every frame and a frame commit awaited:
logged view state was correct, a screenshot during a longer hold showed the
clock, and removing the window without the picture over it did not blink, yet
recordings with a chip showed the clock missing for 5 to 8 frames once the
picture went. Changes to the status bar's own views
made the same way for the icons (hiding them, then crossfading) dipped for the
same reason, so the icons picture also stays solid over SystemUI's icons and
goes once they are shown and the status bar has committed a frame of them
(`registerFrameCommitCallback`) and drawn two more. SystemUI can bring the
status bar back before the time has landed; the committed flight takes 280ms
on a stiff spring, and hurries in if the status bar clock is already shown. Chips can be added mid-frame, so the containers of the
notification icons and chips are held hidden too until the entrance starts. A
chip SystemUI adds after the entrance has started showed for about 4 frames
before a late hide took effect, then grew back in; such late arrivals are left
shown and only bounce (`ExpressiveSpring.kickAt` on the animation matrix).
The large clock face is scaled by SystemUI
(`applyCsToLargeClock: scale=0.9`), so the flight starts from the time's bounds
through `transformMatrixToGlobal`, not its width and height. The battery draws its level as a dark cut in a
light fill, so the picture is tinted by multiplying (`PorterDuff.Mode.MULTIPLY`);
`SRC_IN` filled the level in. `host/NotificationIconsEntrance.kt` hides the
status bar's notification icons, and the chips beside them in
`start_side_notif_and_chip_container` (the screen recording chip), with
`transitionAlpha` on every frame from the start of the unlock, since icons are
rebound as it goes on, and brings them in, once their row is fully shown, with
`setAnimationMatrix` and `transitionAlpha`: `NotificationIconContainer` sets
each icon's translation, scale and alpha itself and never touches those two.

## Lock screen spacing

With the large clock the rows are 40dp apart from ink to ink. Measured on a
Pixel 8 Pro: the status bar is 151px tall and its icons end 19dp above its
bottom; a date line's capitals start 16dp below its top; the smartspace card's
first line starts 38dp below its top. `LockScreenInsets` subtracts these.

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
font can reach for the time shown, so every step grows whatever the font. `Paint.setFontVariationSettings` (and `TextView`'s) returns early when given
the settings it last applied, even after `setTypeface` replaced the derived
typeface they were applied to, so the time view clears them before setting
them again. Otherwise every relayout after the first draws the font's default
instance (Inter Regular). It is loaded from the module's own APK through
`PackageManager.getResourcesForApplication(moduleApplicationInfo).assets`.

R8 rewrites string literals naming `kotlin.*` classes in the release build, so
methods taking a `Continuation` are found by name and parameter count instead.
