# Android and OPPO device validation

This is the acceptance checklist for the **physical OPPO handset**. Its unchecked items remain unverified on the reported OPPO A5 Pro 5G/ColorOS build. They are acceptance tasks, not evidence of completed tests. Emulator runs are recorded separately in [VALIDATION.md](VALIDATION.md); they do not complete this handset checklist. See [COMPATIBILITY.md](COMPATIBILITY.md) for API decisions and [EMULATOR_TESTING.md](EMULATOR_TESTING.md) for the isolated emulator procedure.

The Google APIs Android emulator is not an OPPO device and does not run ColorOS. Its UI/API results cannot establish the handset's default-Home selection, OEM gestures/Recents, lock-screen destinations, Theme Store support, physical controls, graphics performance or battery use. Compilation, JVM tests and instrumentation execution are separate forms of evidence.

## OPPO handset record before testing

| Field | Recorded value |
| --- | --- |
| Test date / tester | Not recorded |
| APK version / SHA-256 | Not recorded |
| Device / region / navigation mode | OPPO A5 Pro 5G reported by user; region/navigation mode not verified |
| Handset Android release / SDK_INT | Not measured on the OPPO phone |
| ColorOS / build display / security patch | User reports 16.0.5 / CPH2695_16.0.5.1200; not measured |
| Install method / permission state | Not recorded |
| Screen resolution / density / refresh rate | Not measured |

Read-only commands when `adb` and an authorized device are available:

```powershell
adb devices -l
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.display.id
adb shell getprop ro.build.version.security_patch
```

Also inspect Settings > About device for the ColorOS version; proprietary properties are not a stable API. Do not publish device serials or private notification content in test artifacts.

## First launch and navigation

- [ ] Install the produced debug APK and launch the dashboard without selecting it as Home. Record any crash/ANR.
- [ ] Confirm no overlay, notification-listener or other special access is requested automatically.
- [ ] All sections/actions have accurate working, disabled or preview labels; no inactive decorative control appears successful.
- [ ] Navigate forward/back, rotate, background/resume, kill/relaunch and change font/display size. No lost configuration or clipped critical controls.
- [ ] Enable TalkBack and verify labels, reading order, focus, contrast and usable touch targets.
- [ ] Toggle app reduced-motion/Battery Saver and system animations; confirm visible behavior matches the settings.

## Home role, apps and persistence

- [ ] Cancel default-Home selection. Original launcher remains the default and dashboard remains usable.
- [ ] Select Sid Glass OS through Android's Home role/default-app UI. Home opens its launcher.
- [ ] Switch back through Home settings; confirm OPPO System Launcher works without reinstalling or resetting the phone.
- [ ] Re-select Sid Glass OS; reboot and verify default selection and stored layout survive unlock.
- [ ] Test Home, Back, Recents, quick app switch, screen unlock and app-return animations in gesture and three-button modes. Record OEM delay/flicker instead of assuming parity with stock launcher.
- [ ] Open every available app category; confirm original app/component launches with its normal behavior.
- [ ] Search/scroll drawer with many installed apps, duplicate names, long labels and missing icons; confirm responsive empty state.
- [ ] Install, uninstall, update and disable apps while launcher is backgrounded. No stale item crash on return.
- [ ] Rearrange icons between pages/dock, change grid/icon/spacing/type, restart and confirm persistence without duplicate or inaccessible items.
- [ ] Remove every home/dock item, restart the launcher/process and verify the intentionally empty layout is preserved. First-run seeding must not repeat.
- [ ] Change two different preferences rapidly, then rotate/recreate the dashboard. Both preferences and the selected dashboard page remain intact.
- [ ] Where present, test work/profile/private-space visibility and locked/unavailable apps; never expose a hidden profile's apps or contents.

## Controls and permissions

- [ ] Wi-Fi/Internet, Bluetooth, Airplane and display tiles open official controls. Cancel without changing state; tile must not falsely report success.
- [ ] Change those settings externally then resume; status refreshes from actual APIs or shows unknown when inaccessible.
- [ ] Deny Bluetooth read access if requested, then revoke it in Settings. Shortcuts remain usable and status degrades gracefully.
- [ ] Volume slider/shortcut works with speaker, wired/Bluetooth audio where available, and fixed-volume/blocked cases; DND/ringer policy is not changed unexpectedly.
- [ ] App-brightness slider affects only its documented scope. Leaving/resetting the app restores the expected window behavior.
- [ ] If global brightness is implemented: deny/revoke Modify system settings; no crash/loop or false applied state. Verify manual versus adaptive brightness.
- [ ] Flashlight toggles only when hardware is available; test while Camera is using flash, camera privacy is enabled, and app exits. Callbacks reflect actual on/off/unavailable state.
- [ ] Battery percentage/charging state updates while visible; no unnecessary permission or permanent polling service.
- [ ] The Light glass icon option changes the app's icon surfaces; the app retains its dark palette. Display & theme opens the user-controlled system appearance settings.
- [ ] Missing Settings handlers or denied launches fall back to general Settings with a clear status.

