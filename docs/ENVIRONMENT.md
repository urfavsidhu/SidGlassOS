# Development environment

Audit date: 19 October 2026 (Asia/Calcutta). Workspace: `C:\Users\sidha\OneDrive\Desktop\SidGlassOS`. Findings below distinguish the initial audit from tools subsequently installed by the bootstrap script.

## Initial audit

| Item | Observed result |
| --- | --- |
| Workspace | Only `references/` was present at the start; no existing Android project or Gradle wrapper. |
| Java on PATH | `C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe`; Oracle Java runtime `1.8.0_503`, HotSpot `25.503-b01`. This is unsuitable for the Android build. |
| Compatible installed JDK | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`; `java -version` reports Temurin `21.0.12.1+1-LTS`; `javac -version` reports `21.0.12.1`. Both commands actually ran. |
| Environment variables | `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` were unset. |
| Gradle, adb, emulator, Kotlin CLI | `Get-Command` did not find these on PATH. |
| Android Studio and SDK | Not found at the standard Program Files or Local AppData locations, or at `C:\Android`, `C:\tools`, or `C:\gradle`. This targeted search does not establish that no copy exists anywhere on the machine. |
| Gradle/Maven caches | `.gradle` and `.m2` were absent in the user profile at the inspected locations. |
| Android user data | `.android` exists, but contains only existing adb key files in the inspected directory. Key contents were not read. Their existence does not prove an attached device is authorized. |
| Device/emulator testing | Not performed during the initial audit because adb and emulator were unavailable. The phone's Android API level, ColorOS build, gesture behavior, and feature support remain unverified on hardware. |

Listing the user-profile root was access-denied. Readable, explicitly named installation directories were inspected instead; no elevated access was requested for the audit.

## Pinned build stack

The project uses AGP **8.13.2**, Gradle **8.13**, Kotlin and Compose compiler plugin **2.3.10**, Compose BOM **2025.12.01**, compile/target SDK **36**, Build Tools **35.0.0**, and minimum SDK **29**. The installed JDK 21 runs Gradle; the project may still target Java 17 bytecode.

AGP 8.13 requires Gradle 8.13 and JDK 17 or later, defaults to Build Tools 35.0.0, and supports API levels through 36.1. [Official AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes).

Kotlin 2.3.10's documented compatibility range includes Gradle 8.13 and AGP 8.13.2. The Compose compiler plugin uses the Kotlin version. [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

Gradle supports running on Java 21 starting with Gradle 8.5. [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html). Compose BOM pins library versions separately from the compiler. [Official BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping).

These are fixed, mutually compatible releases selected for API 36; this document does not claim they are the newest releases.

## Workspace-local setup

Read the [Android SDK terms](https://developer.android.com/studio/terms) before explicitly accepting them. From the project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\bootstrap.ps1 -AcceptAndroidSdkLicense
```

The switch authorizes SDK license acceptance. Without it, the script stops before creating directories or downloading anything. It downloads official Gradle and command-line-tools archives, verifies their pinned SHA-256 checksums before extraction, and installs `platform-tools`, `platforms;android-36`, and `build-tools;35.0.0` using Google's SDK manager. SDK manager performs package verification. Bootstrap does not install Android Studio, an emulator, system images, drivers, or a JDK.

Once the Gradle project files are ready, add `-GenerateWrapper` to generate the standard Gradle wrapper with the distribution checksum and validate its JAR against Gradle's published checksum. This step resolves project plugins from their configured repositories.

Downloaded tools, Gradle caches, and Java-based Android SDK tooling preferences live under ignored `.tools/`. Native Windows adb is an exception: it obtains the Windows profile using `SHGetFolderPathW(CSIDL_PROFILE)` and uses that profile's `.android` directory, rather than `ANDROID_USER_HOME`. The scripts do not redirect or inspect the existing adb keys. `local.properties` records the selected local SDK path and is ignored. Environment variables change only in the current PowerShell process; machine-wide Java and SDK settings are untouched. An existing SDK can be supplied to the build script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -JavaHome 'C:\path\to\jdk-21' -AndroidSdk 'C:\path\to\Android\Sdk'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Tasks assembleDebug
```

The process-scoped execution-policy option is needed on this host, whose default PowerShell policy blocked direct script invocation. It does not change the machine's execution policy. The default build runs `assembleDebug`, `testDebugUnitTest`, and `lintDebug`. Device instrumentation tests require a separately configured emulator or a connected, explicitly authorized phone; they are not part of this command. Expected debug APK path after a successful build: `app/build/outputs/apk/debug/app-debug.apk`.

Inside this restricted Windows execution sandbox, even `adb version` failed with `Cannot mkdir '\.android': Permission denied` because the native Windows profile lookup did not return a usable directory. This failure does not establish whether a phone is connected. Run the device inventory from an authorized normal Windows process before making device-testing claims. No environment-variable workaround or profile modification is supplied. [Official Android environment variables](https://developer.android.com/tools/variables), [AOSP Windows adb directory resolution](https://android.googlesource.com/platform/packages/modules/adb/%2B/refs/heads/main/adb_utils.cpp).

## Download integrity

| Archive | Pinned SHA-256 |
| --- | --- |
| `gradle-8.13-bin.zip` | `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` |
| Gradle 8.13 wrapper JAR | `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f` |
| `commandlinetools-win-15859902_latest.zip` | `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` |

Sources: [Gradle release checksums](https://gradle.org/release-checksums/), [Android command-line tools downloads](https://developer.android.com/studio#command-line-tools-only). A checksum failure stops installation. Network access is needed for initial tool and dependency downloads.

## Emulator environment observed on 20 October 2026

These later observations supplement the 19 October initial audit above.

| Item | Observed result |
| --- | --- |
| Workspace-local emulator | Android Emulator **37.2.12**, build **16428233**, installed in `.tools/android-sdk/`. |
| System image | Google APIs Android **16 / API 36**, **x86_64**; build `sdk_gphone64_x86_64-userdebug 16 BE2A.250530.026.F3 13894323 dev-keys`. This is Google's emulator image, not ColorOS. |
| Hardware acceleration | `emulator -accel-check` reported **WHPX 10.0.26200** usable. No Windows virtualization feature or driver changes were made. |
| Project AVD | `SidGlassOS_API36`, stored below `.tools/avd/`, using the isolated test identity `emulator-5580`. |
| Physical-device inventory | Native adb inventory showed no physical devices before this VM was started. This does not verify the reported OPPO phone or its firmware. |

See [EMULATOR_TESTING.md](EMULATOR_TESTING.md) for setup/run procedures and [VALIDATION.md](VALIDATION.md) for actual build and runtime outcomes. This environment record does not claim a final UI-test pass or OPPO handset compatibility.

## Verification status

The initial audit ran Java and javac version checks. Android compilation, unit tests, lint, APK generation, emulator tests, and phone tests were not performed as part of that audit. Record subsequent real build outcomes in the project validation documentation; do not infer success from the presence of source files or scripts.
