# Validation results

Updated: 20 October 2026 (Asia/Calcutta). Version **0.2.0**, package **com.sidglassos**, version code **2**. The project is the workspace root. No physical OPPO device has been tested.

## Build and unit checks

Debug compilation, debug assembly, JVM tests, Android lint and instrumentation APK assembly completed successfully. The final full Gradle check returned exit code 0 and BUILD SUCCESSFUL in 39s: 81 actionable tasks, 3 executed and 78 up-to-date. The subsequent test synchronization change rebuilt successfully in 21s.

~~~powershell
& .\scripts\build.ps1 -Tasks @('assembleDebug','testDebugUnitTest','lintDebug','assembleDebugAndroidTest','-Pkotlin.compiler.execution.strategy=in-process','--no-daemon')
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-emulator.ps1
~~~

Toolchain: Temurin JDK 21.0.12.1, Gradle 8.13, AGP 8.13.2, Kotlin 2.3.10, SDK 36 and Build Tools 35.0.0. See [environment](ENVIRONMENT.md). Single-use Gradle and in-process Kotlin avoid the restricted host's cached daemon/profile access issues without changing machine settings. Gradle reuses test results when inputs are unchanged.

**27 JVM tests passed**, zero failures, errors or skipped tests. XML reports: app/build/test-results/testDebugUnitTest/.

| Suite | Tests | Behavior |
| --- | ---: | --- |
| GlassSettingsTest | 7 | Settings normalization, rendering limits, defaults and concurrent layout preservation |
| LayoutOrderTest | 4 | App ordering, boundaries and drawer search |
| WallpaperFramePolicyTest | 4 | Frame caps, reduced motion/power saving and bounded particles |
| MediaPolicyTest | 4 | Session/action selection and bounded artwork |
| GlassEffectPolicyTest | 4 | SDK/hardware/power gating, radius caps and crop alignment |
| WidgetLayoutPolicyTest | 4 | Valid distinct IDs, capacity, provider size limits and ordering |

Android lint: **0 errors, 14 warnings** (seven tooling/dependency update advisories and seven optional KTX-style suggestions). Reports: app/build/reports/. There is no global suppression or lint baseline. The BIND_APPWIDGET exception is limited to one documented host-contract declaration: an ordinary APK does not receive this signature permission and must use Android consent. The existing tile API compatibility exception remains narrowly guarded.

## Emulator coverage

**10 UI tests passed, zero failures**, in 74.708s, with an empty collected crash buffer. [Raw runner output](test-results/api36-instrumentation.txt), [device/APK record](test-results/api36-result.json), [JVM result](test-results/unit-result.json). Final run: 20261020T092216Z, completed 20 October 2026 at 09:23:36 UTC. Each test reported status 0 and the runner reported OK (10 tests).

The project-owned **SidGlassOS_API36**, serial **emulator-5580**, runs Android 16/API 36 Google APIs x86_64. The harness verifies AVD identity, installed packages, test discovery count and runner completion. It does not change a physical phone or default-Home selection. Tests use Android's real widget approval without adopting shell permissions or checking persistent Always allow access.

Five existing dashboard/launcher UI tests are retained. Five additional widget tests cover picker cancellation without allocating IDs, denied binding cleanup, native clock content/resizing/activity recreation/removal, required provider configuration before committing, and canceled configuration cleanup. Tests preserve preexisting widgets and remove their own additions. Activity recreation is tested; process death, reboot and every provider are not.

Earlier failed runs remain in .tools/emulator-tests/. The consent locator was corrected to use Android's real Create action. The independent test provider's missing Kotlin runtime was fixed using platform-only Java. Its title bar covered Save; the fixture now uses a title-free theme and system insets, preserving button labels explicitly. The configuration test advances Compose's test clock until native content is rendered before its UI Automator assertion. Required setup and assertions were retained. These fixture components are absent from the production APK.

The screenshot helper previously read a stale hierarchy after a failed dump. It now requires a successful fresh UUID-named dump with bounded retries. Captures run only when instrumentation is idle because Android permits one UI automation connection. Screenshots are unedited Android screencap PNGs; [metadata](previews/capture.json) identifies the APK and wallpaper observation.

Final loaded [dashboard](previews/dashboard.png), [icons](previews/icons.png), [controls](previews/control-center.png) and [launcher](previews/launcher.png) were captured and visually inspected. The actual app's static home-wallpaper button returned success on the VM. The [widget picker](previews/widget-picker.png) and [hosted native clock](previews/hosted-clock.png) were also captured after real Create approval; its temporary instance was removed through the app. [Widget capture record](previews/widget-capture.json). The picker includes the test-only provider installed for instrumentation, which is absent from ordinary app installs.

Temporary capture widgets were removed, pending setup was empty, the final crash buffer was empty, and the owned emulator was shut down after verification.

## Signed debug artifact

APK: **app/build/outputs/apk/debug/app-debug.apk**, **66,092,401 bytes**. Signature verification passed using apksigner verify --verbose: APK Signature Scheme v2, one signer. AAPT2 reports minimum API 29, target API 36, version 0.2.0/code 2.

SHA-256:

~~~text
260CE1B8D5E9EEF406BD92B71B0DAA8D1CB4B82CE75642EFD44C98D405CFB7F7
~~~

This revision's release build/signing was not requested or validated; an older unsigned release output is not the current deliverable. The debug package has no INTERNET, overlay, accessibility, camera-capture, location, storage or QUERY_ALL_PACKAGES permission. Optional media-session access remains a separate user grant.

## Unsupported and untested behavior

[Glass and hosting](GLASS_AND_HOSTING.md) documents the implementation. Blur samples the app-owned scene on supported API31+ hardware. App/system Battery Saver and unavailable acceleration use tinted fills. Modal blur follows Android's capability signal. The Home dock does not sample arbitrary system wallpaper. Radius caps are implementation budgets, not measured handset performance guarantees.

Widgets use dedicated Home pages, up to 12. Mixed icon/widget grids, drag resize handles, external pin-request intake and work/private-profile widgets remain unsupported. Protected SystemUI/notification-shade replacement, secure lock-screen replacement, other signed apps' resource modifications and arbitrary OPPO .theme import remain unsupported.

**OPPO ColorOS 16 remains untested**, including actual firmware/API level, GPU/window blur, Home-role persistence, gesture/Recents behavior, media-session delivery and access revocation, live-wallpaper lifecycle, provider compatibility, reboot recovery, smoothness, thermal load and battery use. Follow the [physical-device checklist](DEVICE_TESTS.md). No physical-device compatibility is claimed.
