# Depth effect

The subject of a photo wallpaper in front of the large clock. This page covers
how the subject is found, how it is drawn, and why the service that finds it is
safe to export. `HOOK_NOTES.md` has the model's measurements and the SystemUI
signatures.

## From photo to layer

SystemUI draws a still lock screen photo itself. Each time it draws one it has
not drawn before, the photo is taken as drawn, copied at the wallpaper
surface's size, and sent to the module's own app, which finds the subject's
mask with BiRefNet_lite on ONNX Runtime. Nothing leaves the device. A mask
already found is read back from disk, from
`/data/user_de/0/com.android.systemui/files/pixel_lock_screen_evolved_depth_<model>/`.

The subject cut out of the photo becomes a layer beside the large clock face,
placed as `WallpaperController` places the wallpaper (crop, scroll and zoom). It
is erased from the time alone, left out while it would hide more than half of
the time's ink, and drawn as one layer with the face, so whatever fades, dims or
darkens the photo does the same to the subject.

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

`SubjectMaskCallersTest` and `NewestOnlyWorkerTest` cover the caller check and
the coalescing.
