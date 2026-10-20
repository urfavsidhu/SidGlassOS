# Validation results

Updated: 20 October 2026 (Asia/Calcutta); initial complete build: 19 October. Project: workspace root `SidGlassOS`. Version: **0.1.0**, package **com.sidglassos**.

## Actual results

| Check | Result |
| --- | --- |
| Development environment | Temurin Java/javac 21.0.12.1; Gradle8.13; SDK platform36 revision2; Build Tools35.0.0; ADB37.0.1 installed and invoked |
| Wrapper integrity | Standard Gradle8.13 wrapper JAR matches the published SHA-256; distribution URL/checksum pinned |
| Kotlin/Android debug compilation | Passed |
| Debug APK assembly | Passed; real APK produced |
| JVM unit tests | **19 passed, 0 failures, 0 errors, 0 skipped** across four suites |
| Android lint | **0 errors, 9 warnings**; no lint baseline and no global error suppression |
| Android UI-test compilation and APK assembly | Passed; five UI tests across two classes |
| Android UI-test execution | **5 passed, 0 failures** on Android 16/API 36; final run took 46.358s; [raw runner output](test-results/api36-instrumentation.txt) and [device/APK result record](test-results/api36-result.json) |
| Release compilation / R8 / vital lint / APK assembly | Passed; release APK is **unsigned** |
| Debug signing verification | `apksigner verify --verbose` passed; one signer, APK Signature Scheme v2 valid |
| Packaged manifest/permissions | Parsed using AAPT2; package/version/target and permissions checked |
| Connected-device inventory | No physical device listed before starting the isolated test VM |
| Emulator installation | Debug app and test APKs installed successfully on project-owned `SidGlassOS_API36`, Google APIs x86_64, Android 16/API 36 |
| Static home wallpaper | Actual app button used; success message observed on the emulator |
| Drawer search with the real Android clock | Tapped All apps, typed an unmatched query, observed No matching apps; [record](test-results/real-clock-search.json) and [screenshot](previews/drawer-empty.png) |
| Visual checks | Four loaded app screens captured and inspected; dark text/system-bar contrast corrected; final Control Center tile contrast also corrected |
| OPPO handset / performance / battery tests | **Not run**; no connected OPPO phone |

Latest full build command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -Command "& .\scripts\build.ps1 -Tasks @('assembleDebug','testDebugUnitTest','lintDebug','assembleDebugAndroidTest','assembleRelease')"
```

Gradle reported `BUILD SUCCESSFUL` in 1m54s: 129 actionable tasks, 26 executed and 103 up-to-date. Full output is saved locally at `.tools/build-validation-20261020.log`. The restricted host denied Kotlin daemon cache access; Kotlin's automatic non-daemon compilation fallback completed successfully. Android lint's optional analytics initialization also warned about its unavailable profile directory.

After the test-fixture adjustments, the instrumentation APK rebuilt successfully in 22s. Final `lintDebug` passed in 7s, with **0 errors and 9 warnings** on the final sources. Its full output is `.tools/lint-final-20261020.log`.

The original 19 October build passed after a clean resource rebuild removed stale adaptive-icon cache entries. Filesystem watching remains disabled for this OneDrive workspace.

## Emulator execution

The isolated VM uses Android Emulator 37.2.12, API 36 Google APIs x86_64 image `BE2A.250530.026.F3`, and the existing Windows Hypervisor Platform. No physical phone, default-Home role or Windows virtualization feature was changed. See [environment](ENVIRONMENT.md) and [repeatable procedure](EMULATOR_TESTING.md).

An initial Home-intent test failed during framework teardown. AndroidX filters lifecycle events against the original launch intent; the production callback correctly changes `Activity.intent`. The test now restores its fixture intent in `finally`, after its unchanged UI assertions. That Home-intent test passed in the subsequent run. This tests callback behavior, not default-Home selection or OPPO gestures. [AndroidX lifecycle matching source](https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java).

The subsequent drawer-search test encountered `SnapshotStateObserver` multithread access under the Compose test clock; earlier runs passed that test. Independent real UI taps/text entry then passed the same search behavior. Both test fixtures now use `StandardTestDispatcher`, retaining default clock advancement and every assertion, following Google's documented Compose 1.10 recommendation. The corrected-fixture run passed **all five tests**, with each test reporting status 0, `OK (5 tests)`, successful runner completion and an empty collected crash buffer. The precise cause of the earlier intermittent error is not established. [Official Compose testing recommendation](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html).

Raw runs remain under `.tools/emulator-tests/`: initial teardown failure `20261020T035601Z`, callback-only teardown failure `20261020T041218Z`, intermittent snapshot error `20261020T041922Z`, and final passing run `20261020T042507Z`. The final runner output and result JSON are also copied to `docs/test-results/`. The owned emulator was shut down after verification.

Early wallpaper capture was obstructed by an emulator System UI error dialog; that interrupted attempt is not counted. The helper was rerun after VM recovery and now waits for loaded screen content. The completed [capture metadata](previews/capture.json) records the actual Android image and APK hash. Screenshots are unedited Android `screencap` PNGs: [dashboard](previews/dashboard.png), [icons](previews/icons.png), [controls](previews/control-center.png), [launcher](previews/launcher.png).

## Test coverage

| Suite | Tests | What it validates |
| --- | ---: | --- |
| `GlassSettingsTest` | 7 | Rendering budgets, invalid/non-finite values, layout uniqueness/order, safe defaults, stale-field merge, concurrent layout preservation and ARGB bounds |
| `LayoutOrderTest` | 4 | Rearrangement boundaries, missing IDs, stable order and drawer search |
| `WallpaperFramePolicyTest` | 4 | Mode FPS caps, static reduced-motion/power-saving behavior and bounded particles |
| `MediaPolicyTest` | 4 | Playing-session selection, actual action flags, independent skip actions and bounded artwork aspect ratio |

JVM XML reports: `app/build/test-results/testDebugUnitTest/`. HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`. Lint report: `app/build/reports/lint-results-debug.html`.

