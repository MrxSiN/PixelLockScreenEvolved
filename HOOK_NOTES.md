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

The move runs on `ExpressiveSpring.DEFAULT_SPATIAL` (damping 0.8, stiffness
380) and the fade on `DEFAULT_EFFECTS` (1, 1600). With Material's emphasized
decelerate over 500ms, a screen recording showed 69% of the resize in the
first 25ms and the rest crawling; the spring starts from rest and settles in
about 440ms. `ClockFaceAdapter.arrival` carries the fade to the depth layer.

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
keyguard's fade while dragged, then toward the status bar clock once going
away or flung, all on one critically damped spring whose stiffness eases from
120 to 500 over 120ms at the commit, aimed at 1.02 so it lands on crossing 1.

Measured on a Pixel 8 Pro (120Hz), the flight's own frames cost about 0.2ms.
Started from nothing it cost about 20ms (drawing the time 3ms, erasing the
depth subject from it 9.5ms, adding the window 7ms), its window first drew
some 46ms later, and SystemUI's unlock work stalls the main thread up to about
120ms besides. So `host/ClockFlightStandby.kt` gets it ready while the lock
screen is up. `host/LockScreenReadiness.kt` looks at the lock screen 250ms
after each burst of its frames and reports it settled when the display is
awake (doze fraction 0) and the time fully shown, or left when it dozes or the
clock detaches. On settling, the flight's window is added, empty
(`host/OverlayWindowStandby.kt`), and its pictures are drawn (about 12ms),
calling `Bitmap.prepareToDraw()`; later looks cost about 0.1ms and draw again
only what no longer matches. With it, starting the flight costs about 0.5ms
and its pictures reach the screen 24-34ms later. The lock screen's window is
told apart by `WindowManager.LayoutParams.type` 2040 (`TYPE_NOTIFICATION_SHADE`)
on the time's root, which leaves out the picker's preview clocks.

The status icons' picture is small and changes with every signal and battery
change, so only its window is kept ready, by another `OverlayWindowStandby`;
the picture is still drawn as the unlock begins.

A swipe to unlock lifts the clock face against the depth layer (the layer's
position in the time's pixels moved from -572 to -545px on one swipe), so the
depth layer's state for these pictures (`DepthLayer.occlusionState`) is its own
placement and the time's laid-out offset, leaving out SystemUI's moves of the
clock; otherwise every unlock found the pictures stale and drew them again.
The spring advances at most 17ms per frame, so the picture never leaps after
a stalled frame. A 250ms adb swipe first reports the unlock with the keyguard
already about 50% faded, and the time faded to about 5% by the picture's first
frame, so the picture starts at the time's opacity then and comes up to full
over the next 30% of the flight. The size shrinks geometrically: a linear
height went from 346 to 108px in 40ms near the end, which read as a collapse.

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
35% of the flight. Where the subject hides more than half of the time's ink,
the layer is hidden and the time stays in front, so nothing is erased. The
share (`TimeCoverage`) is the same erase drawn onto a 128 px alpha-only picture
of the time; it costs about 0.8 ms on a Pixel 8 Pro, so it runs once the time
and the layer have held still for a frame, not on every frame of waking up.

## Status bar through an unlock

`com.android.systemui.statusbar.phone.KeyguardStatusBarView` (hooked at
`onFinishInflate`) holds the lock screen's right side in
`mSystemIconsContainer`. The status bar window is a view hierarchy:
`status_bar_contents` > `status_bar_start_side_container` (`clock`,
`notification_icon_area` > `notificationIcons`, a `NotificationIconContainer`)
and `status_bar_end_side_container` > `status_bar_end_side_content` (`system_icons` > `statusIcons`, and the battery).
`cmd window dump-visible-window-views` writes it out.

The two rows are not spaced alike. With six status icons the lock screen's
`statusIcons` was 9px wider than the status bar's (264 against 255 on a Pixel
8 Pro), its icons laid out from the same left, so they sat 9px further from the
battery; with five the rows matched. A single picture eased onto the status
bar by its right edge doubled the status icons for its last frames and jumped
9px as it went. The picture is cut at `statusIcons`: that piece lands on the
status bar's `statusIcons` by the right edge of its visible children, and the
rest (the battery) on `status_bar_end_side_content` the same way.

The icons picture is shown from its window's first frame and the lock screen's
icons are hidden 3 frames after that first draw
(`OverlayWindow.onFirstFrameShown`). Showing the picture and hiding the icons
in the same callback, the frame after the first draw, left neither on screen
for 1 frame (at 81 and 120fps recordings). A frame commit callback registered
during that first draw never fired: the still picture draws no further frame.

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
(half the screen's size, `Bitmap.Config.HARDWARE`, about 5MB) and
`host/AodWallpaperLayer.kt` adds a view as the first child of `KeyguardRootView`,
over the black scrims beneath the keyguard and behind everything in it. Its
alpha is the clock's doze fraction. It draws with one AGSL `RuntimeShader`: the
photo, through a `BitmapShader` placed as `WallpaperPlacement` places the
wallpaper, multiplied by the brightness, made grey, or sampled at each dot
cell's middle (5dp) to light a disc whose radius is 0.36 of the cell times
`smoothstep(0.3, 0.95, lightness)`, times the depth cut-out's alpha there when
the depth effect has one (`KeyguardDepthEffect.addSubjectListener`, copied to
the GPU at the same half size). Every minute the photo drifts on a 3dp circle
and the dot grid moves 7px across and 4px down within its cell, both prime to
its 15px, so every offset comes round; between two minutes 6.4% of lit dot
pixels stayed lit. On a Pixel 8 Pro, with brightness 25%, dots lit 11.7% of
pixels (mean level 8 of 255), and 8.4% with the subject alone (6 of 255); a
3.5dp cell and `smoothstep(0.18, 0.85)` had lit 42.6% (27 of 255). Colour and
grey light every pixel.

`KeyguardRootView` is a `ConstraintLayout` that SystemUI's blueprint transitions
clone into a `ConstraintSet`: a child without an id crashed SystemUI ("All
children of ConstraintLayout must have ids to use ConstraintSet"), and a
match-parent child is sized in pixels instead. The photo is drawn once as
SystemUI starts, often before the style setting is read, and not again until it
changes, so its copy is always kept. While charging, turning the screen off
with the power key starts the clock screensaver, not the always-on display;
`KEYCODE_SLEEP` dozes.

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
