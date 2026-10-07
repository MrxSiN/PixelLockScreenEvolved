# Migration

Nothing persistent changes, so an update needs no migration.

| Item | Change |
|---|---|
| Package `io.github.mrxsin.pixellockscreenevolved`, signing key, `versionName` | unchanged; the release `versionCode` must still exceed the installed one |
| `META-INF/xposed` (entry class, `minApiVersion=102`, `staticScope`, `autoHotReload`, scope list) | unchanged |
| Secure settings (`lock_screen_custom_clock_face` axes, `pixel_lock_screen_evolved_*`) | unchanged keys and formats |
| Safe Mode state file, host snapshots, host descriptor capture | unchanged |
| Cached masks (`files/pixel_lock_screen_evolved_depth_birefnet_lite/*.mask`) | unchanged directory, key and format. The model and its output are unchanged (byte-identical mask), so every stored mask stays valid and is read as before |
| `SubjectMaskContract` messages between SystemUI and the service | unchanged |

Update: as before, Vector posts "Module updated, please force stop and restart apps in its scope"
after an install; restarting SystemUI (and Wallpaper & style) loads the new code. The service contract
is unchanged, so an old SystemUI talking to a new service, or the reverse, behaves as before.

Rollback: install a build with a higher version code from the previous source; no data needs undoing.