Lint warnings consist of six newer-version advisories for intentionally pinned compatible tooling/libraries, and three optional KTX-style suggestions. These are not treated as tested runtime guarantees. A single narrow tile-lint suppression documents the required API29–33 compatibility overload and has a runtime guard; Android34+ uses PendingIntent.

## Produced artifacts

| File | Bytes | Purpose |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 65,307,933 | Signed development build, installed on the emulator |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,408,712 | Instrumentation package; all five tests executed and passed |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,476,503 | R8-optimized release output; requires the owner's signing before installation |

SHA-256 for the debug APK:

```text
B4636D922BE94EF61862C1D24EFE618A6B48E21ADABFFF10CB06E1E4A26AAE39
```

SHA-256 for the unsigned release APK:

```text
AE41161DD91579E3F822EC17D0AE27812170044C1A11E66EF3171CDAFD590C81
```

The debug package requests SET_WALLPAPER, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, MODIFY_AUDIO_SETTINGS and POST_NOTIFICATIONS, plus AndroidX's app-specific non-exported-receiver signature permission. It has **no INTERNET, overlay, camera-capture, location, storage, QUERY_ALL_PACKAGES or accessibility permission**. The optional media service is system-bound through BIND_NOTIFICATION_LISTENER_SERVICE and requires a separate user grant; this is not an automatically granted uses-permission.

## Remaining gates and scope

Emulator installation, screenshots and UI tests cover only the observations recorded above. Media-session delivery/revocation, native widget hosting/updates, live-wallpaper lifecycle, animation smoothness, battery use and exact ColorOS behavior still need their device acceptance checks. Run [DEVICE_TESTS.md](DEVICE_TESTS.md) on the actual OPPO A5 Pro 5G build. Its Android/API level has not been measured from a phone.

The delivered project implements the launcher, original icon framing/assignment, in-app Control Center and optional tile, local notification test/preview, lock-screen design preview, static/live wallpaper, four native widgets, optional local media-session integration and customization dashboard. Real SystemUI/secure-lock-screen replacement and arbitrary `.theme` import are not claimed. Floating overlays, notification mirroring and external widget hosting inside this launcher are not included; the in-app panel and published native widgets provide the supported alternatives. All seven reference files remain unchanged outside the app's packaged resources.
