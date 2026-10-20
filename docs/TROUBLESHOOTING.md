# Troubleshooting

| Symptom | Action |
| --- | --- |
| Gradle selects Java 8 | Use JDK 21 in Android Studio, or `scripts/build.ps1 -JavaHome 'C:\path\to\jdk-21'`. The script changes only its own process environment. |
| PowerShell refuses a script | Invoke `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1`. This does not change machine execution policy. |
| SDK packages missing | Read the SDK terms, then run bootstrap with `-AcceptAndroidSdkLicense`; alternatively install platform 36 / Build Tools 35.0.0 in Android Studio. |
| Download blocked | Tool/dependency installation needs approved internet access to official Google, Maven Central, Gradle and plugin repositories. Reuse verified cached downloads on retry. |
| SDK extraction path too long | Bootstrap uses short staging paths in `.tools`. If your workspace is substantially deeper than this one, use a short SDK path through Android Studio and pass it to `build.ps1 -AndroidSdk`. |
| Resource missing after moving a resource folder | Stop the build, then run a clean build with `--no-build-cache` in the Gradle task arguments. Stale cached resource mappings were observed during initial development. Filesystem watching is disabled for this OneDrive workspace. |
| Home chooser does not appear | Open Android Default apps → Home app. ColorOS menus and device policy can differ. The app never silently takes the role. |
| Gesture/recents transition differs from OPPO home | Third-party launchers do not inherit privileged OEM recents integration. Test both gesture and three-button navigation; restore OPPO home if needed. |
| An app disappeared or will not launch | Return to the launcher to refresh its catalog. Remove obsolete shortcuts. Restricted/disabled/work-profile apps are not represented as ordinary launchable activities. |
| Icon frame too crowded | Lower icon size or grid column count. Effective layout clamps settings to fit narrow windows. |
| Wi-Fi/Bluetooth tap opens Settings | This is intentional: a modern unprivileged app cannot silently toggle these settings. The displayed shortcut is not a confirmation that the state changed. |
| Flashlight unavailable | Close camera apps and retry. Missing hardware, OEM policy and camera contention can prevent torch control. The app does not claim success on failure. |
| Brightness reverts on leaving Controls | The slider changes this app window only. Use Device display settings for global brightness. |
| Live wallpaper absent on lock screen | Use the destinations offered by the official picker. If unavailable, apply a still lock wallpaper. A custom lock-screen layout is a design preview only. |
| Music card has no title/controls | Connect media only if you choose to grant Android's notification access, then start playback in a player that exposes a media session. Some players omit metadata/actions. Revoke access through Manage access; the Open player fallback works without it. |
| Widgets are not visible | In Sid Home, tap Widgets or use dashboard Add widgets inside Sid Home. Complete Android consent/setup, then swipe past app pages. Pin buttons depend on the default home supporting pin requests. For a pending setup, cancel it and try again; for an unavailable provider, enable its app or remove the widget. |
| Battery widget reading is old | Tap it to refresh. System-scheduled updates are inexact; clock/date use native TextClock and need no per-minute wakeups. |
| Test notification is missing | Check system app/channel notification settings and permission. Do Not Disturb and channel settings can suppress visibility even when posting succeeds. |
| Forest motion is still | Reduced motion, Battery Saver, system power saver, zero particles, or zero intensity intentionally stop the render timer. |
| Settings reset did not change wallpaper | Applied wallpapers belong to Android. Select another wallpaper in Android Settings. |

No emulator or hardware result should be inferred from a successful compilation. Use `DEVICE_TESTS.md` for checks on the actual OPPO build.