## Optional overlay and Quick Settings tile

- [ ] If overlay is implemented, enable it only from a user action after explanation. Denial leaves in-app Control Center available.
- [ ] Dismiss by close, outside action/back where applicable, and dashboard disable. System navigation, keyboard and critical UI remain usable.
- [ ] Revoke overlay access while visible; panel disappears safely and does not repeatedly prompt/restart.
- [ ] Screen lock/off, app process removal, battery restrictions and reboot do not resurrect an unwanted panel.
- [ ] If a foreground service is necessary, validate its permitted type, visible notification, stop action and Android background-start restrictions.
- [ ] If a TileService is implemented, add/remove its tile through the user's system UI. Tile opens the supported app feature without claiming to replace the shade.

## Notifications and media

- [ ] Previews are labeled and work with notification access off.
- [ ] For the app's own notifications, deny/revoke Android 13+ notification permission; essential app screens remain functional.
- [ ] Connect media only after its privacy explanation. Cancel the dialog/system grant and verify Open music remains usable.
- [ ] Enable Glass music connection and test a compatible player's metadata/controls. On API31+ verify the default notification-content type list is empty; record any OEM differences.
- [ ] Revoke access while a card/widget is visible. Metadata/artwork/actions clear, callbacks are released, and no regrant loop occurs. Notification payloads are never inspected, logged or persisted.
- [ ] If notification mirroring is added in a future version, separately validate privacy, dismissal/update and source integrity. Mirroring is not implemented in this version.
- [ ] Locked-screen, work/private profile and sensitive/OTP redaction remain respected; do not attempt bypasses.
- [ ] Media card/widget shows correct permission-off/no-session state, missing artwork/title fallback and unsupported-action states.
- [ ] With a player exposing a media session, test play/pause/previous/next only when supported, session ending, changing players and access revocation.
- [ ] Confirm actual OPPO notifications and secure lock-screen layout are not presented as modified by a local preview.

## Wallpapers, widgets and glass rendering

- [ ] Apply static home, lock and both-screen wallpapers using their explicit buttons. Confirm the actual destination and failure reporting; these buttons call the wallpaper API directly and do not display a picker.
- [ ] Open native live wallpaper preview and record destinations actually offered. Cancel without applying; no success claim or unexpected change. Test supported home/lock/both choices; keep static lock fallback for unavailable choices.
- [ ] Live engine stops scheduled rendering when hidden/surface destroyed, and resumes safely after unlock/rotation/preview removal.
- [ ] Particle/intensity/parallax/reduced-motion settings persist; unavailable parallax does not break rendering.
- [ ] Battery Saver reduces work; static wallpaper fallback remains functional after rendering errors/low memory.
- [ ] Add, resize, reconfigure and remove each native widget in OPPO launcher and Sid Glass OS if it hosts widgets. IDs/configuration do not leak or corrupt.
- [ ] Native clock remains correct across minute, date, locale, time-zone and 12/24-hour changes without a one-second background polling service.
- [ ] Battery widget refresh/freshness is honest after charging changes, screen off and process death.
- [ ] If hosting third-party widgets, cancel bind/configuration, deny permission and reboot; no broken permanent items or repeated prompts.
- [ ] Record cross-window-blur support where implemented. Disable via Battery Saver/developer option if available; cards remain readable with fallback.
- [ ] Confirm Compose/RenderEffect blur is not described as live backdrop blur unless it actually samples the intended app-owned background.

## Performance, stability and reversal

- [ ] Measure cold/warm startup, drawer scrolling, page changes and dragging using Android Studio profiler/Perfetto or an appropriate benchmark; retain device/build and trace evidence.
- [ ] Record jank/frame-time distribution for Balanced, High Quality and Battery Saver. Do not mark 60 FPS guaranteed.
- [ ] Compare idle/screen-off battery and CPU with features enabled/disabled; no invisible wallpaper render loop, persistent wakelock or idle overlay work.
- [ ] Exercise memory pressure/background process death; settings restore and graphics resources are reclaimed.
- [ ] Test reboot, system dark-mode change, denied/revoked permissions and repeated screen-off/on without crashes or permission loops.
- [ ] Reset customization restores documented local defaults. Wallpaper restoration explains any need to reselect the user's previous image.
- [ ] Return Home to OPPO System Launcher, stop overlay, revoke optional grants, select another wallpaper and remove widgets. Original PIN/biometrics remain intact throughout.
- [ ] Uninstall Sid Glass OS after reversal and verify ordinary Home/notifications/navigation remain functional.

## Result record format

For each test, record `PASS`, `FAIL`, `NOT RUN` or `NOT APPLICABLE`, plus device/build, APK hash, steps, expected/actual result and relevant redacted screenshot/log/trace. Label each record **OPPO handset** or **Android emulator**; an emulator result must not be relabeled as an OPPO pass. A preview-only or unimplemented optional feature is `NOT APPLICABLE` with an explanation, not `PASS`. All failures need a tracked limitation or fix and a targeted rerun.
