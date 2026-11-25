# Depth effect

The subject of a photo wallpaper in front of the large clock. This page covers
how the subject is found, how it is drawn, and why the service that finds it is
safe to export. `HOOK_NOTES.md` has the model's measurements and the SystemUI
signatures.

## From photo to layer

SystemUI draws a still lock screen photo itself. Each time it draws one it has
not drawn before, the photo is taken as drawn, scaled straight to the size it
is segmented at (its long side at most 1024 px, stretched as the wallpaper
surface stretches it), and sent to the module's own app, which finds the
subject's mask with BiRefNet_lite on ONNX Runtime. Nothing leaves the device.

### Edges

The model answers at 1024 px square. Its confidence (a sigmoid of its logits)
is stretched bilinearly over the photo and then run through a guided filter
(`depth/GuidedFilter`, grey guide from the photo's luma, radius the photo's
long side / 128, ε 2·10⁻³) before the edge is eased into alpha (`MaskEdge`).
Stretching the answer alone smeared the outline over as many pixels as the
photo is larger than the model's input; the filter pulls it back onto the
photo's own edges. It runs in the module's app, not SystemUI, and added about
a second to a 13 s segmentation on a Pixel 8 Pro (MEASURED once: 13 059 ms for
a 1024×872 photo).

### Cache

A mask already found is read back from disk, from
`/data/user_de/0/com.android.systemui/files/pixel_lock_screen_evolved_depth_<model>_v2/`.
Each file starts with a magic number and its size and ends with a CRC32 of its
alpha; it is written to a temporary file and renamed into place, so a reader
never sees half a mask, and leftovers of a write cut short are removed at
start. A mask whose size is not the one the photo is segmented at now is not
used. Reading a mask marks it as just used, and the six most recently used are
kept. The photo's key samples it at 64 px a side (32 before, at which two
edits of one photo could share a mask). `_v2` sends masks found before the
edge refinement back to be found once more.

### Memory

The subject is kept as an `ALPHA_8` matte the size of the wallpaper surface:
the clock's erasing, the adaptive position and the always-on display's dots
read only its alpha. It used to be an `ARGB_8888` cut-out, four times the
size, and the photo was also held at the surface's size until its subject was
found, then packed as a PNG for a later reprocess. Now only the photo at
segmentation size (about 3.5 MB for 1024×872) is kept. For a 1344×2992 surface
that is about 4 MB for the matte where 16 MB went to the cut-out alone
(computed from bitmap sizes, NOT MEASURED with `dumpsys meminfo`).

### Stale masks

A new photo clears the last one's subject at once: before, the old subject was
erased from the time over the new photo for the seconds its subject took to
find. A matte that comes back after a newer photo or look was asked for is
dropped, the photo a request is for is read across threads through a volatile
field, and a reprocess for a photo since replaced does nothing.

### Which subjects

**Subjects in front** keeps all or only the main subject: the mask is looked at
on a 64-cell grid, touching ink cells are one subject, the one with the most
alpha is kept with a cell's margin and the others cleared
(`host/SubjectComponents`). It is applied to the mask kept, so changing it
needs no new segmentation.

### Depth map

