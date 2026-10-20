# Isolated Android UI tests

The PowerShell harness uses a project-owned Android 16 (API 36) emulator. It never selects a physical phone or changes Windows virtualization features. Emulator results supplement the OPPO checklist; they do not establish ColorOS compatibility or handset performance.

The VM runs Google's Android image, not ColorOS firmware, and does not reproduce the OPPO phone's processor, RAM, GPU or physical controls. Home-intent handling tests do not verify the user's approval of the default-Home role, its persistence on OPPO, or OPPO gesture/Recents behavior. Keep emulator results separate from the unchecked [OPPO handset acceptance tasks](DEVICE_TESTS.md).

## Prerequisites

- Build both `app-debug.apk` and `app-debug-androidTest.apk`. The default build script does not assemble the instrumentation APK; include its task explicitly: `.\scripts\build.ps1 -Tasks @('assembleDebug', 'assembleDebugAndroidTest')`.
- Install the SDK **Android Emulator** and **Google APIs Intel x86_64 system image, API 36** packages in the workspace SDK, using Android Studio's SDK Manager or the supplied SDK command-line tools after accepting the applicable license.
- An existing usable Windows Hypervisor Platform installation is required. The harness runs `emulator -accel-check` before startup. It does not enable features, install drivers or reboot the PC.

The basic bootstrap script installs compile tools, not emulator images. The project's initial emulator setup uses Android Emulator 37.2.12 and the API 36 Google APIs x86_64 image. Tool/image versions can change independently from the app's build stack. See the official [hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration) and [command-line emulator](https://developer.android.com/studio/run/emulator-commandline) documentation.

## Run

From the workspace root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-emulator.ps1
```

The harness creates `SidGlassOS_API36` below `.tools/avd/`, uses port 5580, starts a hidden headless VM with 2 GB of RAM and two CPU cores, installs only this app and its instrumentation APK, then runs the AndroidJUnit runner. It requires an actual positive `OK (N tests)` result and successful completion; compilation alone cannot pass the harness. Five UI tests are currently authored: dashboard/control navigation, icon screen content, page restoration after recreation, drawer search, and Home-intent handling.

Both Compose fixtures explicitly use `StandardTestDispatcher`, preserving normal clock advancement and synchronization. The Home-intent fixture restores its original launch intent only after its UI assertions so AndroidX can observe activity destruction. See the source comments and [validation notes](VALIDATION.md) for the observed failures and final passing run.

Every ADB operation targets `emulator-5580`. The harness rejects occupied ports and a conflicting AVD. It records raw instrumentation/crash output, APK hashes and a result under `.tools/emulator-tests/<run timestamp>/`. Failed tests retain their output. Normal cleanup stops only the emulator process started by the harness.

## Screenshots and wallpaper smoke check

To inspect the test device after its run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-emulator.ps1 -KeepRunning
python .\scripts\capture-emulator.py --apply-wallpaper
```

The Python helper verifies the project AVD identity, uses the app's **Set home wallpaper** button on that VM, waits for the app's real success message, and captures four unedited Android screenshots under `docs/previews/`. It does not generate or modify image content. Omit `--apply-wallpaper` to capture without applying a wallpaper. Python 3 is needed only for this optional capture helper.

These are procedure descriptions, not a claim that a run has passed. An interrupted helper or an obstructing Android/System UI dialog is not a wallpaper or screenshot pass. Record the interruption and rerun the affected check; a screenshot's presence alone does not establish test success. A completed emulator wallpaper smoke check still leaves OPPO wallpaper destinations and rendering unverified. See [VALIDATION.md](VALIDATION.md) for actual outcomes.

Stop the retained test emulator afterward:

```powershell
& '.\.tools\android-sdk\platform-tools\adb.exe' -s emulator-5580 emu kill
```

Read [VALIDATION.md](VALIDATION.md) for observed results and [DEVICE_TESTS.md](DEVICE_TESTS.md) for unverified handset-specific behavior.