The service also runs Depth Anything V2 Small (int8, 27 MB, from
`onnx-community/depth-anything-v2-small`; the fp16 export needs an fp16
`BiasGelu` kernel ONNX Runtime's Android CPU backend does not have) on the same
photo, at 518 px on its long side in whole 14 px patches, and sends back how
near each pixel is, stretched between its 2nd and 98th percentiles. It is kept
beside the mask (`.depth`). With **In front of the clock: Depth map**
(`DepthLayers`), `host/DepthComposite` sets the clock at a plane in the photo:
the subject's median depth less 25 steps out of 255. Whatever is at least as
near stands in front even where the mask missed it, and what the mask took in
that is 40 steps or more behind the plane (sky between an arm and a body) goes
back behind the clock. The coarser result is snapped to the photo's edges with
the same guided filter. The depth model is loaded and run only while the depth
map is chosen (`KEY_DEPTH` on the request); a mask kept without a depth map is
looked at again once it is wanted. A careful look with both models took 20 s on the test
phone (MEASURED once, warm); a plain look at a 460 x 1024 preview photo
11.9 s.

### Cost

MEASURED on the Pixel 8 Pro, `CP4A.261105.040.A1`, 2026-11-25:

- **Where the service runs.** SystemUI bound it as an ordinary background
  service, so whenever the phone was not in use it ran in the `background`
  cpuset, cores 0 to 3 only, and a careful look took 64.7 s and 90.3 s. It is
  bound `BIND_IMPORTANT` now, at SystemUI's own foreground level while a
  request is open: `top-app`, cores 0 to 8, and the same look took 20.1 s and
  24.5 s with the screen off.
- **The guided filter** sums its boxes in separable running sums: five images
  of floats instead of nine and a summed-area table of doubles, 17 MB instead
  of 39 MB of working arrays at 1024 x 872, and 18.2 ms instead of 31.8 ms on
  the desk JVM, the output the same to 1.2e-7. In SystemUI, laying the subject
  out by depth reads its guide a row at a time, as Rec. 601 luma, rather than
  the photo's colours whole and an sRGB power function per pixel.
- **Startup.** Every patch of a host shares one `ClockPluginApi`
  (`ClockPluginApi.of`); SystemUI's hooks installed in 19 to 23 ms over four
  cold starts, against 21 to 30 ms before.
- **Each frame** of the lock screen, the tilt follower walks its clocks by
  index and Google's clock reads whether it dozes from its display's cached
  state, not from `PowerManager` over Binder; nothing of it is asked while the
  effect is off. Wallpaper & style encodes a previewed photo off its main thread.

### Editing the subject

**Edit subject** on the Depth effect page asks SystemUI (`DepthEditRequest`)
to open `host/DepthMaskEditor`, a window of SystemUI's own over the picker,
since the photo and its mask never leave SystemUI. It shows the photo with the
subject tinted and the rest dimmed; a brush (three sizes) adds to the subject
or takes from it. Edits are kept beside the mask (`.edit`, one mark per pixel:
untouched, added or removed), laid over whatever the mask and depth map make,
and forgotten with the mask when the subject is found again. It opens only
while the phone is unlocked: an overlay opened over the keyguard waited
hidden until the next unlock.

### Tilt motion

With **Tilt motion** on, `host/MotionParallax` reads the game rotation vector
(no magnetometer) and shifts a module clock's large face up to 12 dp sideways
against the photo, measured from how the phone has been held over the last
couple of seconds, so only movement shows. The sensor listens only while a
clock asks before a frame and stops 1.5 s after the last ask; it is off with
animations off. Checked on the device: the sensor is active while the lock
screen shows and stops once it is gone (`dumpsys sensorservice`); the shift
itself was NOT RUN by hand.

### Google's clocks and previews

The subject stands in front of Google's own large clocks too, and in
Wallpaper & style's preview of a photo not yet set; `HOOK_NOTES.md` has how
each is reached and why the preview's photo goes through SystemUI.

## In front of the time, or behind it

A face names what the subject may stand in front of (`ClockFace.depthView`):
the time, unless its design gives that place to something else, as Poster
gives it to its title.

The matte becomes a layer beside the large clock face, placed as
`WallpaperController` places the wallpaper (crop, scroll and zoom), erased from
the time alone and drawn as one layer with the face, so whatever fades, dims
or darkens the photo does the same to the subject.

Whether it stands in front is `host/TimeOcclusion`. The time is drawn
alpha-only at 128 px and read in parts: lines are runs of inked rows, digits
runs of inked columns within a line (gaps of up to 1.2% of the long side
bridged, so a dot matrix's dots join into digits), a part with under 30% of
the median digit's ink is a separator such as the colon, and a part wider
than 1.7 digits is split evenly. Stacked lines are the hour over the minute;
on one line, the hour is what comes before the colon.

| Bar | Balanced | Readability first |
|---|---|---|
| Most of the ink hidden | 40% | 25% |
| Least of any digit visible | 55% | 70% |
| Least of the hour, or the minute, visible | 60% | 75% |

Each bar is raised by up to 0.1 for thin strokes (ink under 30% of the digits'
boxes) and up to 0.1 for contrast under 3:1 between the clock's colour and the
mean luminance of the photo under the time. High-contrast text or a font scale
of 1.3 or more takes Readability first's bars. The bar is 0.05 lower for
keeping the subject in front than for bringing it there, so the answer holds
at the edge. **Depth first** always stands the subject in front. The previous
rule was one bar, 50% of the ink.

## The segmentation service

`depth/SubjectMaskService` runs in the module's app, where the model and its
runtime live. SystemUI binds to it with a `Messenger` and sends
`SubjectMaskContract.SEGMENT` messages carrying the photo; the service answers
`MASK` or `FAILED` with the request's number.

One segmentation takes about 12 seconds and 2.3 GB on a Pixel 8 Pro, twice that
when asked to look carefully. Runs never overlap: one ONNX session at a time.
Requests are coalesced by `NewestOnlyWorker`: the run under way always
finishes, and of the requests that arrive meanwhile only the newest waits; each
one it replaces is answered `FAILED` with the reason `superseded`. SystemUI's
client (`host/SubjectMaskClient`) only listens for its latest request number, so
it never acts on a stale answer. Changing the wallpaper several times in a row,
or asking for a reprocess repeatedly, so costs at most two runs, not one per
change. On the test phone three reprocess requests a second apart ran twice.

## Exported-service threat model

SystemUI runs as its own uid, so it can reach the service only if the service
is exported, and any installed app can then try to reach it too. What an
attacker could want:

- **Burn CPU, memory and battery** by asking for segmentations, each seconds
  of CPU and gigabytes of memory.
- **Crash the module's app** with a malformed message, such as a `Bundle` that
  cannot be unparcelled.
- **Learn something** from the answers, although a mask describes only the
  photo the caller sent.

What stands in the way, in order:

1. **Binding needs `android.permission.STATUS_BAR_SERVICE`**
   (`AndroidManifest.xml`). It is a `signature|recents` platform permission:
   held by SystemUI on every Pixel build, granted otherwise only to
   platform-signed code, the system's recents app and the adb shell, and never
   to a downloaded app. Checked on `CP3A.260905.009` with
   `dumpsys package permission android.permission.STATUS_BAR_SERVICE` and
   `dumpsys package com.android.systemui`. The module is signed with its own
   key, so a signature permission of its own could not include SystemUI.
2. **Package visibility.** Android hides the module's package from apps that do
   not declare a query for it, so most apps cannot even resolve the service.
3. **Each message is checked** (`depth/SubjectMaskCallers`) before any of its
   data is read: the uid Binder reports for it (`Message.sendingUid`, which the
   caller cannot set) must be the uid `com.android.systemui` is installed
   under. Any other message is logged and dropped without an answer, so it
   neither costs a segmentation nor tells the sender anything.
4. **Work is bounded** by the single worker and `NewestOnlyWorker`, so even an
   allowed caller cannot queue more than one run behind the one under way.

What is not defended against: a caller that already runs as SystemUI, or as
root, the adb shell or the platform, already controls the lock screen and the
wallpaper the service would read.

SystemUI's receiver for previewed photos (`DepthPreviewBroadcast`) is exported
too. A sender must hold `SET_WALLPAPER_COMPONENT`, which no downloaded app is
granted, and must be the one Android names as Wallpaper & style
(`BroadcastReceiver.getSentFromPackage`); anything else is logged and dropped.
The worst an allowed sender can do is have one photo segmented, which the
picker does anyway as it previews one.

`SubjectMaskCallersTest` and `NewestOnlyWorkerTest` cover the caller check and
the coalescing.
